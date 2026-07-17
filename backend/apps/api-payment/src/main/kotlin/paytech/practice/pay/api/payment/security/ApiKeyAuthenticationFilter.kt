package paytech.practice.pay.api.payment.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import paytech.practice.pay.application.apikey.AuthenticateApiKeyCommand
import paytech.practice.pay.application.apikey.AuthenticateApiKeyUseCase
import paytech.practice.pay.application.apikey.InvalidApiKeyException

private const val BEARER_PREFIX = "Bearer "

/**
 * `Authorization: Bearer <API Key>` 헤더를 읽어 `AuthenticateApiKeyUseCase`로
 * 인증하고, 성공하면 이번 요청의 `SecurityContext`에 [ApiKeyPrincipal]을 심는다.
 *
 * 세션을 만들지 않는다(`SecurityConfig`가 `SessionCreationPolicy.STATELESS`로
 * 설정돼 있다) — API Key는 요청마다 다시 검증하는 서버 간 자격증명이라
 * `apps:api-admin`/`apps:api-merchant` 로그인의 세션 쿠키 방식과 다르다.
 *
 * 헤더가 없거나 인증에 실패해도 이 필터는 예외를 던지지 않는다 — `SecurityContext`를
 * 비워둔 채 다음 필터로 넘기고, 그 뒤 `authorizeHttpRequests`의 인가 규칙이
 * 401/403을 결정한다([ApiKeyAuthenticationEntryPoint]가 응답 형식을 통일한다).
 */
class ApiKeyAuthenticationFilter(
	private val authenticateApiKeyUseCase: AuthenticateApiKeyUseCase,
) : OncePerRequestFilter() {
	override fun doFilterInternal(
		request: HttpServletRequest,
		response: HttpServletResponse,
		filterChain: FilterChain,
	) {
		val header = request.getHeader("Authorization")
		if (header != null && header.startsWith(BEARER_PREFIX)) {
			val rawApiKey = header.removePrefix(BEARER_PREFIX)
			try {
				val result = authenticateApiKeyUseCase.execute(AuthenticateApiKeyCommand(rawApiKey))
				val authorities = result.scopes.map { SimpleGrantedAuthority("SCOPE_${it.name}") }
				val principal = ApiKeyPrincipal(result.merchantId, result.merchantApiKeyId)
				SecurityContextHolder.getContext().authentication =
					UsernamePasswordAuthenticationToken(principal, null, authorities)
			} catch (ex: InvalidApiKeyException) {
				SecurityContextHolder.clearContext()
			}
		}
		filterChain.doFilter(request, response)
	}
}
