package paytech.practice.pay.api.merchant.support

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import paytech.practice.pay.application.port.outbound.InvitationTokenHasher
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * [InvitationTokenHasher] Port를 HMAC-SHA-256으로 구현한다 — `apps:api-admin`의
 * `HmacInvitationTokenHasher`와 완전히 같은 구현이다. `IdGenerator`→`UuidIdGenerator`/
 * `PasswordEncoder`→`BCryptPasswordEncoderAdapter`가 이미 앱마다 자기 `support`
 * 패키지에 복제돼 있는 것과 같은 기존 관례를 따른다.
 *
 * 지금 값은 로컬 개발용 평문 placeholder다 — 실제 배포 전 환경변수/Secret
 * Manager로 옮겨야 한다.
 *
 * 해시 비교는 `String.equals` 대신 [MessageDigest.isEqual]로 한다 — 타이밍
 * 공격을 막기 위한 상수 시간 비교다.
 */
@Component
class HmacInvitationTokenHasher(
	@Value("\${app.invitation-token.pepper}") private val pepper: String,
) : InvitationTokenHasher {
	override fun hash(rawToken: String): String {
		val mac = Mac.getInstance(ALGORITHM)
		mac.init(SecretKeySpec(pepper.toByteArray(Charsets.UTF_8), ALGORITHM))
		return Base64.getEncoder().encodeToString(mac.doFinal(rawToken.toByteArray(Charsets.UTF_8)))
	}

	override fun matches(
		rawToken: String,
		hash: String,
	): Boolean = MessageDigest.isEqual(hash(rawToken).toByteArray(Charsets.UTF_8), hash.toByteArray(Charsets.UTF_8))

	companion object {
		private const val ALGORITHM = "HmacSHA256"
	}
}
