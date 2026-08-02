package paytech.practice.pay.api.merchant.web

import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import java.time.Clock
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** `.xlsx`의 공식 MIME 타입. 틀리면 브라우저가 파일을 zip으로 저장하거나 열지 못한다. */
const val XLSX_CONTENT_TYPE: String = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/** 잘렸는지 알리는 커스텀 헤더. 본문이 바이너리라 응답 본문에 실을 자리가 없다. */
const val TRUNCATED_HEADER: String = "X-Export-Truncated"

private val FILE_DATE_FORMATTER: DateTimeFormatter =
	DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.of("Asia/Seoul"))

/**
 * 내보내기 결과를 다운로드 응답으로 감싼다 — **결제·정산 두 내보내기가 함께 쓴다**.
 *
 * 결과 타입이 아니라 바이트와 잘림 여부만 받는 이유는, 이 함수가 알아야 할 것이 그 둘뿐이라
 * 새 내보내기가 생길 때마다 시그니처를 늘리지 않기 위해서다. **api-admin의 같은 파일과 동일한 복제본이다** —
 * 앱은 서로를 모르는 독립 배포 단위라 코드를 공유하지 않는다(CsrfCookieFilter와 같은 규율).
 * **한쪽을 고치면 다른 쪽도 함께 본다.**
 *
 *
 * - **파일 이름은 ASCII로만 만든다**(`payments-20260801-153000.xlsx`). 한글을 쓰면
 *   `Content-Disposition`에 RFC 5987 인코딩(`filename*=UTF-8''...`)이 필요하고 브라우저마다
 *   처리가 갈린다 — 이름에 날짜만 있으면 충분해서 그 복잡도를 사지 않는다.
 * - **잘림은 [TRUNCATED_HEADER]로 알린다.** 본문이 스프레드시트 바이너리라 JSON 필드로
 *   전할 수 없다. 프론트는 이 헤더를 보고 "기간을 좁히라"고 안내한다 — 조용히 일부만
 *   담긴 파일을 받아가는 것이 이 기능에서 가장 위험한 실패다.
 * - **CORS에서 이 헤더가 보이려면 `exposedHeaders`에 있어야 한다**(SecurityConfig) —
 *   교차 출처에서는 기본적으로 몇 개의 표준 헤더만 JS에 노출된다.
 */
fun spreadsheetDownload(
	spreadsheet: ByteArray,
	truncated: Boolean,
	filePrefix: String,
	clock: Clock,
): ResponseEntity<ByteArray> {
	val fileName = "$filePrefix-${FILE_DATE_FORMATTER.format(clock.instant())}.xlsx"

	return ResponseEntity
		.ok()
		.header(HttpHeaders.CONTENT_TYPE, XLSX_CONTENT_TYPE)
		.header(
			HttpHeaders.CONTENT_DISPOSITION,
			ContentDisposition
				.attachment()
				.filename(fileName)
				.build()
				.toString(),
		).header(TRUNCATED_HEADER, truncated.toString())
		.contentType(MediaType.parseMediaType(XLSX_CONTENT_TYPE))
		.body(spreadsheet)
}
