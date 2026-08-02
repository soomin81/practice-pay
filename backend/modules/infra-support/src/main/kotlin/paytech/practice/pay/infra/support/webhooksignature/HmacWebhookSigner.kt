package paytech.practice.pay.infra.support.webhooksignature

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import paytech.practice.pay.application.port.outbound.WebhookSigner
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Instant
import java.util.Base64
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * [WebhookSigner] Port를 HMAC-SHA-256으로 구현한다 — `HmacInvitationTokenHasher`/
 * `HmacApiKeySecretHasher`와 같은 방식이지만, **쓰임이 반대**라는 점이 다르다.
 * 저 둘은 받은 값을 검증하려고 해시하고, 이쪽은 **우리가 보내는 값에 서명한다**
 * (그래서 원문을 되찾을 수 있어야 하고, 저장 대신 파생을 쓴다 — 근거는
 * [WebhookSigner]의 KDoc).
 *
 * [pepper]는 `app.webhook-signature.pepper`로 다른 두 Pepper와 분리한다 — 한쪽이
 * 새도 나머지가 함께 무너지지 않게 하려는 같은 의도다.
 *
 * **`api-merchant`와 `batch`가 반드시 같은 Pepper를 써야 한다.** 서명은 `batch`가
 * 만들고, 가맹점에게 비밀을 보여주는 것은 `api-merchant`다 — 두 값이 어긋나면
 * 콘솔이 알려준 비밀로는 어떤 서명도 검증되지 않는다. 그때 나타나는 증상은
 * "가맹점 쪽 구현이 틀렸다"처럼 보여서 추적이 매우 어렵다(`app.api-key.pepper`가
 * `api-payment`/`api-merchant` 사이에서 갖는 제약과 같은 성격이다 —
 * `backend/CLAUDE.md`의 "설정과 비밀값" 참고).
 *
 * **Pepper를 바꾸면 모든 가맹점의 서명 비밀이 한꺼번에 바뀐다** — 세대를 올리지
 * 않았는데도 파생 결과가 달라지므로, 가맹점들은 영문도 모른 채 검증에 실패하기
 * 시작한다. 교체가 필요하면 개별 가맹점의 `webhookSecretVersion`을 올리는 쪽을
 * 쓴다.
 *
 * **자체 하위 패키지에 있는 이유**: `@Value`로 필수 설정값을 요구해서, 이 Bean을
 * 쓰지 않는 앱이 스캔하면 컨텍스트가 뜨지 않는다. 같은 이유로 `infra.support.webhook`
 * (전송, 설정값 없음)과도 분리했다 — `api-merchant`는 서명 비밀을 보여주기만 할 뿐
 * Webhook을 **보내지 않으므로** `HttpWebhookSender`까지 스캔할 이유가 없다.
 */
@Component
class HmacWebhookSigner(
	@Value("\${app.webhook-signature.pepper}") private val pepper: String,
) : WebhookSigner {
	override fun deriveSecret(
		merchantId: MerchantId,
		secretVersion: Int,
	): String {
		require(secretVersion >= 1) { "secretVersion은 1 이상이어야 합니다: $secretVersion" }
		val derived = hmac(pepper.toByteArray(Charsets.UTF_8), "${merchantId.value}:$secretVersion")
		return SECRET_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(derived)
	}

	override fun signatureHeaderValue(
		merchantId: MerchantId,
		secretVersions: List<Int>,
		payload: String,
		signedAt: Instant,
	): String {
		require(secretVersions.isNotEmpty()) { "서명할 비밀 세대가 최소 하나는 있어야 합니다." }

		val timestamp = signedAt.epochSecond
		// 본문만이 아니라 "{t}.{본문}"에 서명한다 — 재전송 공격을 가맹점이
		// 판단할 수 있게 하려는 것이다([WebhookSigner]의 KDoc 참고).
		val signedPayload = "$timestamp.$payload"
		val signatures =
			secretVersions.joinToString(",") { version ->
				val secret = deriveSecret(merchantId, version)
				val signature = hmac(secret.toByteArray(Charsets.UTF_8), signedPayload)
				"$SIGNATURE_SCHEME=${HexFormat.of().formatHex(signature)}"
			}
		// 겹침 기간에는 v1이 두 개 실린다 — 가맹점은 하나라도 맞으면 받아들인다.
		return "t=$timestamp,$signatures"
	}

	private fun hmac(
		key: ByteArray,
		message: String,
	): ByteArray {
		val mac = Mac.getInstance(ALGORITHM)
		mac.init(SecretKeySpec(key, ALGORITHM))
		return mac.doFinal(message.toByteArray(Charsets.UTF_8))
	}

	companion object {
		private const val ALGORITHM = "HmacSHA256"

		/** 가맹점이 다른 비밀값과 눈으로 구분할 수 있게 하는 접두사다(API Key의 `sk_test_`와 같은 의도). */
		private const val SECRET_PREFIX = "whsec_"

		/** 서명 형식 버전. 알고리즘을 바꿔야 할 때 `v2`를 함께 실어 보내기 위한 자리다. */
		private const val SIGNATURE_SCHEME = "v1"
	}
}
