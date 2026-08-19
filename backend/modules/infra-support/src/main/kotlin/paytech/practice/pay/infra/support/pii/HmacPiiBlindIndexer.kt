package paytech.practice.pay.infra.support.pii

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import paytech.practice.pay.application.port.outbound.PiiBlindIndexer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

/**
 * [PiiBlindIndexer] Port를 HMAC-SHA-256으로 구현한다 — `HmacInvitationTokenHasher`와 같은
 * 방식이되 **결과를 hex로 낸다**(`payment_customer.*_index`가 `CHAR(64)`다).
 *
 * **암호화 키와 별개의 비밀값을 쓴다**(`app.pii.blind-index-pepper`). 한쪽이 새도 다른 쪽까지
 * 위험해지지 않게 하려는 분리이고, 초대 토큰 Pepper와 API Key Pepper를 나눈 것과 같은 판단이다.
 * 특히 여기서는 성질이 다르다 — **암호화 키가 새면 원문이 드러나지만, 이 Pepper가 새면
 * "같은 값인지"를 대조할 수 있게 된다**(사전 공격으로 이메일 후보를 넣어 볼 수 있다).
 *
 * 인덱스는 **비교에만 쓰이고 되돌릴 필요가 없으므로** 단방향 HMAC이면 충분하다.
 */
@Component
class HmacPiiBlindIndexer(
	@Value("\${app.pii.blind-index-pepper}") private val pepper: String,
) : PiiBlindIndexer {
	init {
		// 암호화 키와 같은 이유로 기동 시점에 알린다(`AesGcmPiiEncryptor` 참고). 이쪽이
		// 새면 원문이 드러나지는 않지만 **"같은 값인지"를 대조할 수 있게 된다** — 이메일
		// 후보를 넣어 보는 사전 공격이 성립한다.
		if (pepper.contains(LOCAL_DEV_SECRET_MARKER)) {
			logger.warn {
				"app.pii.blind-index-pepper가 저장소에 적힌 개발용 기본값입니다 — 운영이라면 " +
					"APP_PII_BLIND_INDEX_PEPPER로 반드시 교체하세요(교체하면 기존 인덱스로는 검색되지 않습니다)."
			}
		}
	}

	override fun index(normalizedValue: String): String {
		val mac =
			Mac.getInstance(ALGORITHM).apply {
				init(SecretKeySpec(pepper.toByteArray(Charsets.UTF_8), ALGORITHM))
			}
		return mac.doFinal(normalizedValue.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
	}

	companion object {
		private const val ALGORITHM = "HmacSHA256"
	}
}
