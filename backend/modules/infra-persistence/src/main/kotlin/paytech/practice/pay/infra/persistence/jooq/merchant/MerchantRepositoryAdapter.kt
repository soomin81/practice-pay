package paytech.practice.pay.infra.persistence.jooq.merchant

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT
import paytech.practice.pay.dbcore.jooq.tables.records.MerchantRecord
import paytech.practice.pay.domain.merchant.Merchant
import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.merchant.MerchantStatus
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant

/**
 * jOOQ로 [MerchantRepository] Port를 구현한다.
 *
 * `paytech.practice.pay.dbcore.jooq.tables.Merchant`(jOOQ가 생성한 테이블 참조
 * 클래스)와 도메인 [Merchant]가 이름이 같아 클래스 자체를 그대로 import하면
 * 충돌한다 — 그래서 테이블 클래스는 참조하지 않고 `Merchant.Companion.MERCHANT`
 * (싱글턴 테이블 참조 값) 하나만 import한다. 이 프로젝트의 모든 Repository
 * Adapter가 같은 이름 충돌(Payment, PaymentQuote, CheckoutSession, OutboxEvent)을
 * 겪으므로 동일한 방식을 따른다.
 */
@Repository
class MerchantRepositoryAdapter(
	private val dsl: DSLContext,
) : MerchantRepository {
	override fun findById(merchantId: MerchantId): Merchant? =
		dsl
			.selectFrom(MERCHANT)
			.where(MERCHANT.MERCHANT_ID.eq(merchantId.value))
			.fetchOne()
			?.toDomain()

	private fun MerchantRecord.toDomain(): Merchant =
		Merchant.reconstitute(
			id = MerchantId(merchantId!!),
			code = MerchantCode(merchantCode!!),
			name = merchantName!!,
			createdAt = createdAt!!.toUtcInstant(),
			status = MerchantStatus.valueOf(merchantStatus!!),
			webhookUrl = webhookUrl?.let { HttpUrl(it) },
			updatedAt = updatedAt!!.toUtcInstant(),
		)
}
