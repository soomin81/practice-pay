package paytech.practice.pay.application.merchant

import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.shared.HttpUrl
import java.time.Clock

/**
 * 가맹점이 자기 Webhook 수신 URL을 설정하거나 해제하는 Use Case다
 * (`PUT /merchant/webhook`).
 *
 * `Merchant.updateWebhookUrl`은 도메인에 있었지만 **호출부가 테스트밖에 없었다** —
 * 가맹점이 URL을 바꿀 방법이 콘솔에 없어서 DB를 직접 고치는 수밖에 없었다.
 * 이 Use Case가 그 구멍을 닫는다.
 *
 * URL 형식 검증은 [HttpUrl] 값 객체가 한다 — 여기서 다시 확인하지 않는다.
 */
class UpdateMerchantWebhookUrlUseCase(
	private val merchantRepository: MerchantRepository,
	private val clock: Clock,
) {
	/** [webhookUrl]이 `null`이면 설정을 해제한다(그 뒤로는 전송 자체를 만들지 않는다). */
	fun execute(
		merchantId: MerchantId,
		webhookUrl: String?,
	) {
		val merchant =
			merchantRepository.findById(merchantId)
				?: error("인증된 가맹점($merchantId)을 찾을 수 없습니다.")

		merchant.updateWebhookUrl(webhookUrl?.let { HttpUrl(it) }, clock.instant())
		merchantRepository.save(merchant)
	}
}
