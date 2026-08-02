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
 * - **정산 금액을 "예정"과 "제외" 두 열로 나눈다.** 아래 절 참고.
 *
 * ## 금액 열을 나눈 이유 — 열을 그냥 더해도 맞아야 한다
 *
 * 이 파일은 **받는 사람이 엑셀에서 합계를 내라고** 금액을 숫자 셀로 쓴다. 그런데 한 열에
 * 모든 상태의 금액을 담으면, `HELD`(지급을 막아 둔 돈)와 `CANCELLED`(정산하지 않기로 끝낸
 * 돈)까지 함께 더해져 **화면이 말하는 금액과 다른 값**이 나온다 — 화면 합계는 지급 경로에
 * 살아 있는 것만 더하기 때문이다(ADR-007).
 *
 * 그래서 `PENDING`/`READY`는 "정산 예정 금액"에, 나머지는 "정산 제외 금액"에 쓰고 반대쪽
 * 칸은 **비운다**. 열을 통째로 선택해 합계를 내도 곧바로 맞는 값이 되고, 어느 쪽이 왜
 * 빠졌는지는 "상태"와 "보류 사유" 열이 답한다. `0`을 쓰지 않는 것은 환전 손익과 같은 규칙이다
 * — `0`은 "금액이 0이었다"로 읽힌다.
 *
 * 합계 행을 파일에 직접 넣는 방법도 있었지만 택하지 않았다: 받는 사람이 정렬·필터를 걸면
 * 합계 행이 데이터에 섞여 조용히 틀린 값이 된다.
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
				// 같은 금액이 상태에 따라 둘 중 한 열에만 들어간다 — 반대쪽은 비운다(0이 아니다).
				// 기준은 상태 자신이 갖는다: 여기서 목록을 따로 들면 화면 합계와 갈릴 수 있다.
				val payable = entry.status.isOnPayoutPath
				row.createCell(column++).apply { if (payable) setCellValue(entry.netAmount.toDouble()) }
				row.createCell(column++).apply { if (!payable) setCellValue(entry.netAmount.toDouble()) }
				// 환전 전에는 빈 칸으로 둔다 — 0을 쓰면 "손익 0"으로 읽힌다.
				row.createCell(column++).apply { entry.exchangeReceivedAmount?.let { setCellValue(it.toDouble()) } }
				row.createCell(column++).apply { entry.exchangeProfitLossAmount?.let { setCellValue(it.toDouble()) } }
				row.createCell(column++).setCellValue(entry.status.name)
				// 화면에는 사유를 적어 두고 파일에서 빼면, 파일만 받은 사람은 왜 막혔는지 알 수 없다.
				row.createCell(column++).setCellValue(entry.holdReasonCode ?: "")
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
				"정산 제외 금액",
				"환전 확보 금액",
				"환전 손익",
				"상태",
				"보류 사유",
				"생성 시각(KST)",
				"결제 식별자",
				"정산 채권 식별자",
			)
	}
}
