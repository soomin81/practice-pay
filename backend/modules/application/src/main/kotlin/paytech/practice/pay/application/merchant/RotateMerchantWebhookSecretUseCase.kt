package paytech.practice.pay.application.merchant

import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.WebhookSigner
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Clock

/**
 * Webhook 서명 비밀을 교체하는 Use Case다 (`POST /merchant/webhook/rotate-secret`).
 * 비밀이 노출됐을 때 되돌릴 수 있는 유일한 수단이다.
 *
 * **직전 비밀이 곧바로 죽지는 않는다.** 겹침 기간([WebhookSignaturePolicy.SECRET_OVERLAP])
 * 동안은 새 비밀과 직전 비밀로 각각 서명해 둘 다 실어 보내고, 가맹점은 하나라도 맞으면
 * 받아들인다 — 그러지 않으면 새 비밀을 자기 서버에 반영하기 전까지의 Webhook을 통째로
 * 놓치는데, 그건 교체가 필요한 상황(비밀 노출)에서 가장 하고 싶지 않은 일이다.
 *
 * **그래도 되돌릴 수는 없다** — 겹침 기간이 지나면 옛 비밀은 영영 무효이고, 교체를 취소할
 * 방법도 없다. 그래서 이 동작은 화면에서 확인 절차 뒤에 둔다.
 */
class RotateMerchantWebhookSecretUseCase(
	private val merchantRepository: MerchantRepository,
	private val webhookSigner: WebhookSigner,
	private val clock: Clock,
) {
	/** 교체된 뒤의 설정을 돌려준다 — 화면이 새 비밀과 **겹침 만료 시각**을 곧바로 보여줘야 한다. */
	fun execute(merchantId: MerchantId): MerchantWebhookSettings {
		val merchant =
			merchantRepository.findById(merchantId)
				?: error("인증된 가맹점($merchantId)을 찾을 수 없습니다.")

		val now = clock.instant()
		merchant.rotateWebhookSecret(now)
		merchantRepository.save(merchant)

		return merchantWebhookSettings(merchant, webhookSigner, now)
	}
}
