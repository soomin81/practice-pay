package paytech.practice.pay.api.merchant.web

import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.api.merchant.security.MerchantUserPrincipal

/**
 * 현재 세션의 로그인 사용자를 돌려주는 API(`GET /merchant/me`)다. 프론트엔드는
 * 로그인 응답을 쿠키 말고는 저장하지 않으므로, 새로고침 후 "나는 로그인돼 있는가,
 * 누구인가"를 이 엔드포인트로 복원한다(`docs/architecture/merchant-console-api.md` 참고).
 *
 * 미인증이면 `SecurityConfig`의 `anyRequest, authenticated` 규칙이 401로 막는다 —
 * 프론트는 그 401을 "로그아웃 상태"로 해석해 로그인 화면을 그린다.
 *
 * **이 GET은 CSRF 토큰 발급도 겸한다.** [paytech.practice.pay.api.merchant.security.CsrfCookieFilter]가
 * 안전한 GET 응답에도 `XSRF-TOKEN` 쿠키를 실어 주므로, 프론트는 로그인/발급 POST 전에
 * 이 요청으로 토큰을 먼저 확보한다.
 *
 * **단순화:** 응답 필드는 [MerchantUserPrincipal]이 지금 들고 있는 값(`merchantUserId`/
 * `merchantId`/`loginId`/`role`)으로 한정한다 — `userName`/`merchantCode`는 principal에
 * 없어서 뺐다. 콘솔이 그 값을 실제로 필요로 하면 로그인 시 principal을 확장한다.
 */
@RestController
@RequestMapping("/merchant/me")
class MerchantMeController {
	@GetMapping
	fun me(
		@AuthenticationPrincipal principal: MerchantUserPrincipal,
	): MerchantMeResponse =
		MerchantMeResponse(
			merchantUserId = principal.merchantUserId.value,
			merchantId = principal.merchantId.value,
			loginId = principal.loginId.value,
			role = principal.role.name,
		)
}
