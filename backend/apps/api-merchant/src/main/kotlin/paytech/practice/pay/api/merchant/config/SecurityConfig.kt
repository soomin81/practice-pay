package paytech.practice.pay.api.merchant.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.HttpStatusEntryPoint
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import paytech.practice.pay.api.merchant.security.CsrfCookieFilter

/**
 * `apps:api-admin`의 `SecurityConfig`와 같은 세션 쿠키 인증을 쓰되, **실제 브라우저
 * 프론트엔드(가맹점 콘솔)가 붙는 첫 앱이라 CORS와 CSRF를 실제로 켠다** — 예전
 * 주석에 "CSRF는 실제 프론트엔드가 붙기 전에 반드시 켜야 한다"고 남겨 둔 gap을
 * 여기서 닫는다. 브라우저 대면 계약 전체는 `docs/architecture/merchant-console-api.md`에
 * 있고, 구현 판단은 `backend/IMPLEMENTATION-NOTES.md`의 "가맹점 콘솔 CORS/CSRF" 절에 있다.
 *
 * **CSRF(세션 쿠키 SPA 표준 레시피).** [CookieCsrfTokenRepository.withHttpOnlyFalse]로
 * `XSRF-TOKEN` 쿠키를 내리고, SPA가 그 값을 `X-XSRF-TOKEN` 헤더로 되돌려주면
 * 검증한다. [CsrfTokenRequestAttributeHandler]를 쓰고 `setCsrfRequestAttributeName(null)`로
 * 지연 로딩을 꺼서, [CsrfCookieFilter]가 안전한 GET(`GET /merchant/me`) 응답에도
 * 쿠키를 실을 수 있게 한다(그 필터의 KDoc 참고). BREACH 보호용 `XorCsrfTokenRequestAttributeHandler`
 * 대신 평범한 핸들러를 쓰는 건 "쿠키 값 = 헤더 값"이라야 JS가 단순해지기 때문으로,
 * Spring 공식 SPA 레시피가 택한 트레이드오프 그대로다.
 *
 * **`/merchant/account-invitations/accept`만 CSRF에서 제외한다.** 이 엔드포인트는
 * 비인증 공개 경로이고, 자격증명이 세션 쿠키가 아니라 **요청 본문의 초대 Token
 * 자체**다(브라우저가 쿠키를 자동으로 실어 보내 악용될 표면이 없다 — CSRF가 막으려는
 * 상황이 성립하지 않는다). 이메일 링크로 도달하는 활성화 페이지라 토큰을 미리 받아올
 * GET을 앞에 둘 수도 없어서, 제외가 더 맞다. 로그인은 제외하지 않는다 — SPA가
 * `GET /merchant/me`로 토큰을 먼저 확보한 뒤 로그인 POST에 실으므로 보호할 수 있다.
 *
 * **CORS.** 세션 쿠키를 교차 출처로 실어 보내야 하므로 `allowCredentials = true`이고,
 * 그 제약상 `allowedOrigins`에 와일드카드를 쓸 수 없다 — 허용 Origin은
 * `app.merchant-console.allowed-origins`(환경변수 `APP_MERCHANT_CONSOLE_ALLOWED_ORIGINS`)로
 * 정확히 나열한다. `api-payment`의 체크아웃 CORS와 달리 `allowCredentials`가 켜져 있고
 * `X-XSRF-TOKEN` 헤더를 허용 목록에 더한 것이 차이다.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {
	@Bean
	fun filterChain(http: HttpSecurity): SecurityFilterChain {
		val csrfRequestHandler =
			CsrfTokenRequestAttributeHandler().apply {
				// null로 두면 토큰이 지연 로딩되지 않고 CsrfToken 클래스 이름 속성에 담긴다 —
				// CsrfCookieFilter가 그 이름으로 꺼내 읽어 쿠키를 강제로 렌더한다.
				setCsrfRequestAttributeName(null)
			}

		http {
			cors { configurationSource = merchantConsoleCorsConfigurationSource() }
			// 미인증 요청은 401로 돌려준다 — 기본 엔트리포인트는 403을 내는데, 그러면
			// 프론트가 "로그아웃 상태(401)"를 "CSRF/권한 거부(403)"와 구분할 수 없다.
			// `GET /merchant/me`의 401을 곧 "로그인 필요"로 신뢰하게 하는 API 계약이다.
			exceptionHandling { authenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED) }
			csrf {
				csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
				csrfTokenRequestHandler = csrfRequestHandler
				// 비인증 공개 경로 + 본문 Token이 곧 자격증명 → 세션 쿠키 CSRF 대상이 아니다(위 KDoc).
				ignoringRequestMatchers("/merchant/account-invitations/accept")
			}
			authorizeHttpRequests {
				// 컨테이너의 ERROR 디스패치 경로 — 인증을 요구하면 실제 오류(400/404/405 등)가
				// 전부 401/403으로 가려진다(`apps:api-payment`의 SecurityConfig 주석 참고).
				authorize("/error", permitAll)
				authorize("/merchant/login", permitAll)
				authorize("/merchant/account-invitations/accept", permitAll)
				// **와일드카드가 필요하다.** 계정 관리 액션(`/{id}/suspend` 등)이 생기면서
				// 정확 경로 규칙(`/merchant/merchant-users`)으로는 그 하위 경로를 덮지 못하게 됐다 —
				// 그대로 두면 액션 경로가 아래 `anyRequest, authenticated`로 떨어져 VIEWER도
				// 정적 관문을 통과한다(Use Case가 막긴 하지만 1차 방어가 사라진다).
				// 이 와일드카드는 base 경로(`POST`/`GET /merchant/merchant-users`)도 함께 덮는다
				// — `/merchant/api-keys/**`에서 이미 확인한 Spring PathPattern 동작이다.
				authorize("/merchant/merchant-users/**", hasAnyRole("OWNER", "ADMIN"))
				authorize("/merchant/api-keys/**", hasAnyRole("OWNER", "ADMIN"))
				authorize(anyRequest, authenticated)
			}
		}
		// CsrfFilter가 요청 속성에 토큰을 심은 뒤 실행돼야 한다 — BasicAuthenticationFilter는
		// 표준 필터 순서상 CsrfFilter 뒤라, httpBasic을 안 켰어도 위치 기준으로 유효하다
		// (Spring 공식 SPA 레시피와 같은 위치).
		http.addFilterAfter(CsrfCookieFilter(), BasicAuthenticationFilter::class.java)
		return http.build()
	}

	private fun merchantConsoleCorsConfigurationSource(): UrlBasedCorsConfigurationSource {
		val configuration =
			CorsConfiguration().apply {
				allowedOrigins = allowedConsoleOrigins
				allowedMethods = listOf("GET", "POST", "DELETE", "OPTIONS")
				// X-XSRF-TOKEN은 SPA가 CSRF 토큰을 되돌려주는 헤더라 반드시 허용해야 한다.
				allowedHeaders = listOf("Content-Type", "X-XSRF-TOKEN")
				// 교차 출처에서는 JS가 기본적으로 몇 개의 표준 헤더만 읽을 수 있다. 결제 내역
				// 내보내기가 "상한을 넘어 잘렸다"를 이 헤더로 알리므로 명시적으로 노출한다 —
				// 빠뜨리면 프론트가 잘림을 알 수 없어 사용자가 일부만 담긴 파일을 그냥 받아간다.
				// Content-Disposition도 노출해야 프론트가 서버가 정한 파일 이름을 그대로 쓴다
				// (이름 규칙이 두 곳으로 갈리지 않게 한다).
				exposedHeaders = listOf("X-Export-Truncated", "Content-Disposition")
				allowCredentials = true
				maxAge = CORS_MAX_AGE_SECONDS
			}

		return UrlBasedCorsConfigurationSource().apply {
			registerCorsConfiguration("/merchant/**", configuration)
		}
	}

	@Value("\${app.merchant-console.allowed-origins}")
	private lateinit var allowedConsoleOrigins: List<String>

	/** `MerchantLoginController`가 로그인 성공 후 인증 정보를 세션에 저장할 때 쓴다. */
	@Bean
	fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

	private companion object {
		private const val CORS_MAX_AGE_SECONDS = 3600L
	}
}
