package paytech.practice.pay.api.admin.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import paytech.practice.pay.application.identity.AuthenticateInternalUserUseCase
import paytech.practice.pay.application.port.outbound.InternalUserRepository
import paytech.practice.pay.application.port.outbound.PasswordEncoder
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
}
