package paytech.practice.pay.api.payment.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import paytech.practice.pay.application.apikey.AuthenticateApiKeyUseCase
import paytech.practice.pay.application.checkout.CancelCheckoutSessionUseCase
import paytech.practice.pay.application.checkout.ConnectCheckoutWalletUseCase
import paytech.practice.pay.application.checkout.GetCheckoutSessionUseCase
import paytech.practice.pay.application.checkout.GetCheckoutStatusUseCase
import paytech.practice.pay.application.payment.CreatePaymentUseCase
import paytech.practice.pay.application.payment.SubmitPaymentTransactionUseCase
import paytech.practice.pay.application.port.outbound.ApiKeySecretHasher
import paytech.practice.pay.application.port.outbound.BlockchainTransactionRepository
import paytech.practice.pay.application.port.outbound.CheckoutSessionRepository
import paytech.practice.pay.application.port.outbound.CheckoutViewProjection
import paytech.practice.pay.application.port.outbound.ExchangeRateProvider
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.MerchantApiKeyRepository
import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.application.port.outbound.PaymentQuoteRepository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import java.time.Clock

/**
 * `modules:application`의 Use Case를 Spring Bean으로 조립하는 Composition Root다.
 *
 * `CreatePaymentUseCase`는 `modules:application`에 있고 그 모듈은 Spring에 의존하지
 * 않으므로(도메인 순수성 규칙이 application 계층까지 이어짐 — `backend/CLAUDE.md`
 * 참고) `@Component`를 직접 붙일 수 없다. 이 앱(inbound adapter) 쪽에서 `@Bean`
 * 메서드로 필요한 outbound port Bean들을 주입받아 대신 조립한다.
 */
@Configuration
class UseCaseConfiguration {
	@Bean
	fun clock(): Clock = Clock.systemUTC()

	@Bean
	fun authenticateApiKeyUseCase(
		merchantApiKeyRepository: MerchantApiKeyRepository,
		merchantRepository: MerchantRepository,
		apiKeySecretHasher: ApiKeySecretHasher,
		transactionManager: TransactionManager,
		clock: Clock,
	): AuthenticateApiKeyUseCase =
		AuthenticateApiKeyUseCase(
			merchantApiKeyRepository = merchantApiKeyRepository,
			merchantRepository = merchantRepository,
			apiKeySecretHasher = apiKeySecretHasher,
			transactionManager = transactionManager,
			clock = clock,
		)

	@Bean
	fun createPaymentUseCase(
		merchantRepository: MerchantRepository,
		paymentRepository: PaymentRepository,
		paymentQuoteRepository: PaymentQuoteRepository,
		checkoutSessionRepository: CheckoutSessionRepository,
		outboxEventRepository: OutboxEventRepository,
		exchangeRateProvider: ExchangeRateProvider,
		idGenerator: IdGenerator,
		transactionManager: TransactionManager,
		clock: Clock,
	): CreatePaymentUseCase =
		CreatePaymentUseCase(
			merchantRepository = merchantRepository,
			paymentRepository = paymentRepository,
			paymentQuoteRepository = paymentQuoteRepository,
			checkoutSessionRepository = checkoutSessionRepository,
			outboxEventRepository = outboxEventRepository,
			exchangeRateProvider = exchangeRateProvider,
			idGenerator = idGenerator,
			transactionManager = transactionManager,
			clock = clock,
		)

	// ── 고객 대면 체크아웃(docs/architecture/checkout-api.md) ─────────────────
	// ConnectCheckoutWalletUseCase와 SubmitPaymentTransactionUseCase는 이전부터
	// modules:application에 구현돼 있었지만 어떤 앱도 배선하지 않아 호출된 적이
	// 없었다 — 이 앱이 그 둘을 처음 노출하는 자리다.

	@Bean
	fun getCheckoutSessionUseCase(checkoutViewProjection: CheckoutViewProjection): GetCheckoutSessionUseCase =
		GetCheckoutSessionUseCase(checkoutViewProjection = checkoutViewProjection)

	@Bean
	fun getCheckoutStatusUseCase(checkoutViewProjection: CheckoutViewProjection): GetCheckoutStatusUseCase =
		GetCheckoutStatusUseCase(checkoutViewProjection = checkoutViewProjection)

	@Bean
	fun connectCheckoutWalletUseCase(
		checkoutSessionRepository: CheckoutSessionRepository,
		transactionManager: TransactionManager,
		clock: Clock,
	): ConnectCheckoutWalletUseCase =
		ConnectCheckoutWalletUseCase(
			checkoutSessionRepository = checkoutSessionRepository,
			transactionManager = transactionManager,
			clock = clock,
		)

	@Bean
	fun submitPaymentTransactionUseCase(
		checkoutSessionRepository: CheckoutSessionRepository,
		paymentRepository: PaymentRepository,
		blockchainTransactionRepository: BlockchainTransactionRepository,
		idGenerator: IdGenerator,
		transactionManager: TransactionManager,
		clock: Clock,
	): SubmitPaymentTransactionUseCase =
		SubmitPaymentTransactionUseCase(
			checkoutSessionRepository = checkoutSessionRepository,
			paymentRepository = paymentRepository,
			blockchainTransactionRepository = blockchainTransactionRepository,
			idGenerator = idGenerator,
			transactionManager = transactionManager,
			clock = clock,
		)

	@Bean
	fun cancelCheckoutSessionUseCase(
		checkoutSessionRepository: CheckoutSessionRepository,
		transactionManager: TransactionManager,
		clock: Clock,
	): CancelCheckoutSessionUseCase =
		CancelCheckoutSessionUseCase(
			checkoutSessionRepository = checkoutSessionRepository,
			transactionManager = transactionManager,
			clock = clock,
		)
}
