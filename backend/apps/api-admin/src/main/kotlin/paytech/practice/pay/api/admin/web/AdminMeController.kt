package paytech.practice.pay.api.admin.web

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.api.admin.security.InternalUserPrincipal

/**
 * 현재 세션의 로그인 사용자를 돌려주는 API(`GET /admin/me`)다 — `apps:api-merchant`의
 * `MerchantMeController`와 같은 이유·같은 모양이다.
 *
 * 프론트엔드는 로그인 응답을 쿠키 말고는 저장하지 않으므로 새로고침 후 이 엔드포인트로
 * 세션을 복원한다. 미인증이면 `SecurityConfig`의 `anyRequest, authenticated` 규칙이
 * 401로 막고, 프론트는 그 401을 "로그아웃 상태"로 해석한다.
 *
 * **이 GET은 CSRF 토큰 발급도 겸한다** —
 * [paytech.practice.pay.api.admin.security.CsrfCookieFilter]가 안전한 GET 응답에도
 * `XSRF-TOKEN` 쿠키를 실어 주므로, 프론트는 로그인/등록 POST 전에 이 요청으로 토큰을
 * 먼저 확보한다.
 */
@RestController
@RequestMapping("/admin/me")
class AdminMeController {
	@GetMapping
	fun me(
		@AuthenticationPrincipal principal: InternalUserPrincipal,
	): AdminMeResponse =
		AdminMeResponse(
			internalUserId = principal.internalUserId.value,
			loginId = principal.loginId.value,
			role = principal.role.name,
		)
}
