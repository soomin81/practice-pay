package paytech.practice.pay.api.admin.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.springframework.security.web.context.SecurityContextRepository

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
 * **알려진 gap: CSRF 보호를 꺼뒀다.** 세션 쿠키 기반 인증에서 CSRF는 원래 반드시
 * 막아야 하는 것이지만, 이 학습용 MVP 단계에서는 아직 CSRF 토큰 발급/검증 흐름을
 * 만들지 않았다 — 실제 화면(프론트엔드)이 붙기 전에 반드시 켜야 한다.
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
				authorize("/admin/login", permitAll)
				authorize("/admin/account-invitations/accept", permitAll)
				authorize("/admin/internal-users", hasRole("SUPER_ADMIN"))
				authorize(HttpMethod.POST, "/admin/merchants", hasAnyRole("SUPER_ADMIN", "OPERATOR"))
				authorize(anyRequest, authenticated)
			}
		}
		return http.build()
	}

	/** `AdminLoginController`가 로그인 성공 후 인증 정보를 세션에 저장할 때 쓴다. */
	@Bean
	fun securityContextRepository(): SecurityContextRepository = HttpSessionSecurityContextRepository()
}
