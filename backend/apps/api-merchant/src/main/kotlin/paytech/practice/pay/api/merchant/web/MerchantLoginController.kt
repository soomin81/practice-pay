package paytech.practice.pay.api.merchant.web

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.context.SecurityContextRepository
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.api.merchant.security.MerchantUserPrincipal
import paytech.practice.pay.application.identity.AuthenticateMerchantUserCommand
import paytech.practice.pay.application.identity.AuthenticateMerchantUserUseCase
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.merchant.MerchantCode

/**
 * 가맹점 관리자 로그인 API(`docs/architecture/identity-access-api-key.md`의
 * "4.5 로그인 경로", 권장 경로 `/merchant/login`)를 노출하는 inbound Adapter다
 * (`apps:api-admin`의 `AdminLoginController`와 같은 이유·같은 모양).
 *
 * `Authentication.principal`에는 [MerchantUserPrincipal]을 심는다 — 원래는
 * `result.loginId.value`(문자열)만 심었지만, 하위 계정 발급
 * (`InviteMerchantSubAccountUseCase`)이 감사 정보(`invitedByMerchantUserId`)와
 * 발급 대상 가맹점(`merchantId`)을 세션에서 바로 가져와야 해서 확장했다
 * (`AdminLoginController`가 `InternalUserPrincipal`을 도입했던 것과 같은 이유·같은
 * 시점의 변화다).
 */
@RestController
@RequestMapping("/merchant/login")
class MerchantLoginController(
	private val authenticateMerchantUserUseCase: AuthenticateMerchantUserUseCase,
	private val securityContextRepository: SecurityContextRepository,
) {
	@PostMapping
	fun login(
		@Valid @RequestBody request: MerchantLoginRequest,
		httpRequest: HttpServletRequest,
		httpResponse: HttpServletResponse,
	): MerchantLoginResponse {
		val result =
			authenticateMerchantUserUseCase.execute(
				AuthenticateMerchantUserCommand(
					merchantCode = MerchantCode(request.merchantCode),
					loginId = LoginId(request.loginId),
					password = request.password,
				),
			)

		val principal = MerchantUserPrincipal(result.merchantUserId, result.merchantId, result.loginId, result.role)
		val authorities = listOf(SimpleGrantedAuthority("ROLE_${result.role.name}"))
		val authentication = UsernamePasswordAuthenticationToken(principal, null, authorities)
		val context = SecurityContextHolder.createEmptyContext()
		context.authentication = authentication
		SecurityContextHolder.setContext(context)
		securityContextRepository.saveContext(context, httpRequest, httpResponse)

		return MerchantLoginResponse(
			merchantUserId = result.merchantUserId.value,
			merchantId = result.merchantId.value,
			loginId = result.loginId.value,
			userName = result.userName,
			role = result.role.name,
		)
	}
}
