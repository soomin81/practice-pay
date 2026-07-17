package paytech.practice.pay.application.payment

import paytech.practice.pay.domain.merchant.MerchantId

/** `Merchant.canAcceptPayments()`가 `false`인 가맹점으로 결제 생성을 시도했을 때 던진다. */
class MerchantCannotAcceptPaymentsException(
	merchantId: MerchantId,
) : RuntimeException("Merchant가 결제를 받을 수 없는 상태입니다: ${merchantId.value}")
