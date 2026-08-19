package paytech.practice.pay.infra.support.pii

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import paytech.practice.pay.application.port.outbound.PiiEncryptor
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

/**
 * 개발용 기본값을 알아보는 표식이다 — 기본값 문자열 전체를 코드에 복제하면
 * `application.yaml`과 따로 놀다가 조용히 어긋난다. 두 앱의 기본값이 모두 이 표식을
 * 담고 있다(`local-dev-only-32byte-aes-key!!!`, `local-dev-only-blind-index-pepper`).
 */
internal const val LOCAL_DEV_SECRET_MARKER = "local-dev-only"

/**
 * 저장소에 적힌 **개발용 기본 키를 그대로 들고 뜬 경우**를 기동 시점에 알린다.
 *
 * 이 값은 저장소를 볼 수 있는 누구나 아는 키다 — 운영에서 그대로 쓰이면 DB가 새는 순간
 * 구매자 정보가 그냥 평문이다. 그런데 **아무 증상이 없어서** 알아차릴 계기가 없다.
 *
 * **기동을 막지는 않는다.** 막으면 환경변수 없이 `bootRun`이 도는 로컬 개발 규칙이 깨진다
 * (`backend/CLAUDE.md`) — 운영 배포에서 이 WARN을 놓치지 않는 것이 방어선이다.
 */
private fun warnIfLocalDevDefault(keyBytes: ByteArray) {
	if (String(keyBytes, Charsets.UTF_8).contains(LOCAL_DEV_SECRET_MARKER)) {
		logger.warn {
			"app.pii.encryption-key가 저장소에 적힌 개발용 기본값입니다 — 운영이라면 " +
				"APP_PII_ENCRYPTION_KEY로 반드시 교체하세요(교체하면 기존 데이터는 읽을 수 없습니다)."
		}
	}
}

/**
 * [PiiEncryptor] Port를 **AES-256-GCM**으로 구현한다 — 이 저장소의 첫 양방향 암호화다
 * (다른 비밀 처리는 전부 단방향 HMAC/BCrypt다).
 *
 * ## 왜 GCM인가
 *
 * **인증 암호화라 암호문이 변조되면 복호화가 실패한다.** CBC/CTR은 변조된 암호문에 대해
 * 조용히 다른 평문을 내놓을 수 있는데, 그 평문이 이메일 주소라면 **엉뚱한 사람에게 연락하게
 * 된다** — 개인정보에서는 그게 최악의 실패다.
 *
 * ## 값마다 새 IV
 *
 * 암호화할 때마다 [SecureRandom]으로 12바이트 IV를 만들어 **암호문 앞에 붙여** Base64로
 * 담는다. 같은 이메일이라도 행마다 결과가 달라서 **DB만 유출되면 동일인 여부조차 드러나지
 * 않는다**(ADR-008).
 *
 * **IV를 재사용하면 GCM은 치명적으로 깨진다** — 같은 키·같은 IV로 두 번 암호화하면 평문을
 * 복구할 수 있다. 그래서 IV를 고정값이나 파생값으로 두지 않는다.
 *
 * ## 키
 *
 * `app.pii.encryption-key`(환경변수 `APP_PII_ENCRYPTION_KEY`)에서 온다 — 기존 Pepper들과 같은
 * 방식이다. **Base64로 인코딩된 32바이트**여야 하고, 아니면 기동 시점에 실패한다(잘못된 키로
 * 암호화가 시작되면 그 데이터는 되살릴 수 없어서, 늦게 실패하느니 아예 뜨지 않는 편이 낫다).
 *
 * **키를 잃거나 바꾸면 기존 데이터를 읽을 수 없다.** Pepper 교체가 해시를 무효화하는 것과
 * 같은 성격이지만 결과가 더 무겁다 — 해시는 재발급하면 되지만 이쪽은 **원본이 사라진다.**
 * 재암호화 절차는 아직 없다(ADR-008의 "남긴 것").
 *
 * **이 클래스가 `pii` 하위 패키지에 있는 이유**는 `@Value`로 필수 설정값을 요구해서다 —
 * 개인정보를 다루지 않는 앱이 이 Bean까지 스캔하면 그 설정이 없다며 컨텍스트가 뜨지 않는다
 * (`HmacInvitationTokenHasher`와 같은 이유·같은 구조).
 */
@Component
class AesGcmPiiEncryptor(
	@Value("\${app.pii.encryption-key}") encodedKey: String,
) : PiiEncryptor {
	private val key =
		SecretKeySpec(
			Base64.getDecoder().decode(encodedKey).also {
				require(it.size == KEY_BYTES) {
					"app.pii.encryption-key는 Base64로 인코딩된 ${KEY_BYTES}바이트여야 합니다(현재 ${it.size}바이트)."
				}
				warnIfLocalDevDefault(it)
			},
			"AES",
		)

	private val random = SecureRandom()

	override fun encrypt(plaintext: String): String {
		val iv = ByteArray(IV_BYTES).also(random::nextBytes)
		val cipher =
			Cipher.getInstance(TRANSFORMATION).apply {
				init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
			}
		// IV는 비밀이 아니라 유일하기만 하면 된다 — 복호화에 필요하므로 암호문 앞에 붙인다.
		return Base64.getEncoder().encodeToString(iv + cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)))
	}

	override fun decrypt(ciphertext: String): String {
		val decoded = Base64.getDecoder().decode(ciphertext)
		require(decoded.size > IV_BYTES) { "암호문이 손상되었습니다(IV를 담기에도 짧습니다)." }
		val cipher =
			Cipher.getInstance(TRANSFORMATION).apply {
				init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, decoded, 0, IV_BYTES))
			}
		return try {
			String(cipher.doFinal(decoded, IV_BYTES, decoded.size - IV_BYTES), Charsets.UTF_8)
		} catch (ex: GeneralSecurityException) {
			// 이 실패는 **거의 언제나 설정 문제**다 — api-payment가 쓴 것과 다른
			// app.pii.encryption-key로 api-admin이 읽으려 한 경우다. 예외만 올라가면
			// "인증 태그 불일치"라는 암호 라이브러리 메시지만 남아 원인이 드러나지 않는다.
			//
			// **암호문도 평문도 찍지 않는다.** 암호문을 로그에 남기면 나중에 키가 새는
			// 순간 그 로그가 곧 평문이 된다 — 로그는 파기 대상 밖에 있어서 더 오래 남는다.
			logger.warn(ex) {
				"구매자 개인정보 복호화에 실패했습니다 — app.pii.encryption-key가 암호화할 때와 " +
					"다르거나(api-payment/api-admin 불일치) 암호문이 손상됐습니다."
			}
			throw ex
		}
	}

	companion object {
		private const val TRANSFORMATION = "AES/GCM/NoPadding"

		/** AES-256. */
		private const val KEY_BYTES = 32

		/** GCM 권장 IV 길이. 12바이트가 아니면 내부적으로 해싱을 거쳐 성능과 안전성이 모두 나빠진다. */
		private const val IV_BYTES = 12

		/** 인증 태그 길이(비트). 짧게 잡으면 변조 탐지가 약해진다 — 최대값을 쓴다. */
		private const val TAG_BITS = 128
	}
}
