package paytech.practice.pay.api.payment.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import paytech.practice.pay.api.payment.web.ErrorResponse
import tools.jackson.databind.ObjectMapper

/**
 * 인증되지 않은 요청(API Key가 없거나 유효하지 않음)에 대한 401 응답을
 * `PaymentApiExceptionHandler`가 쓰는 것과 같은 [ErrorResponse] JSON 형식으로
 * 통일한다 — 이게 없으면 Spring Security 기본 엔트리 포인트가 다른 형식의
 * 응답을 준다.
 */
class ApiKeyAuthenticationEntryPoint(
	private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint {
	override fun commence(
		request: HttpServletRequest,
		response: HttpServletResponse,
		authException: AuthenticationException,
	) {
		response.status = HttpServletResponse.SC_UNAUTHORIZED
		response.contentType = MediaType.APPLICATION_JSON_VALUE
		response.characterEncoding = "UTF-8"
		response.writer.write(objectMapper.writeValueAsString(ErrorResponse("API Key가 유효하지 않습니다.")))
	}
}
