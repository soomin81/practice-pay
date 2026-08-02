package paytech.practice.pay.application.merchant

import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.WebhookSigner
import paytech.practice.pay.domain.merchant.Merchant
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Clock
import java.time.Instant

/**
 * 가맹점 콘솔이 자기 Webhook 설정(수신 URL과 **서명 비밀**)을 조회하는 Use Case다
 * (`GET /merchant/webhook`).
 *
 * **서명 비밀을 매번 보여준다** — API Key가 "최초 발급 응답에서 한 번만" 원문을
 * 돌려주는 것(`docs/architecture/persistence-jooq.md`의 "인증 정보 저장 규칙")과
 * 정반대다. 두 가지 이유가 있다:
 *
 * 1. 비밀은 **저장돼 있지 않고 파생된다**([WebhookSigner]) — "한 번 보여주고 버린다"는
 *    개념 자체가 성립하지 않는다. 언제든 같은 값을 다시 만들 수 있다.
 * 2. 가맹점은 이 값을 **자기 서버 코드에 넣어야** 검증할 수 있다. 잃어버렸을 때
 *    되찾을 방법이 교체뿐이라면, 교체할 때마다 그동안 오던 Webhook이 전부 거부된다.
 *
 * 대신 **누가 볼 수 있는지를 좁힌다** — 이 경로는 `SecurityConfig`에서 OWNER/ADMIN으로
 * 제한되고, VIEWER는 아예 닿지 못한다.
 */
class GetMerchantWebhookSettingsUseCase(
	private val merchantRepository: MerchantRepository,
	private val webhookSigner: WebhookSigner,
	private val clock: Clock,
) {
	fun execute(merchantId: MerchantId): MerchantWebhookSettings {
		val merchant =
			merchantRepository.findById(merchantId)
				?: error("인증된 가맹점($merchantId)을 찾을 수 없습니다.")

		return merchantWebhookSettings(merchant, webhookSigner, clock.instant())
	}
}

/**
 * Webhook 설정 조회 결과다.
 *
 * [webhookUrl]이 `null`이면 가맹점이 Webhook을 쓰지 않는 정상적인 상태다 —
 * `PublishOutboxEventUseCase`가 그 경우 전송을 아예 만들지 않는다.
 *
 * @property previousSecret 겹침 기간 동안만 값이 있다 — **직전 비밀도 아직 통한다**는
 * 뜻이고, 지나면 `null`이다. 가맹점이 "지금 내 서버의 옛 비밀이 아직 유효한가"를 화면에서
 * 확인할 수 있어야 교체를 마음 놓고 진행한다.
 * @property previousSecretValidUntil 직전 비밀이 무효가 되는 시각. [previousSecret]과
 * 함께 있거나 함께 없다.
 */
data class MerchantWebhookSettings(
	val webhookUrl: String?,
	val signingSecret: String,
	val secretVersion: Int,
	val previousSecret: String?,
	val previousSecretValidUntil: Instant?,
)

/**
 * 조회와 교체가 **같은 모양의 답**을 만들도록 한곳에 모은다 — 교체 직후 화면이 그리는
 * 값과, 그 뒤 새로고침해서 받는 값이 달라지면 안 된다.
 */
internal fun merchantWebhookSettings(
	merchant: Merchant,
	webhookSigner: WebhookSigner,
	now: Instant,
): MerchantWebhookSettings {
	val activeVersions = merchant.activeWebhookSecretVersions(now, WebhookSignaturePolicy.SECRET_OVERLAP)
	val previousVersion = activeVersions.getOrNull(1)

	return MerchantWebhookSettings(
		webhookUrl = merchant.webhookUrl?.value,
		signingSecret = webhookSigner.deriveSecret(merchant.id, merchant.webhookSecretVersion),
		secretVersion = merchant.webhookSecretVersion,
		previousSecret = previousVersion?.let { webhookSigner.deriveSecret(merchant.id, it) },
		// 겹침이 없으면 둘 다 null이다 — 유효 기간만 남기면 "무엇이 유효한지"를 알 수 없다.
		previousSecretValidUntil = previousVersion?.let { merchant.webhookSecretRotatedAt?.plus(WebhookSignaturePolicy.SECRET_OVERLAP) },
	)
}
