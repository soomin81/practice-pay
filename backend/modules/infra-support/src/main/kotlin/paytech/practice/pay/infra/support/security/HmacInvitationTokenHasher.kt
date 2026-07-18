package paytech.practice.pay.infra.support.security

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
 * 쓴다 — 한쪽 비밀값이 새도 다른 쪽까지 위험해지지 않게 하려는 의도적 분리다
 * ([InvitationTokenHasher]의 KDoc 참고).
 *
 * 지금 값은 로컬 개발용 평문 placeholder다 — 실제 배포 전 환경변수/Secret
 * Manager로 옮겨야 한다.
 *
 * 해시 비교는 `String.equals` 대신 [MessageDigest.isEqual]로 한다 — 타이밍
 * 공격을 막기 위한 상수 시간 비교다.
 *
 * **이 클래스가 `security` 하위 패키지에 있는 이유**: `@Value`로 필수 설정값을
 * 요구하기 때문에, 초대 흐름이 없는 앱(`api-payment`/`batch`)이 이 Bean까지
 * 스캔하면 `app.invitation-token.pepper`가 없다며 컨텍스트가 뜨지 않는다. 그래서
 * `infra.support`를 통째로 스캔하지 않고 필요한 앱(`api-admin`/`api-merchant`)만
 * 이 하위 패키지를 스캔한다.
 *
 * 원래 `apps:api-admin`/`api-merchant` 두 곳에 똑같이 복제돼 있던 구현이다.
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
