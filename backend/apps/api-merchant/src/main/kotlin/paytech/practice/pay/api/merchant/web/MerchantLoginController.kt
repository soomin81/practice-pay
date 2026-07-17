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
import paytech.practice.pay.application.identity.AuthenticateMerchantUserCommand
import paytech.practice.pay.application.identity.AuthenticateMerchantUserUseCase
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.merchant.MerchantCode

/**
 * 가맹점 관리자 로그인 API(`docs/architecture/identity-access-api-key.md`의
 * "4.5 로그인 경로", 권장 경로 `/merchant/login`)를 노출하는 inbound Adapter다
 * (`apps:api-admin`의 `AdminLoginController`와 같은 이유·같은 모양).
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

		val authorities = listOf(SimpleGrantedAuthority("ROLE_${result.role.name}"))
		val authentication = UsernamePasswordAuthenticationToken(result.loginId.value, null, authorities)
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
