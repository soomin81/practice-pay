package paytech.practice.pay.api.payment.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import paytech.practice.pay.api.payment.security.ApiKeyAuthenticationEntryPoint
import paytech.practice.pay.api.payment.security.ApiKeyAuthenticationFilter
import paytech.practice.pay.application.apikey.AuthenticateApiKeyUseCase
import tools.jackson.databind.ObjectMapper

/**
 * `apps:api-admin`/`apps:api-merchant`의 `SecurityConfig`(세션 쿠키 인증)와는
 * 성격이 다르다 — `MerchantApiKey`는 브라우저 로그인이 아니라 가맹점 서버가
 * 요청마다 다시 제시하는 서버 간 자격증명이라(`docs/architecture/identity-access-api-key.md`의
 * "6.1 정의") 세션을 만들지 않는다(`SessionCreationPolicy.STATELESS`).
 *
 * **CSRF는 여기서 끄는 게 알려진 gap이 아니라 올바른 선택이다** — admin/merchant의
 * CSRF-끔은 세션 쿠키 인증에서 원래 반드시 켜야 하는 걸 아직 안 켠 것이지만,
 * 이 앱은 세션 쿠키 자체를 안 쓰는 순수 Bearer 토큰 인증이라 CSRF 공격 대상이
 * 애초에 성립하지 않는다(브라우저가 쿠키를 자동으로 실어 보내는 상황이 없다).
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {
	@Bean
	fun apiKeyAuthenticationFilter(authenticateApiKeyUseCase: AuthenticateApiKeyUseCase): ApiKeyAuthenticationFilter =
		ApiKeyAuthenticationFilter(authenticateApiKeyUseCase)

	@Bean
	fun apiKeyAuthenticationEntryPoint(objectMapper: ObjectMapper): ApiKeyAuthenticationEntryPoint =
		ApiKeyAuthenticationEntryPoint(objectMapper)

	@Bean
	fun filterChain(
		http: HttpSecurity,
		apiKeyAuthenticationFilter: ApiKeyAuthenticationFilter,
		apiKeyAuthenticationEntryPoint: ApiKeyAuthenticationEntryPoint,
	): SecurityFilterChain {
		http {
			csrf { disable() }
			sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
			exceptionHandling { authenticationEntryPoint = apiKeyAuthenticationEntryPoint }
			authorizeHttpRequests {
				// 컨테이너가 오류 응답을 만들 때 거치는 ERROR 디스패치 경로다. 인증을 요구하면
				// 실제 오류가 전부 401로 가려진다 — ApiKeyAuthenticationFilter는
				// OncePerRequestFilter 기본값상 ERROR 디스패치에서 실행되지 않아
				// SecurityContext가 비어 있고, 그래서 잘못된 요청 본문(400)이나 404/405가
				// 전부 "API Key가 유효하지 않습니다"(401)로 나갔다(실제 bootRun에서 확인).
				authorize("/error", permitAll)
				authorize(HttpMethod.POST, "/api/v1/payments", hasAuthority("SCOPE_PAYMENT_CREATE"))
				authorize(anyRequest, authenticated)
			}
		}
		http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
		return http.build()
	}
}
