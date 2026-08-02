package paytech.practice.pay.infra.persistence.jooq.settlement

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import paytech.practice.pay.application.port.outbound.SettlementReceivableListQuery
import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT
import paytech.practice.pay.dbcore.jooq.tables.Payment.Companion.PAYMENT
import paytech.practice.pay.dbcore.jooq.tables.SettlementReceivable.Companion.SETTLEMENT_RECEIVABLE
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.insertTestPayment
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

private val BASE_DATE: LocalDate = LocalDate.parse("2026-08-01")

/**
 * `settlement_receivable`을 raw jOOQ로 직접 심는다 — Projection이 조인하는 대상일 뿐이고,
 * 이 테스트가 검증하려는 건 조회 결과이지 애그리게이트 저장이 아니다
 * (`CheckoutViewProjectionAdapterTest`가 `payment_quote`에 쓴 것과 같은 방식).
 */
private fun insertReceivable(
	merchantId: String,
	paymentId: String,
	status: String = "READY",
	netAmount: Long = 19_700,
	eligibleDate: LocalDate = BASE_DATE,
): String {
	val dsl = PersistenceTestSupport.dsl
	val merchantSeq =
		dsl
			.select(MERCHANT.MERCHANT_SEQ)
			.from(MERCHANT)
			.where(MERCHANT.MERCHANT_ID.eq(merchantId))
			.fetchOne(MERCHANT.MERCHANT_SEQ)!!
	val paymentSeq =
		dsl
			.select(PAYMENT.PAYMENT_SEQ)
			.from(PAYMENT)
			.where(PAYMENT.PAYMENT_ID.eq(paymentId))
			.fetchOne(PAYMENT.PAYMENT_SEQ)!!
	val id = "str_${uniqueSuffix()}"

	dsl
		.newRecord(SETTLEMENT_RECEIVABLE)
		.apply {
			settlementReceivableId = id
			this.paymentSeq = paymentSeq
			this.merchantSeq = merchantSeq
			settlementCurrency = "KRW"
			grossAmount = 20_000
			feeRate = BigDecimal("0.01500000")
			feeAmount = 300
			adjustmentAmount = 0
			this.netAmount = netAmount
			receivableStatus = status
			this.eligibleDate = eligibleDate
			createdAt = LocalDateTime.now()
			updatedAt = LocalDateTime.now()
			version = 0
		}.insert()
	return id
}

private fun query(
	merchantId: MerchantId? = null,
	status: SettlementReceivableStatus? = null,
	eligibleFrom: LocalDate? = null,
	eligibleTo: LocalDate? = null,
	page: Int = 0,
	size: Int = 50,
) = SettlementReceivableListQuery(merchantId, status, eligibleFrom, eligibleTo, page, size)

/**
 * DB가 테스트 JVM 전체에서 공유되므로 단언은 **항상 이 테스트가 만든 `merchantId`로 좁힌
 * 뒤에** 한다 — 전역 건수·합계를 세지 않는다.
 */
class SettlementReceivableListProjectionAdapterTest :
	FunSpec({
		val projection = SettlementReceivableListProjectionAdapter(PersistenceTestSupport.dsl)

		test("filters by merchant and joins the merchant name and order id") {
			val merchantId = insertTestMerchant()
			val other = insertTestMerchant()
			val paymentId = insertTestPayment(merchantId, merchantOrderId = "order-settle-1")
			insertReceivable(merchantId, paymentId)
			insertReceivable(other, insertTestPayment(other))

			val page = projection.find(query(merchantId = MerchantId(merchantId)))

			page.totalCount shouldBe 1L
			page.entries.single().merchantName shouldBe "테스트 가맹점"
			page.entries
				.single()
				.merchantOrderId.value shouldBe "order-settle-1"
			page.entries
				.single()
				.paymentId.value shouldBe paymentId
		}

		/**
		 * **이 화면의 핵심 숫자다** — 가맹점이 묻는 것은 "그래서 얼마를 받나"이고, 현재
		 * 페이지의 합으로는 답할 수 없다. 페이지를 넘어서도 합계는 필터 전체 기준이어야 한다.
		 */
		test("sums the net amount across the whole filter, not just the page") {
			val merchantId = insertTestMerchant()
			repeat(3) { insertReceivable(merchantId, insertTestPayment(merchantId), netAmount = 19_700) }

			val firstPage = projection.find(query(merchantId = MerchantId(merchantId), page = 0, size = 1))

			firstPage.entries.size shouldBe 1
			firstPage.totalCount shouldBe 3L
			firstPage.totalNetAmount shouldBe 59_100L
		}

		test("keeps the totals on a page beyond the last one") {
			val merchantId = insertTestMerchant()
			insertReceivable(merchantId, insertTestPayment(merchantId))

			val page = projection.find(query(merchantId = MerchantId(merchantId), page = 9, size = 10))

			page.entries.size shouldBe 0
			page.totalCount shouldBe 1L
			page.totalNetAmount shouldBe 19_700L
		}

		test("filters by status and by eligible-date range") {
			val merchantId = insertTestMerchant()
			insertReceivable(merchantId, insertTestPayment(merchantId), status = "PENDING", eligibleDate = BASE_DATE)
			insertReceivable(merchantId, insertTestPayment(merchantId), status = "READY", eligibleDate = BASE_DATE.plusDays(3))

			projection
				.find(query(merchantId = MerchantId(merchantId), status = SettlementReceivableStatus.PENDING))
				.totalCount shouldBe 1L

			val ranged =
				projection.find(query(merchantId = MerchantId(merchantId), eligibleFrom = BASE_DATE.plusDays(1)))
			ranged.totalCount shouldBe 1L
			ranged.entries.single().status shouldBe SettlementReceivableStatus.READY
		}

		// eligible_date는 하루에 여러 건이 흔하다 — seq 2차 정렬이 없으면 페이지 경계에서
		// 같은 행이 두 번 나오거나 빠진다.
		test("paginates without repeating rows when eligible dates tie") {
			val merchantId = insertTestMerchant()
			repeat(4) { insertReceivable(merchantId, insertTestPayment(merchantId), eligibleDate = BASE_DATE) }

			val first = projection.find(query(merchantId = MerchantId(merchantId), page = 0, size = 2))
			val second = projection.find(query(merchantId = MerchantId(merchantId), page = 1, size = 2))

			val seen = (first.entries + second.entries).map { it.settlementReceivableId.value }
			seen.size shouldBe 4
			seen.toSet().size shouldBe 4
		}

		// 환전 전(PENDING)에는 환전 관련 값이 비어 있다 — 화면이 "-"로 그려야 한다.
		test("exposes null exchange amounts before the receivable is READY") {
			val merchantId = insertTestMerchant()
			insertReceivable(merchantId, insertTestPayment(merchantId), status = "PENDING")

			val entry = projection.find(query(merchantId = MerchantId(merchantId))).entries.single()

			entry.exchangeReceivedAmount shouldBe null
			entry.exchangeProfitLossAmount shouldBe null
			entry.feeRate.compareTo(BigDecimal("0.015")) shouldBe 0
		}

		/**
		 * **이 테스트가 이 집계의 존재 이유다.** 한동안 합계가 상태를 가리지 않고 전부 더해서,
		 * 막아 둔 돈(`HELD`)과 끝낸 돈(`CANCELLED`)까지 "정산 예정 금액"에 들어가 있었다 —
		 * ADR-007은 그 반대라고 적고 있었는데 코드가 따라오지 않은 것이다. 화면의 머리 숫자가
		 * **실제로 나갈 금액보다 커지는** 종류의 어긋남이라 회귀로 고정한다.
		 */
		test("the total counts only what is still on the payout path") {
			val merchantId = insertTestMerchant()
			insertReceivable(merchantId, insertTestPayment(merchantId), status = "PENDING")
			insertReceivable(merchantId, insertTestPayment(merchantId), status = "READY")
			insertReceivable(merchantId, insertTestPayment(merchantId), status = "HELD")
			insertReceivable(merchantId, insertTestPayment(merchantId), status = "CANCELLED")

			val page = projection.find(query(merchantId = MerchantId(merchantId)))

			// 목록에는 네 건이 다 나온다 — 합계에서 빠지는 것과 감추는 것은 다르다.
			page.totalCount shouldBe 4L
			page.totalNetAmount shouldBe 2 * 19_700L
		}

		/** 빼기만 하고 어디로 갔는지 말해주지 않으면 숫자가 달라진 이유를 찾을 수 없다. */
		test("reports how much was held so the total's shortfall is explainable") {
			val merchantId = insertTestMerchant()
			insertReceivable(merchantId, insertTestPayment(merchantId), status = "READY")
			insertReceivable(merchantId, insertTestPayment(merchantId), status = "HELD")
			insertReceivable(merchantId, insertTestPayment(merchantId), status = "HELD")

			val page = projection.find(query(merchantId = MerchantId(merchantId)))

			page.heldCount shouldBe 2L
			page.heldNetAmount shouldBe 2 * 19_700L
			page.totalNetAmount shouldBe 19_700L
		}

		/** 보류 집계도 **같은 필터를 받는다** — 필터가 곧 질문의 범위다. */
		test("the held figures respect the same filter as everything else") {
			val merchantId = insertTestMerchant()
			insertReceivable(merchantId, insertTestPayment(merchantId), status = "HELD")

			val readyOnly =
				projection.find(query(merchantId = MerchantId(merchantId), status = SettlementReceivableStatus.READY))

			readyOnly.heldCount shouldBe 0L
			readyOnly.heldNetAmount shouldBe 0L
		}
	})
