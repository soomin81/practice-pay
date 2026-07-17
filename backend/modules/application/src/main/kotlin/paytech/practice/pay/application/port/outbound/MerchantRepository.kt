package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.merchant.Merchant
import paytech.practice.pay.domain.merchant.MerchantId

/**
 * [Merchant] Aggregate를 복원하는 Command Repository Outbound Port다.
 *
 * 이 슬라이스(결제 생성)에서는 조회만 필요해 `findById`만 정의한다 — `Merchant`
 * 등록·상태 변경 Use Case가 추가될 때 `save` 등을 함께 확장한다.
 */
interface MerchantRepository {
	/** `merchant_id`로 Merchant를 복원한다. 없으면 `null`이다. */
	fun findById(merchantId: MerchantId): Merchant?
}
