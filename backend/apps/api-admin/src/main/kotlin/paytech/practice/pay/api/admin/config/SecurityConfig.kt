package paytech.practice.pay.api.admin.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
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
import paytech.practice.pay.api.admin.security.CsrfCookieFilter

/**
 * `POST /admin/login`은 인증 없이 열어 두고, `POST /admin/internal-users`(내부 운영자
 * 발급)는 `SUPER_ADMIN` 역할을 요구하며(`docs/architecture/identity-access-api-key.md`의
 * "3.3 발급 정책"), `POST /admin/merchants`(가맹점 등록)는 `SUPER_ADMIN`과
 * `OPERATOR` 둘 다 허용한다("3.2 MVP 역할"이 `OPERATOR`의 업무를 "가맹점·결제·운영
 * 업무"로 정의해서다 — 내부 계정 발급만 `SUPER_ADMIN` 전용이다). 그 외 모든 요청은
 * 인증만 요구한다. Spring Security는 먼저 매칭되는 규칙을 쓰므로 이 두 규칙이
 * `anyRequest` 규칙보다 앞에 있어야 한다. 로그인 성공 시 세션에 저장되는 방식은
 * `AdminLoginController` 참고 — 이 앱은 `docs/architecture/identity-access-api-key.md`가
 * "PG 내부 관리자 **화면**"이라고 부르는 대상이라(가맹점 서버 간 API Key 인증인
 * `MerchantApiKey`와 달리 브라우저 로그인), Bearer 토큰이 아니라 Spring Security의
 * 기본 세션 쿠키 방식을 그대로 쓴다.
 *
 * **`/admin/merchants` 규칙은 `HttpMethod.POST`로 메서드를 좁힌다 — `MerchantController`가
 * `GET /admin/merchants`(목록 조회)를 추가하면서 생긴 필수 구분이다.** 메서드를
 * 좁히지 않고 그냥 `authorize("/admin/merchants", hasAnyRole(...))`로 두면 이
 * 규칙이 그 경로의 모든 HTTP 메서드에 적용돼 `GET`까지 `SUPER_ADMIN`/`OPERATOR`로
 * 막아버린다 — `VIEWER`가 "조회 전용"(`InternalUserRole`의 KDoc)이라는 정의와
 * 정면으로 어긋난다. `GET`은 이 규칙에 안 걸리므로 아래 `anyRequest().authenticated()`로
 * 떨어지고, 그 결과 인증된 내부 사용자(`SUPER_ADMIN`/`OPERATOR`/`VIEWER` 전부)가
 * 목록을 볼 수 있다 — `apps:api-payment`의 `authorize(HttpMethod.POST, "/api/v1/payments",
 * ...)`와 같은 메서드 스코핑 방식이다. 실제 `bootRun` + `curl`로 `VIEWER`가
 * `GET`은 200, `POST`는 403을 받는 것을 확인했다.
 *
 * 허용된 역할이 아닌 인증된 세션이 이 경로들을 호출하면 Spring Security의 기본
 * `AccessDeniedHandler`가 403을 돌려준다 — `apps:api-payment`의 Scope 인가와 같은
 * 수준으로, 이 프로젝트는 인가 실패에 커스텀 JSON 바디를 만들지 않는다.
 *
 * **CORS/CSRF는 `apps:api-merchant`와 같은 레시피다** — 내부 운영자 콘솔(`frontend/admin`)이
 * 붙으면서 예전에 "실제 화면이 붙기 전에 반드시 켜야 한다"고 남겨 뒀던 gap을 닫았다.
 * 근거와 함정(Spring Security 6의 지연 토큰 로딩 등)은 `backend/CLAUDE.md`의 "콘솔
 * CORS/CSRF" 절과 `CsrfCookieFilter`의 KDoc에 있다. `/admin/account-invitations/accept`만
 * CSRF 예외인 것도 같은 이유다(비인증 공개 경로이고 자격증명이 세션 쿠키가 아니라
 * 본문의 초대 Token 자체라, CSRF가 막으려는 상황이 성립하지 않는다).
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
			cors { configurationSource = adminConsoleCorsConfigurationSource() }
			// 미인증은 401로 돌려준다 — 기본 403이면 프론트가 "로그아웃 상태"를 "권한 거부"와
			// 구분할 수 없다(`GET /admin/me`의 401을 곧 "로그인 필요"로 신뢰하게 하는 계약).
			exceptionHandling { authenticationEntryPoint = HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED) }
			csrf {
				csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
				csrfTokenRequestHandler = csrfRequestHandler
				ignoringRequestMatchers("/admin/account-invitations/accept")
			}
			authorizeHttpRequests {
				// 컨테이너의 ERROR 디스패치 경로 — 인증을 요구하면 실제 오류(400/404/405 등)가
				// 전부 401/403으로 가려진다(`apps:api-payment`의 SecurityConfig 주석 참고).
				authorize("/error", permitAll)
				authorize("/admin/login", permitAll)
				authorize("/admin/account-invitations/accept", permitAll)
				// **와일드카드가 필요하다.** 계정 관리 액션(`/{id}/suspend|reactivate|terminate|role`)이
				// 생기면서 정확 경로 규칙(`/admin/internal-users`)으로는 그 하위 경로를 덮지 못하게
				// 됐다 — 그대로 두면 액션 경로가 아래 `anyRequest, authenticated`로 떨어져
				// OPERATOR/VIEWER도 정적 관문을 통과한다. `InternalUserManagementGuard`가 요청자
				// 권한을 다시 확인하지 않고 "여기 왔으면 SUPER_ADMIN"을 전제하므로, 이 1차 방어가
				// 반드시 있어야 한다(그 Guard의 KDoc 참고). 와일드카드는 base 경로
				// (`POST`/`GET /admin/internal-users`)도 함께 덮는다 — 가맹점 쪽
				// `/merchant/merchant-users/**`와 같은 Spring PathPattern 동작이다.
				authorize("/admin/internal-users/**", hasRole("SUPER_ADMIN"))
				authorize(HttpMethod.POST, "/admin/merchants", hasAnyRole("SUPER_ADMIN", "OPERATOR"))
				authorize(anyRequest, authenticated)
			}
		}
		// CsrfFilter가 요청 속성에 토큰을 심은 뒤 실행돼야 한다(api-merchant와 같은 위치).
		http.addFilterAfter(CsrfCookieFilter(), BasicAuthenticationFilter::class.java)
		return http.build()
	}

	/**
	 * 세션 쿠키를 교차 출처로 실어 보내야 하므로 `allowCredentials = true`이고, 그 제약상
	 * `allowedOrigins`에 와일드카드를 쓸 수 없다 — `app.admin-console.allowed-origins`로
	 * 정확히 나열한다(`apps:api-merchant`의 같은 설정과 같은 이유).
	 */
	private fun adminConsoleCorsConfigurationSource(): UrlBasedCorsConfigurationSource {
		val configuration =
			CorsConfiguration().apply {
				allowedOrigins = allowedConsoleOrigins
				allowedMethods = listOf("GET", "POST", "DELETE", "OPTIONS")
				// X-XSRF-TOKEN은 SPA가 CSRF 토큰을 되돌려주는 헤더라 반드시 허용해야 한다.
				allowedHeaders = listOf("Content-Type", "X-XSRF-TOKEN")
				allowCredentials = true
				maxAge = CORS_MAX_AGE_SECONDS
			}

		return UrlBasedCorsConfigurationSource().apply {
			registerCorsConfiguration("/admin/**", configuration)
		}
	}

	@Value("\${app.admin-console.allowed-origins}")
	private lateinit var allowedConsoleOrigins: List<String>

	/** `AdminLoginController`가 로그인 성공 후 인증 정보를 세션에 저장할 때 쓴다. */
	@Bean
	fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()

	private companion object {
		private const val CORS_MAX_AGE_SECONDS = 3600L
	}
}
