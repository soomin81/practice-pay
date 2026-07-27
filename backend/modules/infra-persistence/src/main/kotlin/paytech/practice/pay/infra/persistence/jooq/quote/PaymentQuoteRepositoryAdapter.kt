package paytech.practice.pay.infra.persistence.jooq.quote

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.PaymentQuoteRepository
import paytech.practice.pay.dbcore.jooq.tables.PaymentQuote.Companion.PAYMENT_QUOTE
import paytech.practice.pay.domain.quote.PaymentQuote
import paytech.practice.pay.infra.persistence.jooq.paymentSeq
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

/**
 * jOOQ로 [PaymentQuoteRepository] Port를 구현한다.
 *
 * [PaymentQuote]는 불변 스냅샷이라(도메인 KDoc 참고) 갱신 경로가 없다 — [save]는
 * 항상 INSERT만 한다.
 *
 * `payment_quote.quote_currency` 컬럼은 도메인 [PaymentQuote]에 대응 필드가 없다 —
 * `Money`가 이 프로젝트에서 항상 KRW를 뜻하는 것과 같은 이유로(MVP는 KRW → USDC
 * 한 쌍만 지원), `"KRW"`로 고정해서 채운다.
 */
@Repository
class PaymentQuoteRepositoryAdapter(
	private val dsl: DSLContext,
) : PaymentQuoteRepository {
	override fun save(quote: PaymentQuote) {
		dsl
			.newRecord(PAYMENT_QUOTE)
			.apply {
				paymentQuoteId = quote.id.value
				paymentSeq = dsl.paymentSeq(quote.paymentId)
				marketProviderCode = quote.marketProviderCode
				baseAssetCode = quote.baseAsset.code
				quoteCurrency = "KRW"
				marketRate = quote.marketRate.value
				appliedRate = quote.appliedRate.value
				spreadRate = quote.spreadRate
				orderAmount = quote.orderAmount.amount
				paymentAmountMinor = quote.paymentAmount.amountMinor
				quotedAt = quote.quotedAt.toUtcLocalDateTime()
				expiresAt = quote.expiresAt.toUtcLocalDateTime()
				createdAt = quote.createdAt.toUtcLocalDateTime()
			}.insert()
	}
}
