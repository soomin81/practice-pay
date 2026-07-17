package paytech.practice.pay.application.payment

import paytech.practice.pay.application.port.outbound.CheckoutSessionRepository
import paytech.practice.pay.application.port.outbound.ExchangeRateProvider
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.application.port.outbound.PaymentQuoteRepository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.checkout.CheckoutSession
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.outbox.OutboxEvent
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.quote.PaymentQuote
import paytech.practice.pay.domain.quote.PaymentQuoteId
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.EventId
import paytech.practice.pay.domain.shared.ExchangeRate
import paytech.practice.pay.domain.shared.TokenAmount
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Duration

/**
 * "결제 생성" Use Case다. `docs/architecture/mvp-scope.md`의 전체 흐름 중
 * `Payment 생성 → PaymentQuote 확정 → CheckoutSession 생성` 구간과,
 * `docs/architecture/persistence-jooq.md`가 정의한 "결제 생성" 트랜잭션 경계
 * (`Payment + PaymentQuote + CheckoutSession + OutboxEvent`)를 그대로 구현한다.
 *
 * `(merchantId, merchantOrderId)`로 멱등하다 — 이미 생성된 결제가 있으면 새로
 * 만들지 않고 기존 값을 그대로 돌려준다(`backend/CLAUDE.md`의 Idempotency keys).
 * 다만 이 조회 후 판단은 DB Unique 제약만큼 원자적이지 않다 — 동시 요청 사이의
 * 경합을 완전히 막는 최종 방어선은 `uk_payment_merchant_order`(스키마의 Unique
 * 제약)이고, 여기서는 흔한 경우(중복 재요청)를 빠르게 처리하는 선에서 그친다.
 *
 * [SPREAD_RATE]/[TOKEN_DECIMALS]/[PAYMENT_VALIDITY]는 `docs/`에 값이 정해져
 * 있지 않아 이 Use Case가 상수로 고정했다 — 추후 가맹점별/플랫폼 설정으로
 * 분리할 수 있는 지점이다.
 */
class CreatePaymentUseCase(
	private val merchantRepository: MerchantRepository,
	private val paymentRepository: PaymentRepository,
	private val paymentQuoteRepository: PaymentQuoteRepository,
	private val checkoutSessionRepository: CheckoutSessionRepository,
	private val outboxEventRepository: OutboxEventRepository,
	private val exchangeRateProvider: ExchangeRateProvider,
	private val idGenerator: IdGenerator,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	fun execute(command: CreatePaymentCommand): CreatePaymentResult {
		val merchant =
			merchantRepository.findById(command.merchantId)
				?: throw MerchantNotFoundException(command.merchantId)
		if (!merchant.canAcceptPayments()) {
			throw MerchantCannotAcceptPaymentsException(command.merchantId)
		}

		findExistingResult(command)?.let { return it }

		val now = clock.instant()
		val expiresAt = now.plus(PAYMENT_VALIDITY)

		val marketQuote = exchangeRateProvider.currentRate()
		val appliedRate = ExchangeRate(marketQuote.rate.value.multiply(BigDecimal.ONE - SPREAD_RATE))
		val paymentAmount =
			TokenAmount(
				BigDecimal(command.orderAmount.amount)
					.movePointRight(TOKEN_DECIMALS)
					.divide(appliedRate.value, 0, RoundingMode.CEILING)
					.toLong(),
			)

		val payment =
			Payment.create(
				id = PaymentId("pay_" + idGenerator.newId()),
				merchantId = command.merchantId,
				merchantOrderId = command.merchantOrderId,
				orderName = command.orderName,
				orderAmount = command.orderAmount,
				paymentAsset = Asset.USDC,
				paymentAmount = paymentAmount,
				tokenDecimals = TOKEN_DECIMALS,
				network = command.network,
				receivingWallet = command.receivingWallet,
				expiresAt = expiresAt,
				createdAt = now,
			)

		val quote =
			PaymentQuote(
				id = PaymentQuoteId("pq_" + idGenerator.newId()),
				paymentId = payment.id,
				marketProviderCode = marketQuote.providerCode,
				baseAsset = Asset.USDC,
				marketRate = marketQuote.rate,
				appliedRate = appliedRate,
				spreadRate = SPREAD_RATE,
				orderAmount = command.orderAmount,
				paymentAmount = paymentAmount,
				quotedAt = marketQuote.quotedAt,
				expiresAt = expiresAt,
				createdAt = now,
			)

		val checkoutSession =
			CheckoutSession.create(
				id = CheckoutSessionId("cs_" + idGenerator.newId()),
				paymentId = payment.id,
				successUrl = command.successUrl,
				cancelUrl = command.cancelUrl,
				expiresAt = expiresAt,
				createdAt = now,
			)

		payment.ready(now)

		val outboxEvent =
			OutboxEvent.create(
				eventId = EventId("evt_" + idGenerator.newId()),
				aggregateType = "Payment",
				aggregateId = payment.id.value,
				eventType = PAYMENT_CREATED_EVENT_TYPE,
				payload = paymentCreatedPayload(payment, checkoutSession),
				occurredAt = now,
				createdAt = now,
			)

		return transactionManager.runInTransaction {
			paymentRepository.save(payment)
			paymentQuoteRepository.save(quote)
			checkoutSessionRepository.save(checkoutSession)
			outboxEventRepository.save(outboxEvent)
			CreatePaymentResult(paymentId = payment.id, checkoutSessionId = checkoutSession.id)
		}
	}

	private fun findExistingResult(command: CreatePaymentCommand): CreatePaymentResult? {
		val existingPayment =
			paymentRepository.findByMerchantOrderId(command.merchantId, command.merchantOrderId)
				?: return null
		val existingSession =
			checkoutSessionRepository.findByPaymentId(existingPayment.id)
				?: error("Payment(${existingPayment.id.value})에 딸린 CheckoutSession이 없습니다 — 결제 생성 트랜잭션이 원자적이지 않았습니다.")
		return CreatePaymentResult(paymentId = existingPayment.id, checkoutSessionId = existingSession.id)
	}

	private fun paymentCreatedPayload(
		payment: Payment,
		checkoutSession: CheckoutSession,
	): String =
		"""{"paymentId":"${payment.id.value}","merchantOrderId":"${payment.merchantOrderId.value}",""" +
			""""checkoutSessionId":"${checkoutSession.id.value}","status":"${payment.status}"}"""

	companion object {
		/** MVP 스프레드율(0.5%). PG 마진이며 시장 환율보다 불리한 방향(적용 환율을 낮춰 더 많은 USDC를 요구)으로 적용한다. */
		private val SPREAD_RATE = BigDecimal("0.005")

		/** USDC의 표준 Minor Unit 자릿수(`docs/domain/glossary.md`의 Minor Unit 예시와 동일). */
		private const val TOKEN_DECIMALS = 6

		/** Payment/PaymentQuote/CheckoutSession이 함께 만료되는 유효 시간. */
		private val PAYMENT_VALIDITY: Duration = Duration.ofMinutes(30)

		private const val PAYMENT_CREATED_EVENT_TYPE = "payment.created"
	}
}
