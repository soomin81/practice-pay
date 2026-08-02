package paytech.practice.pay.application.merchant

import java.time.Duration

/**
 * Webhook 서명 비밀 교체 정책이다.
 *
 * **한곳에 모은 이유**는 이 값을 쓰는 곳이 둘이고, 둘이 어긋나면 조용히 거짓말을 하기
 * 때문이다: 서명하는 쪽(`PublishOutboxEventUseCase`)과 화면에 "직전 비밀이 언제까지
 * 유효한지" 알려주는 쪽(`GetMerchantWebhookSettingsUseCase`)이 다른 값을 쓰면, 콘솔이
 * "아직 유효하다"고 말하는 동안 전송은 이미 새 비밀만 싣는 상황이 생긴다.
 *
 * `PaymentExportPolicy`/`PaymentListPaging`과 같은 자리·같은 성격이다 — `docs/`가 값을
 * 정하지 않아 애플리케이션이 고정한 운영 상수다.
 */
internal object WebhookSignaturePolicy {
	/**
	 * 비밀을 교체한 뒤 **직전 비밀이 함께 유효한 기간**이다. 이 동안은 서명이 두 개 실려
	 * 나가고, 가맹점은 둘 중 하나만 맞으면 받아들인다.
	 *
	 * 24시간으로 잡은 것은 **가맹점이 배포할 시간을 하루는 줘야 한다**는 판단이다. 너무
	 * 짧으면 겹침의 목적(교체 중에도 Webhook을 놓치지 않는다)을 못 이루고, 너무 길면
	 * 노출된 비밀이 그만큼 오래 살아 있다 — 교체하는 이유가 대개 노출이므로 무한정 늘릴
	 * 값이 아니다.
	 *
	 * **이 값을 줄이면 이미 교체한 가맹점의 겹침이 소급해서 짧아진다** — 유효 세대를
	 * 저장하지 않고 `rotated_at`과 이 상수로 매번 계산하기 때문이다.
	 */
	val SECRET_OVERLAP: Duration = Duration.ofHours(24)
}
