package paytech.practice.pay.infra.persistence.jooq.merchant

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.MerchantListProjection
import paytech.practice.pay.application.port.outbound.MerchantSummary
import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT
import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.merchant.MerchantStatus
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant

/**
 * jOOQ로 [MerchantListProjection] Port를 구현한다.
 *
 * [MerchantRepositoryAdapter]와 같은 `merchant` 테이블을 보지만, 별도 클래스로
 * 둔 이유는 [MerchantListProjection]의 KDoc에 적힌 것과 같다 — Command Repository
 * (Aggregate 저장·복원)와 목록 조회(읽기 전용 Projection)를 코드 레벨에서도
 * 분리해 둔다. `Merchant.reconstitute`를 거치지 않고 jOOQ Record에서 곧바로
 * [MerchantSummary]로 매핑한다 — `version`처럼 목록 화면에 필요 없는 필드는
 * 아예 조회하지 않는다.
 */
@Repository
class MerchantListProjectionAdapter(
	private val dsl: DSLContext,
) : MerchantListProjection {
	override fun findAll(): List<MerchantSummary> =
		dsl
			.select(MERCHANT.MERCHANT_ID, MERCHANT.MERCHANT_CODE, MERCHANT.MERCHANT_NAME, MERCHANT.MERCHANT_STATUS, MERCHANT.CREATED_AT)
			.from(MERCHANT)
			.orderBy(MERCHANT.CREATED_AT.desc())
			.fetch { record ->
				MerchantSummary(
					merchantId = MerchantId(record.get(MERCHANT.MERCHANT_ID)!!),
					merchantCode = MerchantCode(record.get(MERCHANT.MERCHANT_CODE)!!),
					merchantName = record.get(MERCHANT.MERCHANT_NAME)!!,
					status = MerchantStatus.valueOf(record.get(MERCHANT.MERCHANT_STATUS)!!),
					createdAt = record.get(MERCHANT.CREATED_AT)!!.toUtcInstant(),
				)
			}
}
