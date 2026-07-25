package paytech.practice.pay.api.admin.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import paytech.practice.pay.application.identity.AcceptAccountInvitationUseCase
import paytech.practice.pay.application.identity.AuthenticateInternalUserUseCase
import paytech.practice.pay.application.identity.IssueInternalUserUseCase
import paytech.practice.pay.application.identity.ListInternalUsersUseCase
import paytech.practice.pay.application.identity.RegisterMerchantUseCase
import paytech.practice.pay.application.merchant.ListMerchantsUseCase
import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.InternalUserListProjection
import paytech.practice.pay.application.port.outbound.InternalUserRepository
import paytech.practice.pay.application.port.outbound.InvitationTokenHasher
import paytech.practice.pay.application.port.outbound.MerchantListProjection
import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.application.port.outbound.PasswordEncoder
import paytech.practice.pay.application.port.outbound.TransactionManager
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
		clock: Clock,
	): AuthenticateInternalUserUseCase =
		AuthenticateInternalUserUseCase(
			internalUserRepository = internalUserRepository,
			passwordEncoder = passwordEncoder,
			clock = clock,
		)

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
}
