package paytech.practice.pay.application.customer

import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.payment.PaymentId

/**
 * [RevealPaymentCustomerUseCase]의 입력이다.
 *
 * @property actorInternalUserId 열람하는 내부 운영자. **요청 본문이 아니라 인증 주체에서
 * 온다** — 본문에서 받으면 감사 기록이 자기 신고가 된다(`ReleaseSettlementHoldCommand`와
 * 같은 판단).
 * @property reason 왜 보는지. 자동 경로가 없는 행위라 실행한 사람 말고는 이유를 아는 곳이
 * 없다 — 공백이면 거부한다.
 * @property clientIp 요청이 온 IP. 프록시 뒤에서는 알 수 없을 수 있어 nullable이고, **없다고
 * 열람을 막지는 않는다** — 막으면 기록이 아니라 인가 수단이 된다.
 */
data class RevealPaymentCustomerCommand(
	val paymentId: PaymentId,
	val actorInternalUserId: InternalUserId,
	val reason: String,
	val clientIp: String?,
)
