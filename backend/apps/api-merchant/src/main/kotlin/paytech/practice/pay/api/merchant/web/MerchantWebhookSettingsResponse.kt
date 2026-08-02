package paytech.practice.pay.api.merchant.web

import java.time.Instant

/**
 * Webhook 설정 응답이다.
 *
 * [signingSecret]은 **매 조회마다 그대로 담긴다** — API Key와 달리 "한 번만
 * 보여주고 버리는" 값이 아니다(이유는 `GetMerchantWebhookSettingsUseCase`의 KDoc).
 * 대신 이 경로 자체가 `OWNER`/`ADMIN` 전용이다.
 *
 * [secretVersion]은 비밀이 아니다 — 교체가 실제로 반영됐는지 화면과 로그에서
 * 확인할 수 있게 함께 내려준다.
 *
 * @property previousSecret 겹침 기간 동안만 값이 있다 — **직전 비밀도 아직 통한다**는
 * 뜻이다. 지나면 `null`이고, 그때부터는 새 비밀만 유효하다.
 * @property previousSecretValidUntil 직전 비밀이 무효가 되는 시각(UTC).
 * [previousSecret]과 함께 있거나 함께 없다.
 */
data class MerchantWebhookSettingsResponse(
	val webhookUrl: String?,
	val signingSecret: String,
	val secretVersion: Int,
	val previousSecret: String?,
	val previousSecretValidUntil: Instant?,
)
