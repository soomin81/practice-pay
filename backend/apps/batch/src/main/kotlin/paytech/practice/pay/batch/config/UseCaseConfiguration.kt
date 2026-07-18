package paytech.practice.pay.batch.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import paytech.practice.pay.application.payment.ConfirmBlockchainTransactionUseCase
import paytech.practice.pay.application.port.outbound.BlockchainClient
import paytech.practice.pay.application.port.outbound.BlockchainTransactionRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
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
}
