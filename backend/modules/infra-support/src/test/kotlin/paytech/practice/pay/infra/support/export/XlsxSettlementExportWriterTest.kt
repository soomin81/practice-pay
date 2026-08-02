package paytech.practice.pay.infra.support.export

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import paytech.practice.pay.application.port.outbound.SettlementReceivableListEntry
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/** 열 위치를 이름으로 부른다 — 열이 늘면 숫자만 바꾸는 것보다 어긋남이 드러난다. */
private const val COL_PAYABLE = 8
private const val COL_EXCLUDED = 9
private const val COL_STATUS = 12
private const val COL_HOLD_REASON = 13

private fun entry(
	status: SettlementReceivableStatus = SettlementReceivableStatus.READY,
	holdReasonCode: String? = null,
	netAmount: Long = 19_700,
) = SettlementReceivableListEntry(
	settlementReceivableId = SettlementReceivableId("stl_001"),
	merchantId = MerchantId("mrc_001"),
	merchantName = "테스트 가맹점",
	paymentId = PaymentId("pay_001"),
	merchantOrderId = MerchantOrderId("order-001"),
	status = status,
	settlementCurrency = "KRW",
	grossAmount = 20_000,
	feeRate = BigDecimal("0.015"),
	feeAmount = 300,
	adjustmentAmount = 0,
	netAmount = netAmount,
	exchangeReceivedAmount = if (status == SettlementReceivableStatus.READY) 20_101 else null,
	exchangeProfitLossAmount = if (status == SettlementReceivableStatus.READY) 101 else null,
	eligibleDate = LocalDate.parse("2026-08-01"),
	holdReasonCode = holdReasonCode,
	createdAt = Instant.parse("2026-08-01T04:07:24Z"),
)

/** 실제로 POI로 다시 열어서 읽는다 — 바이트가 유효한 xlsx인지까지 확인하려는 것이다. */
private fun readSheet(bytes: ByteArray) = ByteArrayInputStream(bytes).use { WorkbookFactory.create(it).getSheetAt(0) }

/** 빈 칸인지 — `0`이 들어갔다면 숫자 셀이라 여기서 걸린다. */
private fun Row.isBlankAt(column: Int): Boolean = getCell(column).cellType == CellType.BLANK

class XlsxSettlementExportWriterTest :
	FunSpec({
		val writer = XlsxSettlementExportWriter()

		test("writes a header row and one row per entry") {
			val sheet = readSheet(writer.writeSpreadsheet(listOf(entry(), entry())))

			sheet.getRow(0).getCell(0).stringCellValue shouldBe "정산 예정일"
			sheet.lastRowNum shouldBe 2 // 헤더 + 2행
			sheet.getRow(1).getCell(1).stringCellValue shouldBe "테스트 가맹점"
		}

		/**
		 * **이 테스트가 두 금액 열을 나눈 이유 전체다.** 받는 사람은 이 파일에서 열을 통째로
		 * 선택해 합계를 낸다 — 한 열에 모든 상태를 담으면 막아 둔 돈과 끝낸 돈까지 더해져
		 * 화면이 말하는 금액과 다른 값이 나온다(ADR-007).
		 */
		test("puts payable and excluded amounts in different columns so either column sums correctly") {
			val sheet =
				readSheet(
					writer.writeSpreadsheet(
						listOf(
							entry(status = SettlementReceivableStatus.READY),
							entry(status = SettlementReceivableStatus.PENDING),
							entry(status = SettlementReceivableStatus.HELD, holdReasonCode = "TRANSACTION_REORGED"),
							entry(status = SettlementReceivableStatus.CANCELLED),
						),
					),
				)

			val payable = (1..4).sumOf { sheet.getRow(it).getCell(COL_PAYABLE).let { cell -> cell.numericCellValueOrZero() } }
			val excluded = (1..4).sumOf { sheet.getRow(it).getCell(COL_EXCLUDED).let { cell -> cell.numericCellValueOrZero() } }

			payable shouldBe 2 * 19_700.0
			excluded shouldBe 2 * 19_700.0
		}

		/**
		 * **비우는 것과 `0`을 쓰는 것은 다르다** — `0`은 "금액이 0이었다"로 읽히고, 무엇보다
		 * 반대쪽 열의 합계에 섞이지는 않더라도 "이 채권은 금액이 없다"는 오해를 만든다
		 * (환전 손익을 빈 칸으로 두는 것과 같은 규칙).
		 */
		test("leaves the opposite amount column blank rather than writing zero") {
			val sheet =
				readSheet(
					writer.writeSpreadsheet(
						listOf(
							entry(status = SettlementReceivableStatus.READY),
							entry(status = SettlementReceivableStatus.HELD, holdReasonCode = "TRANSACTION_REORGED"),
						),
					),
				)

			sheet.getRow(1).getCell(COL_PAYABLE).numericCellValue shouldBe 19_700.0
			sheet.getRow(1).isBlankAt(COL_EXCLUDED) shouldBe true

			sheet.getRow(2).isBlankAt(COL_PAYABLE) shouldBe true
			sheet.getRow(2).getCell(COL_EXCLUDED).numericCellValue shouldBe 19_700.0
		}

		/** 화면에는 사유를 적어 두고 파일에서 빼면, 파일만 받은 사람은 왜 막혔는지 알 수 없다. */
		test("carries the hold reason into the file") {
			val sheet =
				readSheet(
					writer.writeSpreadsheet(
						listOf(entry(status = SettlementReceivableStatus.HELD, holdReasonCode = "TRANSACTION_REORGED")),
					),
				)
			val row = sheet.getRow(1)

			row.getCell(COL_STATUS).stringCellValue shouldBe "HELD"
			row.getCell(COL_HOLD_REASON).stringCellValue shouldBe "TRANSACTION_REORGED"
		}

		// 셀 자체가 없으면 뒤의 열이 통째로 밀린다 — 빈 문자열이어야 한다.
		test("writes an empty hold reason instead of shifting later columns") {
			val sheet = readSheet(writer.writeSpreadsheet(listOf(entry())))
			val row = sheet.getRow(1)

			row.getCell(COL_HOLD_REASON).stringCellValue shouldBe ""
			row.getCell(COL_HOLD_REASON + 1).stringCellValue shouldBe "2026-08-01 13:07:24"
		}

		test("writes a valid workbook even with no entries") {
			val sheet = readSheet(writer.writeSpreadsheet(emptyList()))

			sheet.lastRowNum shouldBe 0
			sheet.getRow(0).getCell(0).stringCellValue shouldBe "정산 예정일"
		}
	})

/** 빈 칸은 합계에서 `0`으로 센다 — 엑셀에서 열을 더할 때와 같은 셈이다. */
private fun org.apache.poi.ss.usermodel.Cell.numericCellValueOrZero(): Double = if (cellType == CellType.NUMERIC) numericCellValue else 0.0
