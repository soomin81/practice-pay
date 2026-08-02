package paytech.practice.pay.application.settlement

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import paytech.practice.pay.application.port.outbound.SettlementExportWriter
import paytech.practice.pay.application.port.outbound.SettlementReceivableListEntry
import paytech.practice.pay.application.port.outbound.SettlementReceivableListPage
import paytech.practice.pay.application.port.outbound.SettlementReceivableListProjection
import paytech.practice.pay.application.port.outbound.SettlementReceivableListQuery
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private val MERCHANT_ID = MerchantId("mrc_test_001")
private val OTHER_MERCHANT_ID = MerchantId("mrc_test_002")

private fun entry(index: Int) =
	SettlementReceivableListEntry(
		settlementReceivableId = SettlementReceivableId("stl_$index"),
		merchantId = MERCHANT_ID,
		merchantName = "테스트 가맹점",
		paymentId = PaymentId("pay_$index"),
		merchantOrderId = MerchantOrderId("order-$index"),
		status = SettlementReceivableStatus.READY,
		settlementCurrency = "KRW",
		grossAmount = 20_000,
		feeRate = BigDecimal("0.015"),
		feeAmount = 300,
		adjustmentAmount = 0,
		netAmount = 19_700,
		exchangeReceivedAmount = 20_101,
		exchangeProfitLossAmount = 101,
		eligibleDate = LocalDate.parse("2026-08-01"),
		holdReasonCode = null,
		createdAt = Instant.parse("2026-08-01T04:07:24Z"),
	)

private fun projectionReturning(
	count: Int,
	querySlot: CapturingSlot<SettlementReceivableListQuery> = slot(),
): SettlementReceivableListProjection {
	val projection = mockk<SettlementReceivableListProjection>()
	every { projection.find(capture(querySlot)) } returns
		SettlementReceivableListPage(
			entries = (1..count).map(::entry),
			totalCount = count.toLong(),
			totalNetAmount = count * 19_700L,
		)
	return projection
}

private fun writerCapturing(slot: CapturingSlot<List<SettlementReceivableListEntry>>): SettlementExportWriter {
	val writer = mockk<SettlementExportWriter>()
	every { writer.writeSpreadsheet(capture(slot)) } returns byteArrayOf(1, 2, 3)
	return writer
}

class ExportSettlementReceivablesUseCaseTest :
	FunSpec({

		/**
		 * **상한보다 1건 더 조회한다** — 정확히 상한만큼 조회하면 "딱 맞게 채워진 것"과
		 * "넘쳐서 잘린 것"을 구분할 수 없다.
		 */
		test("asks the projection for one row beyond the limit so truncation is detectable") {
			val querySlot = slot<SettlementReceivableListQuery>()
			val rows = slot<List<SettlementReceivableListEntry>>()

			ExportSettlementReceivablesUseCase(projectionReturning(3, querySlot), writerCapturing(rows))
				.execute(ListSettlementReceivablesCommand())

			querySlot.captured.size shouldBe SettlementExportPolicy.MAX_EXPORT_ROWS + 1
			querySlot.captured.page shouldBe 0
		}

		/**
		 * **조용히 잘린 파일을 받아가는 것이 이 기능에서 가장 위험한 실패다.** 잘렸다는
		 * 사실이 결과에 실려야 화면이 "기간을 좁히라"고 안내할 수 있다.
		 */
		test("reports truncation and writes exactly the limit when there are more rows") {
			val rows = slot<List<SettlementReceivableListEntry>>()

			val result =
				ExportSettlementReceivablesUseCase(
					projectionReturning(SettlementExportPolicy.MAX_EXPORT_ROWS + 1),
					writerCapturing(rows),
				).execute(ListSettlementReceivablesCommand())

			result.truncated shouldBe true
			result.rowCount shouldBe SettlementExportPolicy.MAX_EXPORT_ROWS
			rows.captured.size shouldBe SettlementExportPolicy.MAX_EXPORT_ROWS
		}

		/** 딱 상한만큼이면 자르지 않는다 — 경계에서 헛된 경고를 띄우면 사람이 무시하게 된다. */
		test("does not report truncation when the rows fit exactly") {
			val rows = slot<List<SettlementReceivableListEntry>>()

			val result =
				ExportSettlementReceivablesUseCase(
					projectionReturning(SettlementExportPolicy.MAX_EXPORT_ROWS),
					writerCapturing(rows),
				).execute(ListSettlementReceivablesCommand())

			result.truncated shouldBe false
			result.rowCount shouldBe SettlementExportPolicy.MAX_EXPORT_ROWS
		}

		test("passes the filters through unchanged") {
			val querySlot = slot<SettlementReceivableListQuery>()
			val rows = slot<List<SettlementReceivableListEntry>>()

			ExportSettlementReceivablesUseCase(projectionReturning(1, querySlot), writerCapturing(rows))
				.execute(
					ListSettlementReceivablesCommand(
						status = SettlementReceivableStatus.READY,
						eligibleFrom = LocalDate.parse("2026-08-01"),
						eligibleTo = LocalDate.parse("2026-08-31"),
					),
				)

			querySlot.captured.status shouldBe SettlementReceivableStatus.READY
			querySlot.captured.eligibleFrom shouldBe LocalDate.parse("2026-08-01")
			querySlot.captured.eligibleTo shouldBe LocalDate.parse("2026-08-31")
		}

		/**
		 * 내부 운영자용은 **가맹점을 지정하지 않으면 전 가맹점**이다 — 이 Use Case의 존재
		 * 이유이자, 가맹점용과 반드시 갈라야 하는 이유다.
		 */
		test("admin export covers every merchant when no merchant is given") {
			val querySlot = slot<SettlementReceivableListQuery>()
			val rows = slot<List<SettlementReceivableListEntry>>()

			ExportSettlementReceivablesUseCase(projectionReturning(1, querySlot), writerCapturing(rows))
				.execute(ListSettlementReceivablesCommand())

			querySlot.captured.merchantId.shouldBeNull()
		}

		/**
		 * **가맹점용은 Command의 `merchantId`를 쳐다보지 않는다.** 새면 남의 가맹점 매출과
		 * 수취 예정 금액이 파일로 통째로 빠져나가므로, 인자로 받은 값이 언제나 이긴다.
		 */
		test("merchant export ignores the merchantId in the command and uses the argument") {
			val querySlot = slot<SettlementReceivableListQuery>()
			val rows = slot<List<SettlementReceivableListEntry>>()

			ExportMerchantSettlementReceivablesUseCase(projectionReturning(1, querySlot), writerCapturing(rows))
				.execute(
					merchantId = MERCHANT_ID,
					command = ListSettlementReceivablesCommand(merchantId = OTHER_MERCHANT_ID),
				)

			querySlot.captured.merchantId shouldBe MERCHANT_ID
		}
	})
