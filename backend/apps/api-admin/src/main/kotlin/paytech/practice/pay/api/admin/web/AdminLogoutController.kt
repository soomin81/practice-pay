package paytech.practice.pay.api.admin.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 내부 운영자 콘솔 로그아웃 API(`POST /admin/logout`)다 — `apps:api-merchant`의
 * `MerchantLogoutController`와 같은 이유·같은 모양이다. 세션을 무효화해서 서버에 저장된
 * 인증 정보를 제거한다.
 *
 * 상태를 바꾸는 POST라 CSRF 보호 대상이다. Spring이 기본 `/logout` 핸들러를 제공하지만,
 * 세 API 앱이 전부 명시적 REST 컨트롤러로 인증 흐름을 노출하는 것과 결을 맞춰 여기서도
 * 명시적으로 둔다(계약이 문서·스펙에 그대로 드러나게 한다).
 */
@RestController
@RequestMapping("/admin/logout")
class AdminLogoutController {
	@PostMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	fun logout(request: HttpServletRequest) {
		// 이미 로그아웃된 상태(세션 없음)여도 안전하다 — getSession(false)는 null을 돌려준다.
		request.getSession(false)?.invalidate()
		SecurityContextHolder.clearContext()
	}
}
