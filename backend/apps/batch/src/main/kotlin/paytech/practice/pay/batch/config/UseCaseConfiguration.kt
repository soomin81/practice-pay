package paytech.practice.pay.batch.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import paytech.practice.pay.application.exchange.SellToFakeExchangeUseCase
import paytech.practice.pay.application.outbox.PublishOutboxEventUseCase
import paytech.practice.pay.application.payment.ConfirmBlockchainTransactionUseCase
import paytech.practice.pay.application.port.outbound.BlockchainClient
import paytech.practice.pay.application.port.outbound.BlockchainTransactionRepository
import paytech.practice.pay.application.port.outbound.ExchangeOrderRepository
import paytech.practice.pay.application.port.outbound.ExchangeRateProvider
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.SettlementReceivableRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.application.port.outbound.WebhookDeliveryRepository
import paytech.practice.pay.application.port.outbound.WebhookSender
import java.time.Clock

/**
 * `modules:application`의 Use Case를 Spring Bean으로 조립하는 Composition Root다
 * (`apps:api-payment`의 `UseCaseConfiguration`과 같은 이유·같은 모양).
 */
@Configuration
class UseCaseConfiguration {
	@Bean
	fun clock(): Clock = Clock.systemUTC()

	@Bean
	fun confirmBlockchainTransactionUseCase(
		blockchainTransactionRepository: BlockchainTransactionRepository,
		paymentRepository: PaymentRepository,
		outboxEventRepository: OutboxEventRepository,
		blockchainClient: BlockchainClient,
		idGenerator: IdGenerator,
		transactionManager: TransactionManager,
		clock: Clock,
	): ConfirmBlockchainTransactionUseCase =
		ConfirmBlockchainTransactionUseCase(
			blockchainTransactionRepository = blockchainTransactionRepository,
			paymentRepository = paymentRepository,
			outboxEventRepository = outboxEventRepository,
			blockchainClient = blockchainClient,
			idGenerator = idGenerator,
			transactionManager = transactionManager,
			clock = clock,
		)

	@Bean
	fun publishOutboxEventUseCase(
		outboxEventRepository: OutboxEventRepository,
		webhookDeliveryRepository: WebhookDeliveryRepository,
		paymentRepository: PaymentRepository,
		merchantRepository: MerchantRepository,
		webhookSender: WebhookSender,
		idGenerator: IdGenerator,
		transactionManager: TransactionManager,
		clock: Clock,
	): PublishOutboxEventUseCase =
		PublishOutboxEventUseCase(
			outboxEventRepository = outboxEventRepository,
			webhookDeliveryRepository = webhookDeliveryRepository,
			paymentRepository = paymentRepository,
			merchantRepository = merchantRepository,
			webhookSender = webhookSender,
			idGenerator = idGenerator,
			transactionManager = transactionManager,
			clock = clock,
		)

	@Bean
	fun sellToFakeExchangeUseCase(
		paymentRepository: PaymentRepository,
		exchangeOrderRepository: ExchangeOrderRepository,
		settlementReceivableRepository: SettlementReceivableRepository,
		outboxEventRepository: OutboxEventRepository,
		exchangeRateProvider: ExchangeRateProvider,
		idGenerator: IdGenerator,
		transactionManager: TransactionManager,
		clock: Clock,
	): SellToFakeExchangeUseCase =
		SellToFakeExchangeUseCase(
			paymentRepository = paymentRepository,
			exchangeOrderRepository = exchangeOrderRepository,
			settlementReceivableRepository = settlementReceivableRepository,
			outboxEventRepository = outboxEventRepository,
			exchangeRateProvider = exchangeRateProvider,
			idGenerator = idGenerator,
			transactionManager = transactionManager,
			clock = clock,
		)
}
