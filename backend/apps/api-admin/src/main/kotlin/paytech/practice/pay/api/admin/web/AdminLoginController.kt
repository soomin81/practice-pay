package paytech.practice.pay.api.admin.web

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
import paytech.practice.pay.api.admin.security.InternalUserPrincipal
import paytech.practice.pay.application.identity.AuthenticateInternalUserCommand
import paytech.practice.pay.application.identity.AuthenticateInternalUserUseCase
import paytech.practice.pay.domain.identity.LoginId

/**
 * 내부 운영자 로그인 API(`docs/architecture/identity-access-api-key.md`의
 * "3.4 로그인 경로", 권장 경로 `/admin/login`)를 노출하는 inbound Adapter다.
 *
 * `AuthenticateInternalUserUseCase`는 세션을 다루지 않는다(순수 자격증명 검증만) —
 * 인증된 신원을 Spring Security의 `SecurityContext`에 담아 세션에 저장하는 건
 * 이 컨트롤러의 책임이다(`SecurityConfig`가 `/admin/login`만 인증 없이 열어 두고
 * 나머지는 이 세션으로 인증하도록 되어 있다).
 */
@RestController
@RequestMapping("/admin/login")
class AdminLoginController(
	private val authenticateInternalUserUseCase: AuthenticateInternalUserUseCase,
	private val securityContextRepository: SecurityContextRepository,
) {
	@PostMapping
	fun login(
		@Valid @RequestBody request: AdminLoginRequest,
		httpRequest: HttpServletRequest,
		httpResponse: HttpServletResponse,
	): AdminLoginResponse {
		val result =
			authenticateInternalUserUseCase.execute(
				AuthenticateInternalUserCommand(
					loginId = LoginId(request.loginId),
					password = request.password,
				),
			)

		val authorities = listOf(SimpleGrantedAuthority("ROLE_${result.role.name}"))
		val principal = InternalUserPrincipal(result.internalUserId, result.loginId, result.role)
		val authentication = UsernamePasswordAuthenticationToken(principal, null, authorities)
		val context = SecurityContextHolder.createEmptyContext()
		context.authentication = authentication
		SecurityContextHolder.setContext(context)
		securityContextRepository.saveContext(context, httpRequest, httpResponse)

		return AdminLoginResponse(
			internalUserId = result.internalUserId.value,
			loginId = result.loginId.value,
			userName = result.userName,
			role = result.role.name,
		)
	}
}
