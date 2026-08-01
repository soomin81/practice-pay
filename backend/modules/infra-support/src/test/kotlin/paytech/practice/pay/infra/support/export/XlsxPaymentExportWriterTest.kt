package paytech.practice.pay.infra.support.export

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.apache.poi.ss.usermodel.WorkbookFactory
import paytech.practice.pay.application.port.outbound.PaymentListEntry
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentFailureReason
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import java.io.ByteArrayInputStream
import java.time.Instant

private fun entry(
	status: PaymentStatus = PaymentStatus.SUCCEEDED,
	paymentAmount: Long = 72_992_701,
	failureReason: PaymentFailureReason? = null,
	transactionHash: TransactionHash? = TransactionHash("0x" + "d".repeat(64)),
	paidAt: Instant? = Instant.parse("2026-07-20T01:05:00Z"),
) = PaymentListEntry(
	paymentId = PaymentId("pay_001"),
	merchantId = MerchantId("mrc_001"),
	merchantName = "테스트 가맹점",
	merchantOrderId = MerchantOrderId("order-001"),
	orderName = "테스트 주문",
	orderAmount = Money(50_000),
	paymentAsset = Asset.USDC,
	paymentAmount = TokenAmount(paymentAmount),
	tokenDecimals = 6,
	network = BlockchainNetwork.BASE_SEPOLIA,
	status = status,
	failureReason = failureReason,
	transactionHash = transactionHash,
	paidAt = paidAt,
	createdAt = Instant.parse("2026-07-20T01:00:00Z"),
)

/** 실제로 POI로 다시 열어서 읽는다 — 바이트가 유효한 xlsx인지까지 확인하려는 것이다. */
private fun readSheet(bytes: ByteArray) = ByteArrayInputStream(bytes).use { WorkbookFactory.create(it).getSheetAt(0) }

class XlsxPaymentExportWriterTest :
	FunSpec({
		val writer = XlsxPaymentExportWriter()

		test("writes a header row and one row per entry") {
			val sheet = readSheet(writer.writeSpreadsheet(listOf(entry(), entry())))

			sheet.getRow(0).getCell(0).stringCellValue shouldBe "생성 시각(KST)"
			sheet.lastRowNum shouldBe 2 // 헤더 + 2행
			sheet.getRow(1).getCell(1).stringCellValue shouldBe "테스트 가맹점"
		}

		/**
		 * **금액은 숫자 셀이어야 한다** — 받는 사람이 엑셀에서 합계·정렬을 하기 때문이다.
		 * 문자열로 쓰면 화면상 똑같아 보이지만 계산이 되지 않는다.
		 */
		test("writes amounts as numbers, converting minor units to a decimal") {
			val sheet = readSheet(writer.writeSpreadsheet(listOf(entry(paymentAmount = 72_992_701))))
			val row = sheet.getRow(1)

			row.getCell(4).numericCellValue shouldBe 50_000.0
			row.getCell(6).numericCellValue shouldBe 72.992701
		}

		test("renders UTC instants in KST") {
			val sheet = readSheet(writer.writeSpreadsheet(listOf(entry())))

			// 2026-07-20T01:00:00Z == 같은 날 10:00 KST
			sheet.getRow(1).getCell(0).stringCellValue shouldBe "2026-07-20 10:00:00"
		}

		// 비어 있는 값은 null이 아니라 빈 문자열이어야 한다 — 셀 자체가 없으면 열이 밀린다.
		test("leaves optional columns blank instead of shifting them") {
			val sheet =
				readSheet(
					writer.writeSpreadsheet(
						listOf(
							entry(
								status = PaymentStatus.READY,
								failureReason = null,
								transactionHash = null,
								paidAt = null,
							),
						),
					),
				)
			val row = sheet.getRow(1)

			row.getCell(9).stringCellValue shouldBe ""
			row.getCell(10).stringCellValue shouldBe ""
			row.getCell(11).stringCellValue shouldBe ""
			row.getCell(12).stringCellValue shouldBe "pay_001"
		}

		test("writes a valid workbook even with no entries") {
			val sheet = readSheet(writer.writeSpreadsheet(emptyList()))

			sheet.lastRowNum shouldBe 0
			sheet.getRow(0).getCell(0).stringCellValue shouldBe "생성 시각(KST)"
		}
	})
