package paytech.practice.pay.infra.support.security

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import paytech.practice.pay.application.port.outbound.PasswordEncoder

/**
 * [PasswordEncoder] Port를 Spring Security의 `BCryptPasswordEncoder`로 구현한다.
 *
 * 사람이 입력하는 비밀번호라 느린 적응형 해시(BCrypt)를 쓴다 — 매 요청 검증하는
 * API Key가 HMAC(`HmacApiKeySecretHasher`)을 쓰는 것과 의도적으로 다르다.
 *
 * 원래 `apps:api-admin`/`api-merchant` 두 곳에 똑같이 복제돼 있던 구현이다.
 */
@Component
class BCryptPasswordEncoderAdapter : PasswordEncoder {
	private val delegate = BCryptPasswordEncoder()

	override fun encode(rawPassword: String): String = requireNotNull(delegate.encode(rawPassword))

	override fun matches(
		rawPassword: String,
		encodedPassword: String,
	): Boolean = delegate.matches(rawPassword, encodedPassword)
}
