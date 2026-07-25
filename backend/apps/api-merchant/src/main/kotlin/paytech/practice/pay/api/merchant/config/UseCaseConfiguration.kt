package paytech.practice.pay.api.merchant.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import paytech.practice.pay.application.apikey.IssueMerchantApiKeyUseCase
import paytech.practice.pay.application.apikey.ListMerchantApiKeysUseCase
import paytech.practice.pay.application.apikey.RevokeMerchantApiKeyUseCase
import paytech.practice.pay.application.identity.AcceptAccountInvitationUseCase
import paytech.practice.pay.application.identity.AuthenticateMerchantUserUseCase
import paytech.practice.pay.application.identity.ChangeMerchantUserRoleUseCase
import paytech.practice.pay.application.identity.ChangeMerchantUserStatusUseCase
import paytech.practice.pay.application.identity.InviteMerchantSubAccountUseCase
import paytech.practice.pay.application.identity.ListMerchantUsersUseCase
import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.application.port.outbound.ApiKeySecretHasher
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.InternalUserRepository
import paytech.practice.pay.application.port.outbound.InvitationTokenHasher
import paytech.practice.pay.application.port.outbound.MerchantApiKeyListProjection
import paytech.practice.pay.application.port.outbound.MerchantApiKeyRepository
import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.MerchantUserListProjection
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.application.port.outbound.PasswordEncoder
import paytech.practice.pay.application.port.outbound.TransactionManager
import java.time.Clock

/**
 * `modules:application`의 Use Case를 Spring Bean으로 조립하는 Composition Root다
 * (`apps:api-admin`의 `UseCaseConfiguration`과 같은 이유·같은 모양 —
 * `backend/CLAUDE.md`의 "애플리케이션 계층 컨벤션" 참고).
 */
@Configuration
class UseCaseConfiguration {
	@Bean
	fun clock(): Clock = Clock.systemUTC()

	@Bean
	fun authenticateMerchantUserUseCase(
		merchantRepository: MerchantRepository,
		merchantUserRepository: MerchantUserRepository,
		passwordEncoder: PasswordEncoder,
		clock: Clock,
	): AuthenticateMerchantUserUseCase =
		AuthenticateMerchantUserUseCase(
			merchantRepository = merchantRepository,
			merchantUserRepository = merchantUserRepository,
			passwordEncoder = passwordEncoder,
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
	fun inviteMerchantSubAccountUseCase(
		merchantUserRepository: MerchantUserRepository,
		accountInvitationRepository: AccountInvitationRepository,
		invitationTokenHasher: InvitationTokenHasher,
		idGenerator: IdGenerator,
		transactionManager: TransactionManager,
		clock: Clock,
	): InviteMerchantSubAccountUseCase =
		InviteMerchantSubAccountUseCase(
			merchantUserRepository = merchantUserRepository,
			accountInvitationRepository = accountInvitationRepository,
			invitationTokenHasher = invitationTokenHasher,
			idGenerator = idGenerator,
			transactionManager = transactionManager,
			clock = clock,
		)

	@Bean
	fun issueMerchantApiKeyUseCase(
		merchantUserRepository: MerchantUserRepository,
		merchantApiKeyRepository: MerchantApiKeyRepository,
		apiKeySecretHasher: ApiKeySecretHasher,
		idGenerator: IdGenerator,
		clock: Clock,
	): IssueMerchantApiKeyUseCase =
		IssueMerchantApiKeyUseCase(
			merchantUserRepository = merchantUserRepository,
			merchantApiKeyRepository = merchantApiKeyRepository,
			apiKeySecretHasher = apiKeySecretHasher,
			idGenerator = idGenerator,
			clock = clock,
		)

	@Bean
	fun revokeMerchantApiKeyUseCase(
		merchantUserRepository: MerchantUserRepository,
		merchantApiKeyRepository: MerchantApiKeyRepository,
		clock: Clock,
	): RevokeMerchantApiKeyUseCase =
		RevokeMerchantApiKeyUseCase(
			merchantUserRepository = merchantUserRepository,
			merchantApiKeyRepository = merchantApiKeyRepository,
			clock = clock,
		)

	@Bean
	fun listMerchantApiKeysUseCase(
		merchantUserRepository: MerchantUserRepository,
		merchantApiKeyListProjection: MerchantApiKeyListProjection,
	): ListMerchantApiKeysUseCase =
		ListMerchantApiKeysUseCase(
			merchantUserRepository = merchantUserRepository,
			merchantApiKeyListProjection = merchantApiKeyListProjection,
		)

	@Bean
	fun listMerchantUsersUseCase(
		merchantUserRepository: MerchantUserRepository,
		merchantUserListProjection: MerchantUserListProjection,
	): ListMerchantUsersUseCase =
		ListMerchantUsersUseCase(
			merchantUserRepository = merchantUserRepository,
			merchantUserListProjection = merchantUserListProjection,
		)

	@Bean
	fun changeMerchantUserStatusUseCase(
		merchantUserRepository: MerchantUserRepository,
		clock: Clock,
	): ChangeMerchantUserStatusUseCase = ChangeMerchantUserStatusUseCase(merchantUserRepository, clock)

	@Bean
	fun changeMerchantUserRoleUseCase(
		merchantUserRepository: MerchantUserRepository,
		clock: Clock,
	): ChangeMerchantUserRoleUseCase = ChangeMerchantUserRoleUseCase(merchantUserRepository, clock)
}
