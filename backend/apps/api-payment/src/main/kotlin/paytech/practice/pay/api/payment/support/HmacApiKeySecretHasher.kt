package paytech.practice.pay.api.payment.support

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import paytech.practice.pay.application.port.outbound.ApiKeySecretHasher
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * [ApiKeySecretHasher] Port를 HMAC-SHA-256으로 구현한다
 * (`docs/architecture/identity-access-api-key.md`의 "6.4 저장 정책"이 권장하는
 * "HMAC-SHA-256 계열 + 서버 측 비밀값(Pepper)" 그대로다). BCrypt 같은 느린 적응형
 * 해시를 쓰지 않는 이유는 [paytech.practice.pay.application.port.outbound.ApiKeySecretHasher]의
 * KDoc 참고 — API Key는 요청마다 검증해서 그럴 필요가 없다.
 *
 * [pepper]는 `application.yaml`의 `app.api-key.pepper`에서 온다 — 지금 값은
 * 로컬 개발용 평문 placeholder다(이 프로젝트가 `db-core`의 `verysecret` DB
 * 비밀번호처럼 로컬 개발 편의를 위해 실제 배포 전 secret은 환경변수/Secret
 * Manager로 옮겨야 한다고 이미 전제하고 있는 것과 같은 성격 — 소스에 실제
 * 운영 값을 넣지 않는다).
 *
 * 해시 비교는 `String.equals` 대신 [MessageDigest.isEqual]로 한다 — 타이밍
 * 공격을 막기 위한 상수 시간 비교다.
 */
@Component
class HmacApiKeySecretHasher(
	@Value("\${app.api-key.pepper}") private val pepper: String,
) : ApiKeySecretHasher {
	override fun hash(rawApiKey: String): String {
		val mac = Mac.getInstance(ALGORITHM)
		mac.init(SecretKeySpec(pepper.toByteArray(Charsets.UTF_8), ALGORITHM))
		return Base64.getEncoder().encodeToString(mac.doFinal(rawApiKey.toByteArray(Charsets.UTF_8)))
	}

	override fun matches(
		rawApiKey: String,
		hash: String,
	): Boolean = MessageDigest.isEqual(hash(rawApiKey).toByteArray(Charsets.UTF_8), hash.toByteArray(Charsets.UTF_8))

	companion object {
		private const val ALGORITHM = "HmacSHA256"
	}
}
