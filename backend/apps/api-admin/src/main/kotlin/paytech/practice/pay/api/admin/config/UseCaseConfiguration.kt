package paytech.practice.pay.api.admin.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import paytech.practice.pay.application.identity.AcceptAccountInvitationUseCase
import paytech.practice.pay.application.identity.AdminChangeMerchantUserRoleUseCase
import paytech.practice.pay.application.identity.AdminChangeMerchantUserStatusUseCase
import paytech.practice.pay.application.identity.AdminListMerchantUsersUseCase
import paytech.practice.pay.application.identity.AuthenticateInternalUserUseCase
import paytech.practice.pay.application.identity.ChangeInternalUserRoleUseCase
import paytech.practice.pay.application.identity.ChangeInternalUserStatusUseCase
import paytech.practice.pay.application.identity.IssueInternalUserUseCase
import paytech.practice.pay.application.identity.ListInternalLoginAuditUseCase
import paytech.practice.pay.application.identity.ListInternalUsersUseCase
import paytech.practice.pay.application.identity.ListMerchantLoginAuditUseCase
import paytech.practice.pay.application.identity.RegisterMerchantUseCase
import paytech.practice.pay.application.merchant.ListMerchantsUseCase
import paytech.practice.pay.application.payment.ExportPaymentsUseCase
import paytech.practice.pay.application.payment.GetPaymentDetailUseCase
import paytech.practice.pay.application.payment.ListPaymentsUseCase
import paytech.practice.pay.application.payment.MarkTransactionReorgedUseCase
import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.application.port.outbound.BlockchainTransactionRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.InternalLoginAuditProjection
import paytech.practice.pay.application.port.outbound.InternalLoginAuditRepository
import paytech.practice.pay.application.port.outbound.InternalUserListProjection
import paytech.practice.pay.application.port.outbound.InternalUserRepository
import paytech.practice.pay.application.port.outbound.InvitationTokenHasher
import paytech.practice.pay.application.port.outbound.MerchantListProjection
import paytech.practice.pay.application.port.outbound.MerchantLoginAuditProjection
import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.MerchantUserListProjection
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.application.port.outbound.PasswordEncoder
import paytech.practice.pay.application.port.outbound.PaymentDetailProjection
import paytech.practice.pay.application.port.outbound.PaymentExportWriter
import paytech.practice.pay.application.port.outbound.PaymentListProjection
import paytech.practice.pay.application.port.outbound.SettlementExportWriter
import paytech.practice.pay.application.port.outbound.SettlementReceivableListProjection
import paytech.practice.pay.application.port.outbound.SettlementReceivableRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.application.port.outbound.WebhookDeliveryRepository
import paytech.practice.pay.application.settlement.ExportSettlementReceivablesUseCase
import paytech.practice.pay.application.settlement.ListSettlementReceivablesUseCase
import paytech.practice.pay.application.webhook.RedeliverWebhookUseCase
import java.time.Clock

/**
 * `modules:application`의 Use Case를 Spring Bean으로 조립하는 Composition Root다
 * (`apps:api-payment`의 `UseCaseConfiguration`과 같은 이유·같은 모양 —
 * `backend/CLAUDE.md`의 "애플리케이션 계층 컨벤션" 참고).
 */
@Configuration
class UseCaseConfiguration {
	@Bean
	fun clock(): Clock = Clock.systemUTC()

	@Bean
	fun authenticateInternalUserUseCase(
		internalUserRepository: InternalUserRepository,
		passwordEncoder: PasswordEncoder,
		internalLoginAuditRepository: InternalLoginAuditRepository,
		idGenerator: IdGenerator,
		clock: Clock,
	): AuthenticateInternalUserUseCase =
		AuthenticateInternalUserUseCase(
			internalUserRepository = internalUserRepository,
			passwordEncoder = passwordEncoder,
			internalLoginAuditRepository = internalLoginAuditRepository,
			idGenerator = idGenerator,
			clock = clock,
		)

	@Bean
	fun listInternalLoginAuditUseCase(internalLoginAuditProjection: InternalLoginAuditProjection): ListInternalLoginAuditUseCase =
		ListInternalLoginAuditUseCase(internalLoginAuditProjection)

	@Bean
	fun listMerchantLoginAuditUseCase(merchantLoginAuditProjection: MerchantLoginAuditProjection): ListMerchantLoginAuditUseCase =
		ListMerchantLoginAuditUseCase(merchantLoginAuditProjection)

	@Bean
	fun issueInternalUserUseCase(
		internalUserRepository: InternalUserRepository,
		accountInvitationRepository: AccountInvitationRepository,
		invitationTokenHasher: InvitationTokenHasher,
		idGenerator: IdGenerator,
		transactionManager: TransactionManager,
		clock: Clock,
	): IssueInternalUserUseCase =
		IssueInternalUserUseCase(
			internalUserRepository = internalUserRepository,
			accountInvitationRepository = accountInvitationRepository,
			invitationTokenHasher = invitationTokenHasher,
			idGenerator = idGenerator,
			transactionManager = transactionManager,
			clock = clock,
		)

	@Bean
	fun acceptAccountInvitationUseCase(
		accountInvitationRepository: AccountInvitationRepository,
		internalUserRepository: InternalUserRepository,
		merchantUserRepository: MerchantUserRepository,
		invitationTokenHasher: InvitationTokenHasher,
		passwordEncoder: PasswordEncoder,
		transactionManager: TransactionManager,
		clock: Clock,
	): AcceptAccountInvitationUseCase =
		AcceptAccountInvitationUseCase(
			accountInvitationRepository = accountInvitationRepository,
			internalUserRepository = internalUserRepository,
			merchantUserRepository = merchantUserRepository,
			invitationTokenHasher = invitationTokenHasher,
			passwordEncoder = passwordEncoder,
			transactionManager = transactionManager,
			clock = clock,
		)

	@Bean
	fun registerMerchantUseCase(
		merchantRepository: MerchantRepository,
		merchantUserRepository: MerchantUserRepository,
		accountInvitationRepository: AccountInvitationRepository,
		invitationTokenHasher: InvitationTokenHasher,
		idGenerator: IdGenerator,
		transactionManager: TransactionManager,
		clock: Clock,
	): RegisterMerchantUseCase =
		RegisterMerchantUseCase(
			merchantRepository = merchantRepository,
			merchantUserRepository = merchantUserRepository,
			accountInvitationRepository = accountInvitationRepository,
			invitationTokenHasher = invitationTokenHasher,
			idGenerator = idGenerator,
			transactionManager = transactionManager,
			clock = clock,
		)

	@Bean
	fun listMerchantsUseCase(merchantListProjection: MerchantListProjection): ListMerchantsUseCase =
		ListMerchantsUseCase(merchantListProjection)

	@Bean
	fun listInternalUsersUseCase(internalUserListProjection: InternalUserListProjection): ListInternalUsersUseCase =
		ListInternalUsersUseCase(internalUserListProjection)

	@Bean
	fun changeInternalUserStatusUseCase(
		internalUserRepository: InternalUserRepository,
		clock: Clock,
	): ChangeInternalUserStatusUseCase = ChangeInternalUserStatusUseCase(internalUserRepository, clock)

	@Bean
	fun changeInternalUserRoleUseCase(
		internalUserRepository: InternalUserRepository,
		clock: Clock,
	): ChangeInternalUserRoleUseCase = ChangeInternalUserRoleUseCase(internalUserRepository, clock)

	@Bean
	fun adminListMerchantUsersUseCase(merchantUserListProjection: MerchantUserListProjection): AdminListMerchantUsersUseCase =
		AdminListMerchantUsersUseCase(merchantUserListProjection)

	@Bean
	fun adminChangeMerchantUserStatusUseCase(
		merchantUserRepository: MerchantUserRepository,
		clock: Clock,
	): AdminChangeMerchantUserStatusUseCase = AdminChangeMerchantUserStatusUseCase(merchantUserRepository, clock)

	@Bean
	fun adminChangeMerchantUserRoleUseCase(
		merchantUserRepository: MerchantUserRepository,
		clock: Clock,
	): AdminChangeMerchantUserRoleUseCase = AdminChangeMerchantUserRoleUseCase(merchantUserRepository, clock)

	@Bean
	fun listPaymentsUseCase(paymentListProjection: PaymentListProjection): ListPaymentsUseCase = ListPaymentsUseCase(paymentListProjection)

	@Bean
	fun exportPaymentsUseCase(
		paymentListProjection: PaymentListProjection,
		paymentExportWriter: PaymentExportWriter,
	): ExportPaymentsUseCase = ExportPaymentsUseCase(paymentListProjection, paymentExportWriter)

	@Bean
	fun getPaymentDetailUseCase(paymentDetailProjection: PaymentDetailProjection): GetPaymentDetailUseCase =
		GetPaymentDetailUseCase(paymentDetailProjection)

	@Bean
	fun listSettlementReceivablesUseCase(
		settlementReceivableListProjection: SettlementReceivableListProjection,
	): ListSettlementReceivablesUseCase = ListSettlementReceivablesUseCase(settlementReceivableListProjection)

	@Bean
	fun exportSettlementReceivablesUseCase(
		settlementReceivableListProjection: SettlementReceivableListProjection,
		settlementExportWriter: SettlementExportWriter,
	): ExportSettlementReceivablesUseCase = ExportSettlementReceivablesUseCase(settlementReceivableListProjection, settlementExportWriter)

	@Bean
	fun markTransactionReorgedUseCase(
		blockchainTransactionRepository: BlockchainTransactionRepository,
		settlementReceivableRepository: SettlementReceivableRepository,
		transactionManager: TransactionManager,
		clock: Clock,
	): MarkTransactionReorgedUseCase =
		MarkTransactionReorgedUseCase(
			blockchainTransactionRepository = blockchainTransactionRepository,
			settlementReceivableRepository = settlementReceivableRepository,
			transactionManager = transactionManager,
			clock = clock,
		)

	@Bean
	fun redeliverWebhookUseCase(
		webhookDeliveryRepository: WebhookDeliveryRepository,
		outboxEventRepository: OutboxEventRepository,
		transactionManager: TransactionManager,
		clock: Clock,
	): RedeliverWebhookUseCase =
		RedeliverWebhookUseCase(
			webhookDeliveryRepository = webhookDeliveryRepository,
			outboxEventRepository = outboxEventRepository,
			transactionManager = transactionManager,
			clock = clock,
		)
}
