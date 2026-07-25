package paytech.practice.pay.api.admin.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.filter.OncePerRequestFilter

/**
 * `apps:api-merchant`의 같은 이름 파일과 **같은 이유·같은 코드**다 — 두 앱이 서로를
 * 모르는 것이 이 저장소의 구조라(앱은 독립 배포 단위이고 서로 의존하지 않는다) 클래스를
 * 공유하지 않고 복제한다. 한쪽을 고치면 다른 쪽도 함께 본다.
 *
 * SPA(내부 운영자 콘솔)가 `XSRF-TOKEN` 쿠키를 읽어 `X-XSRF-TOKEN` 헤더로 되돌려주는
 * 방식으로 CSRF를 방어할 때 필요하다. Spring Security 6은 [CsrfToken]을 지연 로딩해서,
 * 실제로 토큰을 "읽는" 코드가 없으면 응답에 `XSRF-TOKEN` 쿠키가 실리지 않는다 — 그러면
 * 프론트가 첫 POST에 실을 토큰을 얻을 방법이 없다. 이 필터가 요청마다 토큰 값을 한 번
 * 읽어(`.token`) 지연 로딩을 깨워서, 안전한 GET(`GET /admin/me`) 응답에도 쿠키가 실리게 한다.
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
