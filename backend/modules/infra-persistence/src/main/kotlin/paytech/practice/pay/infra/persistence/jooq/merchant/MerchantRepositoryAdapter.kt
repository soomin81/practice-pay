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
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

/**
 * jOOQ로 [MerchantRepository] Port를 구현한다.
 *
 * `paytech.practice.pay.dbcore.jooq.tables.Merchant`(jOOQ가 생성한 테이블 참조
 * 클래스)와 도메인 [Merchant]가 이름이 같아 클래스 자체를 그대로 import하면
 * 충돌한다 — 그래서 테이블 클래스는 참조하지 않고 `Merchant.Companion.MERCHANT`
 * (싱글턴 테이블 참조 값) 하나만 import한다. 이 프로젝트의 모든 Repository
 * Adapter가 같은 이름 충돌(Payment, PaymentQuote, CheckoutSession, OutboxEvent)을
 * 겪으므로 동일한 방식을 따른다.
 *
 * `save`의 낙관적 잠금 한계는 [paytech.practice.pay.infra.persistence.jooq.payment.PaymentRepositoryAdapter]와
 * 동일하다 — 도메인 [Merchant]가 자신의 `version`을 모르기 때문에, DB에서 방금
 * 읽은 version을 그대로 +1 해서 쓴다.
 */
@Repository
class MerchantRepositoryAdapter(
	private val dsl: DSLContext,
) : MerchantRepository {
	override fun save(merchant: Merchant) {
		val existing =
			dsl
				.selectFrom(MERCHANT)
				.where(MERCHANT.MERCHANT_ID.eq(merchant.id.value))
				.fetchOne()

		if (existing == null) {
			dsl
				.newRecord(MERCHANT)
				.apply {
					merchantId = merchant.id.value
					merchantCode = merchant.code.value
					merchantName = merchant.name
					merchantStatus = merchant.status.name
					webhookUrl = merchant.webhookUrl?.value
					webhookSecretVersion = merchant.webhookSecretVersion
					webhookSecretRotatedAt = merchant.webhookSecretRotatedAt?.toUtcLocalDateTime()
					createdAt = merchant.createdAt.toUtcLocalDateTime()
					updatedAt = merchant.updatedAt.toUtcLocalDateTime()
					version = 0L
				}.insert()
		} else {
			dsl
				.update(MERCHANT)
				.set(MERCHANT.MERCHANT_STATUS, merchant.status.name)
				.set(MERCHANT.WEBHOOK_URL, merchant.webhookUrl?.value)
				.set(MERCHANT.WEBHOOK_SECRET_VERSION, merchant.webhookSecretVersion)
				.set(MERCHANT.WEBHOOK_SECRET_ROTATED_AT, merchant.webhookSecretRotatedAt?.toUtcLocalDateTime())
				.set(MERCHANT.UPDATED_AT, merchant.updatedAt.toUtcLocalDateTime())
				.set(MERCHANT.VERSION, (existing.version ?: 0L) + 1)
				.where(MERCHANT.MERCHANT_SEQ.eq(existing.merchantSeq))
				.and(MERCHANT.VERSION.eq(existing.version))
				.execute()
				.also { updatedRows ->
					check(updatedRows == 1) {
						"Merchant(${merchant.id.value}) 저장에 실패했습니다 — " +
							"동시에 변경된 것으로 보입니다(예상 version=${existing.version})."
					}
				}
		}
	}

	override fun findById(merchantId: MerchantId): Merchant? =
		dsl
			.selectFrom(MERCHANT)
			.where(MERCHANT.MERCHANT_ID.eq(merchantId.value))
			.fetchOne()
			?.toDomain()

	override fun findByCode(merchantCode: MerchantCode): Merchant? =
		dsl
			.selectFrom(MERCHANT)
			.where(MERCHANT.MERCHANT_CODE.eq(merchantCode.value))
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
			webhookSecretVersion = webhookSecretVersion!!,
			webhookSecretRotatedAt = webhookSecretRotatedAt?.toUtcInstant(),
			updatedAt = updatedAt!!.toUtcInstant(),
		)
}
