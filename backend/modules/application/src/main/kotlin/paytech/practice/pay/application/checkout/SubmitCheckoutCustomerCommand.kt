package paytech.practice.pay.application.checkout

import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.customer.CustomerEmail
import paytech.practice.pay.domain.customer.CustomerName
import paytech.practice.pay.domain.customer.CustomerPhone

/**
 * [SubmitCheckoutCustomerUseCase]의 입력이다.
 *
 * 고객이 체크아웃 페이지에서 **자기 이름·이메일·휴대전화를 직접 입력한** 시점의 입력이다 —
 * 가맹점 서버가 결제 생성 API로 대신 보내지 않는다(ADR-008의 2). 개인정보가 가맹점 서버를
 * 한 번 거쳐 오면 PG가 통제할 수 없는 유출 경로가 하나 늘기 때문이다.
 *
 * **평문 Value Object를 그대로 담는다.** 암호화는 이 Command가 아니라 Use Case가 부르는
 * `PaymentCustomerCrypto`에서 일어난다 — 형식 검증(각 VO의 `init`)이 암호화보다 먼저
 * 일어나야 잘못된 값이 되돌릴 수 없는 형태로 저장되지 않는다.
 */
data class SubmitCheckoutCustomerCommand(
	val checkoutSessionId: CheckoutSessionId,
	val name: CustomerName,
	val email: CustomerEmail,
	val phone: CustomerPhone,
)
