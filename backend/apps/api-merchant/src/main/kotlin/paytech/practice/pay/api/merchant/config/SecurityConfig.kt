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
 * (그 Use Case의 KDoc 참고). `/merchant/api-keys` 아래의 와일드카드 규칙(발급·폐기)도
 * 같은 역할 요구를 갖는다 — 그 와일드카드가 `POST /merchant/api-keys`(경로 변수
 * 없음)와 `DELETE /merchant/api-keys/{merchantApiKeyId}`(경로 변수 있음)를 한
 * 규칙으로 함께 덮는다(Spring의 `PathPattern`에서 이 와일드카드는 0개 이상의
 * 하위 경로에 매칭된다 — 실제 `bootRun`으로 두 메서드 다 확인했다).
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
				authorize("/merchant/api-keys/**", hasAnyRole("OWNER", "ADMIN"))
				authorize(anyRequest, authenticated)
			}
		}
		return http.build()
	}

	/** `MerchantLoginController`가 로그인 성공 후 인증 정보를 세션에 저장할 때 쓴다. */
	@Bean
	fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()
}
