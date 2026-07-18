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
 * 프론트엔드가 붙기 전에 반드시 켜야 한다).
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
				authorize(anyRequest, authenticated)
			}
		}
		return http.build()
	}

	/** `MerchantLoginController`가 로그인 성공 후 인증 정보를 세션에 저장할 때 쓴다. */
	@Bean
	fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()
}
