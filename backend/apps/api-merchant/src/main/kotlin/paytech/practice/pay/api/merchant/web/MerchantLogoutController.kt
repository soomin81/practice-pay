package paytech.practice.pay.api.merchant.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 가맹점 콘솔 로그아웃 API(`POST /merchant/logout`)다. 세션을 무효화해서 서버에
 * 저장된 인증 정보(`HttpSessionSecurityContextRepository`가 세션에 담아 둔
 * `SecurityContext`)를 제거한다.
 *
 * 상태를 바꾸는 POST라 CSRF 보호 대상이다 — 프론트는 `X-XSRF-TOKEN`을 실어 호출한다.
 * Spring이 기본 `/logout` 핸들러를 제공하지만, 세 API 앱이 전부 명시적 REST 컨트롤러로
 * 로그인/인증 흐름을 노출하는 것과 결을 맞춰 로그아웃도 명시적으로 둔다(계약이 문서
 * `docs/architecture/merchant-console-api.md`에 그대로 드러나게 한다). 본문 없이 204를
 * 돌려준다.
 */
@RestController
@RequestMapping("/merchant/logout")
class MerchantLogoutController {
	@PostMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun logout(request: HttpServletRequest) {
		// 이미 로그아웃된 상태(세션 없음)여도 안전하다 — getSession(false)는 null을 돌려준다.
		request.getSession(false)?.invalidate()
		SecurityContextHolder.clearContext()
	}
}
