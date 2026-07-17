package paytech.practice.pay.api.admin.support

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import paytech.practice.pay.application.port.outbound.PasswordEncoder

/** [PasswordEncoder] Port를 Spring Security의 `BCryptPasswordEncoder`로 구현한다. */
@Component
class BCryptPasswordEncoderAdapter : PasswordEncoder {
	private val delegate = BCryptPasswordEncoder()

	override fun encode(rawPassword: String): String = requireNotNull(delegate.encode(rawPassword))

	override fun matches(
		rawPassword: String,
		encodedPassword: String,
	): Boolean = delegate.matches(rawPassword, encodedPassword)
}
