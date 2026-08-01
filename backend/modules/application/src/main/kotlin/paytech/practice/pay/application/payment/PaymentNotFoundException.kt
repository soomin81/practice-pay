package paytech.practice.pay.application.payment

/**
 * 결제를 찾지 못했을 때 던진다. inbound Adapter가 `404`로 옮긴다.
 *
 * **식별자를 메시지에 넣지 않는다** — 이 예외는 존재하지 않는 결제와 접근할 수 없는 결제를
 * 같은 응답으로 덮는 자리이기도 해서, 응답 본문이 둘을 구분할 단서를 주지 않는 편이 낫다
 * (`RevokeMerchantApiKeyUseCase`가 다른 가맹점 Key를 404로 가리는 것과 같은 결).
 */
class PaymentNotFoundException(
	paymentId: String,
) : RuntimeException("결제를 찾을 수 없습니다.") {
	init {
		require(paymentId.isNotBlank()) { "paymentId는 공백일 수 없습니다." }
	}
}
