package paytech.practice.pay.application.payment

import paytech.practice.pay.application.port.outbound.BlockchainClient
import paytech.practice.pay.application.port.outbound.BlockchainTransactionRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.blockchain.BlockchainTransaction
import paytech.practice.pay.domain.blockchain.BlockchainTransactionStatus
import paytech.practice.pay.domain.outbox.OutboxEvent
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentFailureReason
import paytech.practice.pay.domain.shared.EventId
import java.time.Clock
import java.time.Duration

/**
 * "BlockchainTransaction 감지·Confirm" Use Case다. `docs/architecture/mvp-scope.md`의
 * 전체 흐름 중 `USDC 전송 → BlockchainTransaction 감지 및 Confirm → Payment
 * SUCCEEDED → 결제 완료 페이지와 Webhook` 구간과, `docs/architecture/persistence-jooq.md`가
 * 정의한 "결제 완료" 트랜잭션 경계(`BlockchainTransaction + Payment SUCCEEDED +
 * OutboxEvent`)를 그대로 구현한다.
 *
 * 이미 `SUBMITTED`/`DETECTED`/`CONFIRMING` 중 하나인 `BlockchainTransaction` 하나를
 * 대상으로, [BlockchainClient]에 그 시점의 온체인 상태를 다시 묻고 필요한 상태
 * 전이를 진행하는 **폴링 한 번**이다 — `docs/database/database-design.md`의
 * "Confirm Worker" 인덱스가 암시하듯, 이 Use Case 자체는 스스로 반복하지 않고
 * 향후 Worker(`apps:batch`)가 대상 목록을 뽑아 하나씩 호출하는 것을 전제로
 * 설계했다(그 Worker는 이 Use Case의 범위 밖이다). `BlockchainTransaction`을
 * 최초로 만드는 것(고객이 제출한 Transaction Hash를 `SUBMITTED`로 기록하는 것)도
 * 범위 밖이다 — 별도 Use Case가 필요하다.
 *
 * 상태 전이 흐름:
 * 1. 온체인에서 못 찾았으면([BlockchainClient.findTransaction]이 `null`) [handleMissingOnChain]이
 *    처리한다 — `SUBMITTED`면 아직 미채굴이라 아무것도 바꾸지 않지만, 이미 블록에서 본
 *    거래(`DETECTED`/`CONFIRMING`)가 사라진 것이면 체인 재구성(reorg)이라 유예 후
 *    `REORGED`로 끝낸다.
 * 2. `SUBMITTED`였다면 `detect()`로 넘어간다 — `Payment`도 같은 순간
 *    `startConfirmation()`으로 함께 넘어간다(`Payment.startConfirmation`의 KDoc:
 *    "온체인 거래가 감지되어 Confirm 대기 상태로 전이한다" — 검증 통과 여부와
 *    무관하게 "감지" 자체가 이 전이의 조건이다).
 * 3. [PaymentTransactionValidator]로 검증한다. 실패하면 `BlockchainTransaction.fail()` +
 *    `Payment.fail()`로 종료하고 저장한다(Confirm 부족은 검증 실패가 아니라 다음
 *    폴링을 기다리는 정상 대기 상태라 여기 포함되지 않는다 — [PaymentTransactionValidator]의
 *    KDoc 참고).
 * 4. `DETECTED`였다면 `startConfirming()`으로 넘어가고, `recordConfirmation()`으로
 *    누적 Confirm 수를 갱신한다.
 * 5. Confirm 수가 [BlockchainTransaction.requiredConfirmationCount] 이상이면
 *    `confirm()` + `Payment.succeed()`로 종료하고, "결제 완료" Webhook 트리거를 위한
 *    `OutboxEvent`를 함께 남긴다. 아직 부족하면 `CONFIRMING`으로 남는다.
 *
 * 허용 USDC Contract 주소는 [PaymentNetworkConfig]의 네트워크별 MVP 상수를 그대로
 * 쓴다 — [paytech.practice.pay.application.payment.SubmitPaymentTransactionUseCase]도
 * 같은 상수를 쓴다([PaymentNetworkConfig]의 KDoc 참고).
 */
class ConfirmBlockchainTransactionUseCase(
	private val blockchainTransactionRepository: BlockchainTransactionRepository,
	private val paymentRepository: PaymentRepository,
	private val outboxEventRepository: OutboxEventRepository,
	private val blockchainClient: BlockchainClient,
	private val idGenerator: IdGenerator,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	fun execute(command: ConfirmBlockchainTransactionCommand): ConfirmBlockchainTransactionResult {
		val blockchainTransaction =
			blockchainTransactionRepository.findById(command.blockchainTransactionId)
				?: throw BlockchainTransactionNotFoundException(command.blockchainTransactionId)
		check(
			blockchainTransaction.status == BlockchainTransactionStatus.SUBMITTED ||
				blockchainTransaction.status == BlockchainTransactionStatus.DETECTED ||
				blockchainTransaction.status == BlockchainTransactionStatus.CONFIRMING,
		) {
			"BlockchainTransaction(${blockchainTransaction.id.value})이 이미 종료 상태입니다: ${blockchainTransaction.status}"
		}
		val payment =
			paymentRepository.findById(blockchainTransaction.paymentId)
				?: error(
					"BlockchainTransaction(${blockchainTransaction.id.value})의 " +
						"Payment(${blockchainTransaction.paymentId.value})를 찾을 수 없습니다.",
				)

		val onChainTransaction =
			blockchainClient.findTransaction(blockchainTransaction.network, blockchainTransaction.transactionHash)
				?: return handleMissingOnChain(blockchainTransaction, payment)

		val now = clock.instant()

		if (blockchainTransaction.status == BlockchainTransactionStatus.SUBMITTED) {
			blockchainTransaction.detect(onChainTransaction.blockNumber, now)
			payment.startConfirmation(now)
		}

		val expectedTokenContractAddress = PaymentNetworkConfig.expectedUsdcContractAddress(blockchainTransaction.network)
		val validation =
			PaymentTransactionValidator.validate(payment, blockchainTransaction, onChainTransaction, expectedTokenContractAddress)
		if (validation is PaymentTransactionValidationResult.Invalid) {
			blockchainTransaction.fail(validation.reason.name, null, now)
			payment.fail(validation.reason, now)
			return transactionManager.runInTransaction {
				blockchainTransactionRepository.save(blockchainTransaction)
				paymentRepository.save(payment)
				resultOf(blockchainTransaction, payment)
			}
		}

		if (blockchainTransaction.status == BlockchainTransactionStatus.DETECTED) {
			blockchainTransaction.startConfirming(now)
		}
		blockchainTransaction.recordConfirmation(onChainTransaction.confirmationCount, now)

		val outboxEvent =
			if (onChainTransaction.confirmationCount >= blockchainTransaction.requiredConfirmationCount) {
				blockchainTransaction.confirm(now)
				payment.succeed(now)
				OutboxEvent.create(
					eventId = EventId("evt_" + idGenerator.newId()),
					aggregateType = "Payment",
					aggregateId = payment.id.value,
					eventType = PAYMENT_SUCCEEDED_EVENT_TYPE,
					payload = paymentSucceededPayload(payment, blockchainTransaction),
					occurredAt = now,
					createdAt = now,
				)
			} else {
				null
			}

		return transactionManager.runInTransaction {
			blockchainTransactionRepository.save(blockchainTransaction)
			paymentRepository.save(payment)
			outboxEvent?.let { outboxEventRepository.save(it) }
			resultOf(blockchainTransaction, payment)
		}
	}

	/**
	 * 온체인에서 거래를 찾지 못했을 때의 처리다. **같은 `null`이 상태에 따라 전혀 다른
	 * 뜻이다.**
	 *
	 * - `SUBMITTED`: 아직 채굴되지 않았다는 정상 대기다 — 아무것도 바꾸지 않는다.
	 * - `DETECTED`/`CONFIRMING`: 우리가 **블록에서 이미 본** 거래가 사라졌다는 뜻이라
	 *   체인 재구성(reorg)이나 거래 교체(replace)다. [REORG_GRACE]가 지나도록 다시
	 *   나타나지 않으면 `REORGED`로 끝내고 `Payment`도 실패시킨다.
	 *
	 * 유예를 두는 이유는 **한 번의 `null`을 근거로 결제를 죽이지 않기 위해서다** — RPC
	 * 노드가 잠시 뒤처져 있어도 `null`이 나올 수 있고, reorg된 거래가 곧바로 다음 블록에
	 * 다시 들어가는 것이 오히려 흔하다. 마지막으로 온체인에서 확인한 시각은
	 * `updatedAt`이다(거래를 볼 때마다 `detect`/`recordConfirmation`이 갱신하고, 못
	 * 찾은 폴링은 아무것도 저장하지 않아 그 값이 그대로 남는다).
	 *
	 * 유예가 지난 뒤 거래가 다시 채굴되는 경우는 자동으로 처리하지 않는다 — 수령 사실은
	 * `blockchain_transaction`에 남아 있으므로 운영 절차로 판단한다(ADR-007과 같은 규율).
	 */
	private fun handleMissingOnChain(
		blockchainTransaction: BlockchainTransaction,
		payment: Payment,
	): ConfirmBlockchainTransactionResult {
		if (blockchainTransaction.status == BlockchainTransactionStatus.SUBMITTED) {
			return resultOf(blockchainTransaction, payment)
		}

		val now = clock.instant()
		if (now < blockchainTransaction.updatedAt.plus(REORG_GRACE)) {
			return resultOf(blockchainTransaction, payment)
		}

		blockchainTransaction.markReorged(now)
		payment.fail(PaymentFailureReason.TRANSACTION_REORGED, now)
		return transactionManager.runInTransaction {
			blockchainTransactionRepository.save(blockchainTransaction)
			paymentRepository.save(payment)
			resultOf(blockchainTransaction, payment)
		}
	}

	private fun resultOf(
		blockchainTransaction: BlockchainTransaction,
		payment: Payment,
	): ConfirmBlockchainTransactionResult =
		ConfirmBlockchainTransactionResult(
			blockchainTransactionId = blockchainTransaction.id,
			blockchainTransactionStatus = blockchainTransaction.status,
			paymentId = payment.id,
			paymentStatus = payment.status,
		)

	private fun paymentSucceededPayload(
		payment: Payment,
		blockchainTransaction: BlockchainTransaction,
	): String =
		"""{"paymentId":"${payment.id.value}","merchantOrderId":"${payment.merchantOrderId.value}",""" +
			""""transactionHash":"${blockchainTransaction.transactionHash.value}","status":"${payment.status}"}"""

	companion object {
		private const val PAYMENT_SUCCEEDED_EVENT_TYPE = "payment.succeeded"

		/**
		 * 온체인에서 사라진 거래를 `REORGED`로 끝내기까지 기다리는 시간.
		 *
		 * `docs/`에 값이 없어 고정한 MVP 상수다. Base는 블록 주기가 ~2초인 L2라 실제
		 * reorg 깊이는 몇 블록 수준이고, 필요 Confirm 수 12를 채우는 데도 ~24초면
		 * 충분하다 — 10분은 그보다 두 자릿수 크게 잡은 값이다. **짧게 잡을 이유보다
		 * 길게 잡을 이유가 크다**: 너무 짧으면 잠시 뒤처진 RPC 노드의 응답 하나로
		 * 정상 결제를 실패시키고, 그 실패는 되돌릴 수 없다(`Payment`의 `FAILED`는
		 * 종료 상태다). 반대로 길어서 생기는 손해는 실패 판정이 늦어지는 것뿐이다.
		 */
		private val REORG_GRACE: Duration = Duration.ofMinutes(10)
	}
}
