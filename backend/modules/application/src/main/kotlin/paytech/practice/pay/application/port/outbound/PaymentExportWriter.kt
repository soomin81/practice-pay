package paytech.practice.pay.application.port.outbound

/**
 * 결제 내역을 스프레드시트 바이트로 바꾸는 Outbound Port다.
 *
 * **왜 Port인가**: 실제 구현은 Apache POI라는 외부 라이브러리를 쓴다.
 * `modules:application`은 인프라 라이브러리에 의존할 수 없으므로(`ApplicationPurityTest`)
 * 그 경계를 Port로 끊는다. 앱(inbound Adapter)에 두지 않는 것도 규칙이다 — 앱에는
 * outbound Port 구현을 두지 않는다(`HexagonalLayerTest`). 구현은
 * `modules:infra-support`의 `XlsxPaymentExportWriter`다.
 *
 * **JSON 직렬화와 달리 Port를 거치는 이유**도 여기 있다. JSON은 프레임워크가 응답
 * 표현으로 알아서 처리하지만, 스프레드시트는 우리가 라이브러리를 직접 불러 만드는
 * 산출물이라 그 의존성이 어느 계층에 있는지가 드러나야 한다.
 */
fun interface PaymentExportWriter {
	/**
	 * [entries]를 스프레드시트 한 장으로 만들어 바이트로 돌려준다.
	 *
	 * **바이트 배열을 통째로 돌려주는 것은 MVP 단순화다** — 내보내기 행 수에 상한이 있어
	 * (`PaymentExportPolicy.MAX_EXPORT_ROWS`) 메모리에 담을 수 있는 크기가 보장된다.
	 * 상한을 크게 올리게 되면 `OutputStream`으로 흘려보내는 형태로 바꾼다.
	 */
	fun writeSpreadsheet(entries: List<PaymentListEntry>): ByteArray
}
