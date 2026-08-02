package paytech.practice.pay.application.settlement

/**
 * 정산 채권 내보내기 결과다.
 *
 * @property spreadsheet 스프레드시트 바이트. inbound Adapter가 그대로 응답 본문으로 쓴다.
 * @property rowCount 실제로 담긴 행 수.
 * @property truncated 조건에 맞는 채권이 [SettlementExportPolicy.MAX_EXPORT_ROWS]를 넘어
 * **일부만 담겼는지**. `true`면 화면이 사용자에게 알려야 한다 — 조용히 잘린 파일을
 * 받아가는 것이 이 기능에서 가장 위험한 실패다.
 */
data class ExportSettlementReceivablesResult(
	val spreadsheet: ByteArray,
	val rowCount: Int,
	val truncated: Boolean,
) {
	// ByteArray를 가진 data class는 equals/hashCode가 참조 비교라 경고가 난다. 이 타입을
	// 값으로 비교할 일이 없어(응답으로 흘려보낼 뿐) 내용 비교로 직접 구현해 둔다.
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is ExportSettlementReceivablesResult) return false
		return spreadsheet.contentEquals(other.spreadsheet) && rowCount == other.rowCount && truncated == other.truncated
	}

	override fun hashCode(): Int = 31 * (31 * spreadsheet.contentHashCode() + rowCount) + truncated.hashCode()
}
