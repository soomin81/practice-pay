package paytech.practice.pay.api.merchant.web

/**
 * Webhook 설정 응답이다.
 *
 * [signingSecret]은 **매 조회마다 그대로 담긴다** — API Key와 달리 "한 번만
 * 보여주고 버리는" 값이 아니다(이유는 `GetMerchantWebhookSettingsUseCase`의 KDoc).
 * 대신 이 경로 자체가 `OWNER`/`ADMIN` 전용이다.
 *
 * [secretVersion]은 비밀이 아니다 — 교체가 실제로 반영됐는지 화면과 로그에서
 * 확인할 수 있게 함께 내려준다.
 */
data class MerchantWebhookSettingsResponse(
	val webhookUrl: String?,
	val signingSecret: String,
	val secretVersion: Int,
)
