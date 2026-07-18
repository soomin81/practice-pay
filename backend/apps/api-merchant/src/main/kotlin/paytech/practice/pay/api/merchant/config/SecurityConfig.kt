package paytech.practice.pay.api.merchant.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository

/**
 * `apps:api-admin`의 `SecurityConfig`와 같은 모양·같은 이유다 — `POST /merchant/login`만
 * 인증 없이 열고, 세션 쿠키 인증을 쓰며, CSRF 보호를 껐다(같은 알려진 gap — 실제
 * 프론트엔드가 붙기 전에 반드시 켜야 한다). `POST /merchant/merchant-users`(하위
 * 계정 발급)는 `OWNER`/`ADMIN` 역할을 요구한다(`docs/architecture/identity-access-api-key.md`의
 * "4.4 하위 계정 발급": "`OWNER`, `ADMIN`은 하위 계정을 발급할 수 있다") — 이건
 * 정적인 1차 관문일 뿐이고, `ACTIVE` 상태까지 포함한 최종 판단은
 * `InviteMerchantSubAccountUseCase`가 요청자의 `MerchantUser`를 다시 읽어서 한다
 * (그 Use Case의 KDoc 참고).
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {
	@Bean
	fun filterChain(http: HttpSecurity): SecurityFilterChain {
		http {
			csrf { disable() }
			authorizeHttpRequests {
				// 컨테이너의 ERROR 디스패치 경로 — 인증을 요구하면 실제 오류(400/404/405 등)가
				// 전부 401/403으로 가려진다(`apps:api-payment`의 SecurityConfig 주석 참고).
				authorize("/error", permitAll)
				authorize("/merchant/login", permitAll)
				authorize("/merchant/account-invitations/accept", permitAll)
				authorize("/merchant/merchant-users", hasAnyRole("OWNER", "ADMIN"))
				authorize(anyRequest, authenticated)
			}
		}
		return http.build()
	}

	/** `MerchantLoginController`가 로그인 성공 후 인증 정보를 세션에 저장할 때 쓴다. */
	@Bean
	fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()
}
