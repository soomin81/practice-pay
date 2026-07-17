package paytech.practice.pay.application.payment

import paytech.practice.pay.application.port.outbound.BlockchainClient
import paytech.practice.pay.application.port.outbound.BlockchainTransactionRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.blockchain.BlockchainTransaction
import paytech.practice.pay.domain.blockchain.BlockchainTransactionStatus
import paytech.practice.pay.domain.blockchain.ContractAddress
import paytech.practice.pay.domain.outbox.OutboxEvent
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.EventId
import java.time.Clock

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
 * 1. 아직 온체인에서 못 찾았으면([BlockchainClient.findTransaction]이 `null`) 아무
 *    것도 바꾸지 않고 현재 상태를 그대로 돌려준다.
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
 * [EXPECTED_TOKEN_CONTRACT_ADDRESSES]는 `docs/`에 값이 정해져 있지 않아 이
 * Use Case가 상수로 고정했다 — `CreatePaymentUseCase`의 `TOKEN_DECIMALS`와 같은
 * 성격의 MVP 단순화다. Base Sepolia 값은 Circle 공식 문서(developers.circle.com/stablecoins/usdc-contract-addresses)의
 * Base Sepolia USDC Contract 주소를 그대로 썼다.
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
				?: return resultOf(blockchainTransaction, payment)

		val now = clock.instant()

		if (blockchainTransaction.status == BlockchainTransactionStatus.SUBMITTED) {
			blockchainTransaction.detect(onChainTransaction.blockNumber, now)
			payment.startConfirmation(now)
		}

		val expectedTokenContractAddress =
			EXPECTED_TOKEN_CONTRACT_ADDRESSES[blockchainTransaction.network]
				?: error("지원하지 않는 네트워크입니다: ${blockchainTransaction.network}")
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
		/** MVP가 지원하는 네트워크별 허용 USDC Contract 주소. */
		private val EXPECTED_TOKEN_CONTRACT_ADDRESSES: Map<BlockchainNetwork, ContractAddress> =
			mapOf(
				BlockchainNetwork.BASE_SEPOLIA to ContractAddress("0x036CbD53842c5426634e7929541eC2318f3dCF7e"),
			)

		private const val PAYMENT_SUCCEEDED_EVENT_TYPE = "payment.succeeded"
	}
}
