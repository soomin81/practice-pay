package paytech.practice.pay.infra.support.export

import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.springframework.stereotype.Component
import paytech.practice.pay.application.port.outbound.SettlementExportWriter
import paytech.practice.pay.application.port.outbound.SettlementReceivableListEntry
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Apache POI로 [SettlementExportWriter]를 구현한다(`.xlsx`).
 *
 * 파일을 만드는 방식은 `XlsxPaymentExportWriter`와 같다(SXSSF 스트리밍, `dispose()`로 임시
 * 파일 정리, 금액은 숫자 셀, 시각은 KST) — 그 근거는 저쪽 KDoc에 한 번만 적었다.
 * **정산 파일에만 해당하는 판단은 아래 셋이다.**
 *
 * - **정산 예정일은 문자열로 쓴다.** `LocalDate`를 엑셀 날짜 셀로 넣으려면 셀 서식을
 *   따로 붙여야 하고, 서식이 없으면 받는 사람 화면에 `45872` 같은 일련번호가 뜬다.
 *   `2026-08-01`은 그대로 읽히고 정렬도 사전순이 곧 날짜순이라 문제가 없다.
 * - **수수료율은 소수 그대로 넣는다**(`0.015`). 화면은 `1.5%`로 보여주지만, 파일에서는
 *   받는 사람이 자기 서식(백분율)을 붙이거나 그대로 곱해 쓸 수 있어야 한다.
 * - **환전 손익은 비어 있을 수 있다.** `READY` 전에는 값이 없는데, 그 칸에 `0`을 쓰면
 *   "손익이 0이었다"로 읽힌다 — 빈 칸으로 둬서 "아직 환전 안 됨"과 구분한다.
 */
@Component
class XlsxSettlementExportWriter : SettlementExportWriter {
	override fun writeSpreadsheet(entries: List<SettlementReceivableListEntry>): ByteArray {
		val workbook = SXSSFWorkbook(SXSSF_ROW_ACCESS_WINDOW)
		try {
			val sheet = workbook.createSheet("정산 채권")
			val headerStyle = headerStyle(workbook)

			val headerRow = sheet.createRow(0)
			HEADERS.forEachIndexed { index, title ->
				headerRow.createCell(index).apply {
					setCellValue(title)
					cellStyle = headerStyle
				}
			}

			entries.forEachIndexed { rowIndex, entry ->
				val row = sheet.createRow(rowIndex + 1)
				var column = 0
				row.createCell(column++).setCellValue(entry.eligibleDate.toString())
				row.createCell(column++).setCellValue(entry.merchantName)
				row.createCell(column++).setCellValue(entry.merchantOrderId.value)
				row.createCell(column++).setCellValue(entry.settlementCurrency)
				row.createCell(column++).setCellValue(entry.grossAmount.toDouble())
				row.createCell(column++).setCellValue(entry.feeRate.toDouble())
				row.createCell(column++).setCellValue(entry.feeAmount.toDouble())
				row.createCell(column++).setCellValue(entry.adjustmentAmount.toDouble())
				row.createCell(column++).setCellValue(entry.netAmount.toDouble())
				// 환전 전에는 빈 칸으로 둔다 — 0을 쓰면 "손익 0"으로 읽힌다.
				row.createCell(column++).apply { entry.exchangeReceivedAmount?.let { setCellValue(it.toDouble()) } }
				row.createCell(column++).apply { entry.exchangeProfitLossAmount?.let { setCellValue(it.toDouble()) } }
				row.createCell(column++).setCellValue(entry.status.name)
				row.createCell(column++).setCellValue(formatKst(entry.createdAt))
				row.createCell(column++).setCellValue(entry.paymentId.value)
				row.createCell(column).setCellValue(entry.settlementReceivableId.value)
			}

			return ByteArrayOutputStream().use { out ->
				workbook.write(out)
				out.toByteArray()
			}
		} finally {
			// 스트리밍 중 만든 임시 파일을 지운다. close()만으로는 지워지지 않는다.
			workbook.dispose()
			workbook.close()
		}
	}

	private fun formatKst(instant: Instant): String = KST_FORMATTER.format(instant)

	private fun headerStyle(workbook: Workbook): CellStyle =
		workbook.createCellStyle().apply {
			alignment = HorizontalAlignment.CENTER
			setFont(workbook.createFont().apply { bold = true })
		}

	companion object {
		/** 메모리에 유지하는 행 수. 이보다 오래된 행은 임시 파일로 내려간다(SXSSF 기본값과 같다). */
		private const val SXSSF_ROW_ACCESS_WINDOW = 100

		private val KST_FORMATTER: DateTimeFormatter =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Seoul"))

		private val HEADERS =
			listOf(
				"정산 예정일",
				"가맹점",
				"주문 번호",
				"통화",
				"정산 기준 금액",
				"수수료율",
				"수수료",
				"조정 금액",
				"정산 예정 금액",
				"환전 확보 금액",
				"환전 손익",
				"상태",
				"생성 시각(KST)",
				"결제 식별자",
				"정산 채권 식별자",
			)
	}
}
