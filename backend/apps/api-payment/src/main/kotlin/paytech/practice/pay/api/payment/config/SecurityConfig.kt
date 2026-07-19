package paytech.practice.pay.api.payment.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
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
 * 아래에서 여는 체크아웃 경로도 마찬가지로 쿠키를 쓰지 않는다.
 *
 * **이 앱은 인증 모델이 둘이다** — 가맹점 서버용 API Key(Bearer)와, 자격증명이 아예
 * 없는 고객 대면 체크아웃(`checkoutSessionId`가 곧 권한). 한 앱에 담은 이유와 그
 * 판단의 근거는 `docs/architecture/checkout-api.md`의 2.1에 있다. 둘이 섞여도
 * 안전한 이유는 [ApiKeyAuthenticationFilter]가 `Authorization` 헤더가 없을 때
 * 예외를 던지지 않고 `SecurityContext`만 비운 뒤 통과시키기 때문이다 —
 * 체크아웃 요청은 그대로 흘러가 아래 `permitAll` 규칙을 만난다.
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
			cors { configurationSource = checkoutCorsConfigurationSource() }
			sessionManagement { sessionCreationPolicy = SessionCreationPolicy.STATELESS }
			exceptionHandling { authenticationEntryPoint = apiKeyAuthenticationEntryPoint }
			authorizeHttpRequests {
				// 컨테이너가 오류 응답을 만들 때 거치는 ERROR 디스패치 경로다. 인증을 요구하면
				// 실제 오류가 전부 401로 가려진다 — ApiKeyAuthenticationFilter는
				// OncePerRequestFilter 기본값상 ERROR 디스패치에서 실행되지 않아
				// SecurityContext가 비어 있고, 그래서 잘못된 요청 본문(400)이나 404/405가
				// 전부 "API Key가 유효하지 않습니다"(401)로 나갔다(실제 bootRun에서 확인).
				authorize("/error", permitAll)
				// 고객 대면 체크아웃 — 자격증명이 없다(checkoutSessionId가 곧 권한).
				// CORS Preflight(OPTIONS)도 인증 없이 통과해야 해서 메서드를 좁히지 않는다.
				authorize("/checkout/**", permitAll)
				// 위 permitAll을 넓히더라도 이 규칙은 그대로 남아야 한다 — 결제 생성은
				// 여전히 가맹점 API Key와 Scope를 요구한다(CheckoutControllerTest가 검증).
				authorize(HttpMethod.POST, "/api/v1/payments", hasAuthority("SCOPE_PAYMENT_CREATE"))
				authorize(anyRequest, authenticated)
			}
		}
		http.addFilterBefore(apiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
		return http.build()
	}

	/**
	 * **CORS를 체크아웃 경로에만 등록한다 — 앱 전체에 걸지 않는다.**
	 *
	 * 브라우저에서 호출되는 것은 체크아웃뿐이다. 전역으로 열면 API Key로 보호되는
	 * `POST /api/v1/payments`까지 브라우저 호출 대상이 되어, 가맹점 서버에만 있어야 할
	 * 표면이 웹 페이지로 넓어진다 — 이 병합에서 가장 실수하기 쉬운 지점이라
	 * `docs/architecture/checkout-api.md`의 2.1에도 못박아 뒀다.
	 *
	 * [ALLOWED_ORIGINS]는 로컬 개발용 기본값이고 운영에서는 환경변수
	 * `APP_CHECKOUT_ALLOWED_ORIGINS`로 덮어쓴다(`application.yaml` 참고).
	 * `allowCredentials`는 켜지 않는다 — 체크아웃은 쿠키를 쓰지 않아서 필요 없고,
	 * 켜면 `allowedOrigins`에 와일드카드를 쓸 수 없게 되는 제약만 생긴다.
	 */
	private fun checkoutCorsConfigurationSource(): UrlBasedCorsConfigurationSource {
		val configuration =
			CorsConfiguration().apply {
				allowedOrigins = allowedCheckoutOrigins
				allowedMethods = listOf("GET", "POST", "OPTIONS")
				allowedHeaders = listOf("Content-Type")
				allowCredentials = false
				maxAge = CORS_MAX_AGE_SECONDS
			}

		return UrlBasedCorsConfigurationSource().apply {
			registerCorsConfiguration("/checkout/**", configuration)
		}
	}

	@Value("\${app.checkout.allowed-origins}")
	private lateinit var allowedCheckoutOrigins: List<String>

	private companion object {
		private const val CORS_MAX_AGE_SECONDS = 3600L
	}
}
