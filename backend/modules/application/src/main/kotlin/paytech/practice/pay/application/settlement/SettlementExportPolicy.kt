package paytech.practice.pay.application.settlement

/**
 * 정산 채권 내보내기 정책이다. [ExportSettlementReceivablesUseCase]와
 * [ExportMerchantSettlementReceivablesUseCase]가 공유한다.
 *
 * **결제 쪽 상수를 그대로 쓰지 않고 따로 둔다** — 값은 지금 같지만 두 내보내기가 같아야 할
 * 이유가 없다. 정산은 결제보다 행이 적고(성공한 결제에만 채권이 생긴다) 열은 더 많다.
 * 한쪽을 조정할 때 다른 쪽이 딸려 오면 그게 사고다(`SettlementListPaging`을
 * `PaymentListPaging`과 나눠 둔 것과 같은 판단).
 */
internal object SettlementExportPolicy {
	/**
	 * 한 번에 내보내는 최대 행 수.
	 *
	 * 상한이 곧 메모리 상한이다 — 전체를 메모리에 담아 바이트로 만들기 때문이다
	 * (`SettlementExportWriter`의 KDoc).
	 *
	 * 넘치는 만큼은 **조용히 잘린다** — 그 사실을 [ExportSettlementReceivablesResult.truncated]로
	 * 알려주고 화면이 "기간을 좁히라"고 안내한다. 조용히 일부만 담긴 파일을 받아가는 것이
	 * 이 기능에서 가장 위험한 실패다.
	 */
	const val MAX_EXPORT_ROWS: Int = 10_000
}
