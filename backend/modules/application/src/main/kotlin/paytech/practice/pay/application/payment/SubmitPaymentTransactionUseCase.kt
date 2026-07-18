package paytech.practice.pay.application.payment

import paytech.practice.pay.application.checkout.CheckoutSessionNotFoundException
import paytech.practice.pay.application.port.outbound.BlockchainTransactionRepository
import paytech.practice.pay.application.port.outbound.CheckoutSessionRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.blockchain.BlockchainTransaction
import paytech.practice.pay.domain.blockchain.BlockchainTransactionId
import paytech.practice.pay.domain.blockchain.TransactionType
import paytech.practice.pay.domain.checkout.CheckoutSession
import paytech.practice.pay.domain.payment.Payment
import java.time.Clock

/**
 * "BlockchainTransaction 생성" Use Case다 — 고객 지갑이 USDC 전송을 브로드캐스트한
 * 뒤, 체크아웃 프런트엔드가 그 Transaction Hash를 PG에 제출하는 시점을 구현한다
 * (`docs/architecture/mvp-scope.md`의 전체 흐름 중 `USDC 전송 → BlockchainTransaction
 * 감지 및 Confirm` 바로 앞 구간). [paytech.practice.pay.application.payment.ConfirmBlockchainTransactionUseCase]가
 * "이미 있는 BlockchainTransaction을 다시 확인하는 폴링"이라면, 이 Use Case는 그
 * BlockchainTransaction을 최초로 만드는 자리다 — `ConfirmBlockchainTransactionUseCase`의
 * KDoc이 범위 밖으로 남겨뒀던 바로 그 지점이다.
 *
 * 트랜잭션 경계는 `BlockchainTransaction(SUBMITTED) + CheckoutSession
 * (PAYMENT_SUBMITTED) + Payment(PROCESSING)`다. `docs/architecture/persistence-jooq.md`가
 * 명시한 세 경계(결제 생성/결제 완료/환전 완료) 중 어디에도 해당하지 않는
 * 네 번째 경계이며, 이 Use Case가 처음 정의한다 — `CreatePaymentUseCase`의
 * "결제 생성" 경계, `ConfirmBlockchainTransactionUseCase`의 "결제 완료" 경계와
 * 같은 성격으로 이 세 Aggregate가 "고객이 결제를 제출했다"는 하나의 사실을
 * 함께 반영해야 해서 원자적으로 묶는다. 이 경계에는 `OutboxEvent`를 포함하지
 * 않는다 — 문서가 Outbox를 명시한 경계는 "결제 생성"과 "결제 완료" 둘뿐이라,
 * 여기서 Webhook을 새로 만들어내지 않는다(알려진 gap).
 *
 * **고객 지갑 연결(`CheckoutSession.connectWallet`, `OPEN → WALLET_CONNECTED`)은
 * 이 Use Case의 범위 밖이다** — 별도 Use Case가 먼저 실행돼 있어야 한다. 이 Use
 * Case는 [CheckoutSession.connectedWallet]을 그대로 `BlockchainTransaction.fromAddress`와
 * `Payment.submit`의 지갑 값으로 재사용한다.
 *
 * **중복 제출은 멱등하게 처리한다.** 같은 `(network, transactionHash)`로 이미
 * `BlockchainTransaction`이 있으면(`uk_blockchain_network_hash`), 그게 **같은
 * Payment**의 것이면 새로 만들지 않고 기존 결과를 그대로 돌려준다(재전송/중복 클릭
 * 대응). **다른 Payment**의 것이면 [DuplicateTransactionHashException]을 던진다 —
 * 같은 Transaction Hash를 여러 결제에 재사용하려는 시도이기 때문이다.
 *
 * [PaymentNetworkConfig.REQUIRED_CONFIRMATION_COUNT]/Chain ID/허용 Contract 주소는
 * `docs/`에 값이 없어 고정한 MVP 상수다 — [PaymentNetworkConfig]의 KDoc 참고.
 */
class SubmitPaymentTransactionUseCase(
	private val checkoutSessionRepository: CheckoutSessionRepository,
	private val paymentRepository: PaymentRepository,
	private val blockchainTransactionRepository: BlockchainTransactionRepository,
	private val idGenerator: IdGenerator,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	fun execute(command: SubmitPaymentTransactionCommand): SubmitPaymentTransactionResult {
		val checkoutSession =
			checkoutSessionRepository.findById(command.checkoutSessionId)
				?: throw CheckoutSessionNotFoundException(command.checkoutSessionId)
		val payment =
			paymentRepository.findById(checkoutSession.paymentId)
				?: error(
					"CheckoutSession(${checkoutSession.id.value})의 " +
						"Payment(${checkoutSession.paymentId.value})를 찾을 수 없습니다.",
				)

		findExistingResult(payment, checkoutSession, command)?.let { return it }

		val customerWallet =
			checkNotNull(checkoutSession.connectedWallet) {
				"CheckoutSession(${checkoutSession.id.value})에 연결된 지갑이 없습니다 — " +
					"지갑 연결이 먼저 끝나 있어야 합니다."
			}

		val now = clock.instant()

		val blockchainTransaction =
			BlockchainTransaction.create(
				id = BlockchainTransactionId("btx_" + idGenerator.newId()),
				paymentId = payment.id,
				transactionType = TransactionType.PAYMENT,
				network = payment.network,
				chainId = PaymentNetworkConfig.expectedChainId(payment.network),
				transactionHash = command.transactionHash,
				fromAddress = customerWallet,
				toAddress = payment.receivingWallet,
				tokenContractAddress = PaymentNetworkConfig.expectedUsdcContractAddress(payment.network),
				tokenAsset = payment.paymentAsset,
				amountMinor = payment.paymentAmount,
				requiredConfirmationCount = PaymentNetworkConfig.REQUIRED_CONFIRMATION_COUNT,
				submittedAt = now,
			)

		checkoutSession.submitPayment(now)
		payment.submit(customerWallet, now)

		return transactionManager.runInTransaction {
			blockchainTransactionRepository.save(blockchainTransaction)
			checkoutSessionRepository.save(checkoutSession)
			paymentRepository.save(payment)
			resultOf(blockchainTransaction, checkoutSession, payment)
		}
	}

	private fun findExistingResult(
		payment: Payment,
		checkoutSession: CheckoutSession,
		command: SubmitPaymentTransactionCommand,
	): SubmitPaymentTransactionResult? {
		val existing =
			blockchainTransactionRepository.findByNetworkAndTransactionHash(payment.network, command.transactionHash)
				?: return null
		if (existing.paymentId != payment.id) {
			throw DuplicateTransactionHashException(command.transactionHash)
		}
		return resultOf(existing, checkoutSession, payment)
	}

	private fun resultOf(
		blockchainTransaction: BlockchainTransaction,
		checkoutSession: CheckoutSession,
		payment: Payment,
	): SubmitPaymentTransactionResult =
		SubmitPaymentTransactionResult(
			blockchainTransactionId = blockchainTransaction.id,
			checkoutSessionId = checkoutSession.id,
			checkoutSessionStatus = checkoutSession.status,
			paymentId = payment.id,
			paymentStatus = payment.status,
		)
}
