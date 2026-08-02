package paytech.practice.pay.application.merchant

import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.WebhookSigner
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Clock

/**
 * Webhook 서명 비밀을 교체하는 Use Case다 (`POST /merchant/webhook/rotate-secret`).
 * 비밀이 노출됐을 때 되돌릴 수 있는 유일한 수단이다.
 *
 * **되돌릴 수 없고, 겹치는 기간도 없다.** 세대를 올리는 순간 옛 비밀로 검증하던
 * 가맹점 서버는 그다음 Webhook부터 전부 서명 불일치로 거부하게 된다 — 새 비밀을
 * 서버에 반영하기 전까지 그 사이의 이벤트를 놓친다는 뜻이다.
 *
 * 실무의 결제 게이트웨이는 대개 옛 비밀과 새 비밀을 한동안 함께 유효하게 두어
 * (여러 서명을 한 헤더에 실어) 이 공백을 없애는데, MVP는 그렇게 하지 않았다 —
 * 세대를 하나만 들고 있어서 "지금 유효한 비밀은 언제나 정확히 하나"다. 겹침이
 * 필요해지면 `webhook_secret_version`을 두 개(현재/직전) 들고 헤더에
 * `v1=현재,v1=직전`처럼 두 서명을 싣는 방향으로 넓힌다.
 *
 * 그래서 이 동작은 화면에서 확인 절차 뒤에 둔다.
 */
class RotateMerchantWebhookSecretUseCase(
	private val merchantRepository: MerchantRepository,
	private val webhookSigner: WebhookSigner,
	private val clock: Clock,
) {
	/** 교체된 뒤의 설정을 돌려준다 — 화면이 새 비밀을 곧바로 보여줄 수 있어야 한다. */
	fun execute(merchantId: MerchantId): MerchantWebhookSettings {
		val merchant =
			merchantRepository.findById(merchantId)
				?: error("인증된 가맹점($merchantId)을 찾을 수 없습니다.")

		merchant.rotateWebhookSecret(clock.instant())
		merchantRepository.save(merchant)

		return MerchantWebhookSettings(
			webhookUrl = merchant.webhookUrl?.value,
			signingSecret = webhookSigner.deriveSecret(merchant.id, merchant.webhookSecretVersion),
			secretVersion = merchant.webhookSecretVersion,
		)
	}
}
