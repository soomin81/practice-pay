package paytech.practice.pay.application.payment

import paytech.practice.pay.domain.merchant.MerchantId

/** 존재하지 않는 [MerchantId]로 결제 생성을 시도했을 때 던진다. */
class MerchantNotFoundException(
	merchantId: MerchantId,
) : RuntimeException("Merchant를 찾을 수 없습니다: ${merchantId.value}")
