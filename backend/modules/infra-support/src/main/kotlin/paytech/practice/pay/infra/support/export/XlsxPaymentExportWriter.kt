package paytech.practice.pay.infra.support.export

import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.HorizontalAlignment
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.streaming.SXSSFWorkbook
import org.springframework.stereotype.Component
import paytech.practice.pay.application.port.outbound.PaymentExportWriter
import paytech.practice.pay.application.port.outbound.PaymentListEntry
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Apache POI로 [PaymentExportWriter]를 구현한다(`.xlsx`).
 *
 * - **`SXSSFWorkbook`(스트리밍)을 쓴다.** 일반 `XSSFWorkbook`은 모든 셀 객체를 힙에
 *   들고 있어서 행이 많아지면 그대로 메모리를 먹는다. SXSSF는 윈도우 밖 행을 임시
 *   파일로 흘려보내므로, 나중에 내보내기 상한을 올려도 여기가 먼저 터지지 않는다.
 *   **대신 `dispose()`로 임시 파일을 반드시 지운다** — 빠뜨리면 서버 디스크에 쌓인다.
 * - **금액은 문자열이 아니라 숫자로 쓴다.** 받는 사람이 엑셀에서 합계·정렬을 하기
 *   때문이다. 그래서 토큰 금액은 Minor Unit을 소수로 환산해 [BigDecimal]로 넣는다
 *   (JSON 응답이 문자열인 것과 다른 판단 — 그쪽은 JavaScript `Number`의 안전 정수
 *   범위가 문제였고, 여기서는 그 제약이 없다).
 * - **시각은 KST로 적는다.** API는 UTC로 주지만 이 파일은 사람이 바로 읽는 산출물이고,
 *   운영자가 "몇 시 결제인지"를 시차 계산 없이 봐야 한다. 열 제목에 시간대를 밝힌다.
 * - **가맹점 이름 열은 두 콘솔 모두에 넣는다.** 가맹점 콘솔에서는 자기 이름이라 정보가
 *   새지 않고, 파일을 받아 다른 자료와 합칠 때 오히려 필요하다.
 */
@Component
class XlsxPaymentExportWriter : PaymentExportWriter {
	override fun writeSpreadsheet(entries: List<PaymentListEntry>): ByteArray {
		val workbook = SXSSFWorkbook(SXSSF_ROW_ACCESS_WINDOW)
		try {
			val sheet = workbook.createSheet("결제 내역")
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
				row.createCell(column++).setCellValue(formatKst(entry.createdAt))
				row.createCell(column++).setCellValue(entry.merchantName)
				row.createCell(column++).setCellValue(entry.merchantOrderId.value)
				row.createCell(column++).setCellValue(entry.orderName)
				row.createCell(column++).setCellValue(entry.orderAmount.amount.toDouble())
				row.createCell(column++).setCellValue(entry.paymentAsset.code)
				row.createCell(column++).setCellValue(tokenAmount(entry).toDouble())
				row.createCell(column++).setCellValue(entry.network.code)
				row.createCell(column++).setCellValue(entry.status.name)
				row.createCell(column++).setCellValue(entry.failureReason?.name ?: "")
				row.createCell(column++).setCellValue(entry.transactionHash?.value ?: "")
				row.createCell(column++).setCellValue(entry.paidAt?.let { formatKst(it) } ?: "")
				row.createCell(column).setCellValue(entry.paymentId.value)
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

	/**
	 * Minor Unit 정수를 소수로 환산한다(`72992701`, 6 → `72.992701`). `Double` 연산을
	 * 거치지 않고 [BigDecimal]의 소수점 이동만 쓴다 — 통화 금액을 부동소수로 계산하지
	 * 않는다는 이 저장소의 규칙(`backend/CLAUDE.md`)과 같은 결이다.
	 */
	private fun tokenAmount(entry: PaymentListEntry): BigDecimal =
		BigDecimal(entry.paymentAmount.amountMinor).movePointLeft(entry.tokenDecimals)

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
				"생성 시각(KST)",
				"가맹점",
				"주문 번호",
				"주문명",
				"주문 금액(KRW)",
				"자산",
				"결제 금액",
				"네트워크",
				"상태",
				"실패 사유",
				"거래 Hash",
				"완료 시각(KST)",
				"결제 식별자",
			)
	}
}
