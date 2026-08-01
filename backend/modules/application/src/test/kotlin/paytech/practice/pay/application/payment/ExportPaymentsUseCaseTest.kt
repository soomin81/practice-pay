package paytech.practice.pay.application.payment

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import paytech.practice.pay.application.port.outbound.PaymentExportWriter
import paytech.practice.pay.application.port.outbound.PaymentListEntry
import paytech.practice.pay.application.port.outbound.PaymentListPage
import paytech.practice.pay.application.port.outbound.PaymentListProjection
import paytech.practice.pay.application.port.outbound.PaymentListQuery
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import java.time.Instant

private val EXPORT_MERCHANT_ID = MerchantId("mrc_test_001")

private fun exportEntry(index: Int) =
	PaymentListEntry(
		paymentId = PaymentId("pay_$index"),
		merchantId = EXPORT_MERCHANT_ID,
		merchantName = "테스트 가맹점",
		merchantOrderId = MerchantOrderId("order-$index"),
		orderName = "주문 $index",
		orderAmount = Money(10_000),
		paymentAsset = Asset.USDC,
		paymentAmount = TokenAmount(6_666_667),
		tokenDecimals = 6,
		network = BlockchainNetwork.BASE_SEPOLIA,
		status = PaymentStatus.SUCCEEDED,
		failureReason = null,
		transactionHash = null,
		paidAt = Instant.parse("2026-07-20T10:05:00Z"),
		createdAt = Instant.parse("2026-07-20T10:00:00Z"),
	)

private fun projectionReturning(
	count: Int,
	querySlot: io.mockk.CapturingSlot<PaymentListQuery>,
): PaymentListProjection {
	val projection = mockk<PaymentListProjection>()
	every { projection.find(capture(querySlot)) } returns
		PaymentListPage(entries = (1..count).map(::exportEntry), totalCount = count.toLong())
	return projection
}

private fun writerCapturing(slot: io.mockk.CapturingSlot<List<PaymentListEntry>>): PaymentExportWriter {
	val writer = mockk<PaymentExportWriter>()
	every { writer.writeSpreadsheet(capture(slot)) } returns byteArrayOf(1, 2, 3)
	return writer
}

class ExportPaymentsUseCaseTest :
	FunSpec({

		// 화면 페이징 상한(200)에 묶이면 내보내기가 200건에서 잘린다 — 다른 경로여야 한다.
		test("asks for one more row than the export cap, not the page-size cap") {
			val querySlot = slot<PaymentListQuery>()
			val rowsSlot = slot<List<PaymentListEntry>>()

			ExportPaymentsUseCase(projectionReturning(3, querySlot), writerCapturing(rowsSlot))
				.execute(ListPaymentsCommand(size = 20))

			querySlot.captured.size shouldBe PaymentExportPolicy.MAX_EXPORT_ROWS + 1
			querySlot.captured.page shouldBe 0
		}

		test("passes the filters through and reports the row count") {
			val querySlot = slot<PaymentListQuery>()
			val rowsSlot = slot<List<PaymentListEntry>>()

			val result =
				ExportPaymentsUseCase(projectionReturning(3, querySlot), writerCapturing(rowsSlot)).execute(
					ListPaymentsCommand(merchantId = EXPORT_MERCHANT_ID, status = PaymentStatus.SUCCEEDED),
				)

			querySlot.captured.merchantId shouldBe EXPORT_MERCHANT_ID
			querySlot.captured.status shouldBe PaymentStatus.SUCCEEDED
			result.rowCount shouldBe 3
			result.truncated shouldBe false
			rowsSlot.captured.size shouldBe 3
		}

		/**
		 * **조용히 잘린 파일을 받아가는 것이 이 기능에서 가장 위험한 실패다** — 상한을 넘으면
		 * 잘라내되 `truncated`로 반드시 알린다. 상한+1건을 조회하는 것이 "딱 맞음"과 "넘침"을
		 * 구분하는 방법이다.
		 */
		test("truncates at the cap and says so") {
			val querySlot = slot<PaymentListQuery>()
			val rowsSlot = slot<List<PaymentListEntry>>()
			val projection = projectionReturning(PaymentExportPolicy.MAX_EXPORT_ROWS + 1, querySlot)

			val result = ExportPaymentsUseCase(projection, writerCapturing(rowsSlot)).execute(ListPaymentsCommand())

			result.truncated shouldBe true
			result.rowCount shouldBe PaymentExportPolicy.MAX_EXPORT_ROWS
			rowsSlot.captured.size shouldBe PaymentExportPolicy.MAX_EXPORT_ROWS
		}

		test("exactly the cap is not reported as truncated") {
			val querySlot = slot<PaymentListQuery>()
			val rowsSlot = slot<List<PaymentListEntry>>()
			val projection = projectionReturning(PaymentExportPolicy.MAX_EXPORT_ROWS, querySlot)

			val result = ExportPaymentsUseCase(projection, writerCapturing(rowsSlot)).execute(ListPaymentsCommand())

			result.truncated shouldBe false
			result.rowCount shouldBe PaymentExportPolicy.MAX_EXPORT_ROWS
		}
	})

class ExportMerchantPaymentsUseCaseTest :
	FunSpec({

		/**
		 * 조회 쪽과 같은 회귀지만 **내보내기에서 더 중요하다** — 범위가 새면 화면에 잠깐
		 * 보이는 정도가 아니라 남의 가맹점 결제가 파일로 통째로 빠져나간다.
		 */
		test("always scopes to the given merchant, ignoring the merchantId in the command") {
			val querySlot = slot<PaymentListQuery>()
			val rowsSlot = slot<List<PaymentListEntry>>()

			ExportMerchantPaymentsUseCase(projectionReturning(1, querySlot), writerCapturing(rowsSlot)).execute(
				merchantId = EXPORT_MERCHANT_ID,
				command = ListPaymentsCommand(merchantId = MerchantId("mrc_someone_else")),
			)

			querySlot.captured.merchantId shouldBe EXPORT_MERCHANT_ID
		}

		test("applies the same export cap as the admin use case") {
			val querySlot = slot<PaymentListQuery>()
			val rowsSlot = slot<List<PaymentListEntry>>()

			ExportMerchantPaymentsUseCase(projectionReturning(1, querySlot), writerCapturing(rowsSlot)).execute(
				merchantId = EXPORT_MERCHANT_ID,
				command = ListPaymentsCommand(),
			)

			querySlot.captured.size shouldBe PaymentExportPolicy.MAX_EXPORT_ROWS + 1
		}
	})
