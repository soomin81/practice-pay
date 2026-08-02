package paytech.practice.pay.application.port.outbound

/**
 * 정산 채권을 스프레드시트 바이트로 바꾸는 Outbound Port다.
 *
 * `PaymentExportWriter`와 같은 이유로 Port다 — 구현이 Apache POI를 쓰는데
 * `modules:application`은 인프라 라이브러리에 의존할 수 없다(`ApplicationPurityTest`).
 * 구현은 `modules:infra-support`의 `XlsxSettlementExportWriter`다.
 *
 * **결제 쪽 Port와 합치지 않았다.** 두 산출물은 열 구성이 완전히 다르고(정산은 수수료·
 * 순액·정산 예정일이 중심이다), 하나로 묶으려면 "행을 셀 목록으로 바꾸는" 중간 추상을
 * 하나 더 만들어야 한다 — 그 추상은 **열 이름과 서식이라는 이 기능의 실질**을 감추기만
 * 하고 재사용할 것은 시트를 여닫는 몇 줄뿐이다.
 */
fun interface SettlementExportWriter {
	/**
	 * [entries]를 스프레드시트 한 장으로 만들어 바이트로 돌려준다.
	 *
	 * 바이트 배열을 통째로 돌려주는 것이 안전한 이유는 행 수에 상한이 있기 때문이다
	 * (`SettlementExportPolicy.MAX_EXPORT_ROWS`) — `PaymentExportWriter`와 같은 판단.
	 */
	fun writeSpreadsheet(entries: List<SettlementReceivableListEntry>): ByteArray
}
