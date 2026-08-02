package paytech.practice.pay.api.merchant.web

import jakarta.validation.constraints.Size

/**
 * Webhook 수신 URL 설정 요청이다.
 *
 * [webhookUrl]은 **의도적으로 nullable**이다 — `null`(또는 빈 문자열)이 "해제"를
 * 뜻한다. 해제 전용 엔드포인트를 따로 두지 않은 것은 도메인의
 * `Merchant.updateWebhookUrl`이 이미 `null`로 해제를 표현하기 때문이다.
 *
 * 형식 검증(`http`/`https` 스킴)은 `HttpUrl` 값 객체가 하고, 위반은
 * `IllegalArgumentException` → `400`으로 매핑된다. 여기서 길이를 한 번 더 막는
 * 것은 `merchant.webhook_url`(`VARCHAR(1000)`)과 같은 한계를 **요청 검증 단계에서**
 * 알려주기 위해서다.
 */
data class UpdateMerchantWebhookUrlRequest(
	@field:Size(max = 1000)
	val webhookUrl: String?,
)
