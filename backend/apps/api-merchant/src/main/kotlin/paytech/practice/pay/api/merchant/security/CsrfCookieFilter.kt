package paytech.practice.pay.api.merchant.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.filter.OncePerRequestFilter

/**
 * SPA(가맹점 콘솔)가 `XSRF-TOKEN` 쿠키를 읽어 `X-XSRF-TOKEN` 헤더로 되돌려주는
 * 방식으로 CSRF를 방어할 때 필요한 보조 필터다 — Spring Security 6의 공식 SPA
 * 레시피(Reference의 "Integrating with a JavaScript application → Single Page
 * Application")를 그대로 옮긴 것이다.
 *
 * **왜 필요한가:** Spring Security 6은 [CsrfToken]을 지연 로딩한다 — 실제로 토큰을
 * "읽는" 코드가 없으면 [org.springframework.security.web.csrf.CookieCsrfTokenRepository]가
 * 응답에 `XSRF-TOKEN` 쿠키를 싣지 않는다. 그러면 프론트는 로그인 POST에 실을
 * 토큰을 얻을 방법이 없다. 이 필터가 요청마다 토큰 값을 한 번 읽어(`.token`) 지연
 * 로딩을 강제로 깨워서, 안전한 GET(예: `GET /merchant/me`) 응답에도 쿠키가 실리게
 * 한다 — 프론트는 그 GET으로 토큰을 먼저 확보한 뒤 상태 변경 요청을 보낸다.
 *
 * `SecurityConfig`가 `CsrfTokenRequestAttributeHandler.setCsrfRequestAttributeName(null)`로
 * 토큰을 [CsrfToken] 클래스 이름 속성에 담고 지연 로딩을 끄기 때문에, 여기서 그
 * 이름으로 꺼내 읽는다.
 */
class CsrfCookieFilter : OncePerRequestFilter() {
	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain,
	) {
		val csrfToken = request.getAttribute(CsrfToken::class.java.name) as CsrfToken?
		// 토큰 값을 실제로 읽어야 CookieCsrfTokenRepository가 XSRF-TOKEN 쿠키를 응답에 싣는다.
		csrfToken?.token
		filterChain.doFilter(request, response)
	}
}
