package paytech.practice.pay.application.merchant

import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.WebhookSigner
import paytech.practice.pay.domain.merchant.MerchantId

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
) {
	fun execute(merchantId: MerchantId): MerchantWebhookSettings {
		val merchant =
			merchantRepository.findById(merchantId)
				?: error("인증된 가맹점($merchantId)을 찾을 수 없습니다.")

		return MerchantWebhookSettings(
			webhookUrl = merchant.webhookUrl?.value,
			signingSecret = webhookSigner.deriveSecret(merchant.id, merchant.webhookSecretVersion),
			secretVersion = merchant.webhookSecretVersion,
		)
	}
}

/**
 * Webhook 설정 조회 결과다.
 *
 * [webhookUrl]이 `null`이면 가맹점이 Webhook을 쓰지 않는 정상적인 상태다 —
 * `PublishOutboxEventUseCase`가 그 경우 전송을 아예 만들지 않는다.
 */
data class MerchantWebhookSettings(
	val webhookUrl: String?,
	val signingSecret: String,
	val secretVersion: Int,
)
