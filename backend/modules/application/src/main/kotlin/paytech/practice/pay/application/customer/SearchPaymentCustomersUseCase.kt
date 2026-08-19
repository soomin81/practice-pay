package paytech.practice.pay.application.customer

import paytech.practice.pay.application.port.outbound.PaymentCustomerSearchProjection

/**
 * 내부 운영자가 **이메일 또는 휴대전화로 결제를 찾는** Use Case다
 * (`GET /admin/payment-customers`, `docs/architecture/admin-console-api.md`의 4.7).
 *
 * ADR-008이 구매자 정보를 받기로 한 이유 중 하나에 직접 답하는 자리다 — 고객이 "제 주문
 * 어떻게 됐나요"라고 연락해 왔을 때, 지갑 주소밖에 없던 시절에는 답할 방법이 없었다.
 *
 * ## 정확 일치만 된다
 *
 * 암호문은 값마다 랜덤 IV라 검색에 쓸 수 없어서 `HMAC(pepper, 정규화된 값)`을 별도 컬럼에
 * 두고 그걸로 찾는다. **부분 검색(도메인만, 앞 세 글자)은 원리적으로 불가능하고**, 되게
 * 하려면 평문 색인이 필요한데 그건 암호화를 무력화한다(ADR-008의 5).
 *
 * **이름으로는 찾을 수 없다.** 이름에는 Blind Index를 두지 않았다 — 동명이인이 흔해 결과를
 * 믿을 수 없고, 두면 "같은 이름인지"가 드러나는 대가만 남는다.
 *
 * ## 복호화하지 않는다
 *
 * 돌려주는 것은 마스킹된 값뿐이다. 이 Use Case는 [PaymentCustomerCrypto]를 쓰지만
 * **인덱스를 만들기 위해서지 원문을 얻기 위해서가 아니다** — `decrypt`를 부르는 곳은
 * [RevealPaymentCustomerUseCase] 하나로 유지한다.
 */
class SearchPaymentCustomersUseCase(
	private val paymentCustomerSearchProjection: PaymentCustomerSearchProjection,
	private val paymentCustomerCrypto: PaymentCustomerCrypto,
) {
	/**
	 * **이메일과 휴대전화 중 정확히 하나**를 받는다. 둘 다이거나 둘 다 아니면 거부한다 —
	 * 둘을 AND로 걸 수 있게 하면 "이 이메일과 이 번호가 같은 사람인가"를 확인할 수 있게 되고,
	 * 그건 찾는 것이 아니라 대조하는 것이다.
	 */
	fun execute(command: SearchPaymentCustomersCommand): SearchPaymentCustomersResult {
		val email = command.email
		val phone = command.phone
		require((email == null) != (phone == null)) {
			"이메일과 휴대전화 중 정확히 하나로만 검색할 수 있습니다."
		}

		val matches =
			if (email != null) {
				paymentCustomerSearchProjection.findByEmailIndex(paymentCustomerCrypto.emailIndex(email))
			} else {
				paymentCustomerSearchProjection.findByPhoneIndex(paymentCustomerCrypto.phoneIndex(checkNotNull(phone)))
			}

		return SearchPaymentCustomersResult(matches = matches)
	}
}
