package paytech.practice.pay.api.admin.support

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import paytech.practice.pay.application.port.outbound.InvitationTokenHasher
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * [InvitationTokenHasher] Port를 HMAC-SHA-256으로 구현한다 —
 * `apps:api-payment`의 `HmacApiKeySecretHasher`와 같은 방식이다. [pepper]는
 * API Key Pepper(`app.api-key.pepper`)와 별개의 설정값(`app.invitation-token.pepper`)을
 * 쓴다 — [InvitationTokenHasher]의 KDoc 참고.
 *
 * 지금 값은 로컬 개발용 평문 placeholder다 — 실제 배포 전 환경변수/Secret
 * Manager로 옮겨야 한다(`app.api-key.pepper`와 같은 성격).
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
