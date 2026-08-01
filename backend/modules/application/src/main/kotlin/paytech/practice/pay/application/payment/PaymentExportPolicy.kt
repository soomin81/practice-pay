package paytech.practice.pay.application.payment

/**
 * 결제 내역 내보내기 정책이다. [ExportPaymentsUseCase]와 [ExportMerchantPaymentsUseCase]가
 * 공유한다(화면 페이징의 [PaymentListPaging]과 같은 자리·같은 이유).
 */
internal object PaymentExportPolicy {
	/**
	 * 한 번에 내보내는 최대 행 수.
	 *
	 * **화면 페이징 상한(`PaymentListPaging.MAX_PAGE_SIZE` = 200)과 일부러 다르다** —
	 * 내보내기는 "한 화면에 그릴 양"이 아니라 "한 파일로 받아갈 양"이라 요구 조건이 다르다.
	 * 그렇다고 무제한으로 두지는 않는다: 전체를 메모리에 담아 바이트로 만들기 때문에
	 * (`PaymentExportWriter`의 KDoc 참고) 상한이 곧 메모리 상한이다.
	 *
	 * 넘치는 만큼은 **조용히 잘린다** — 그 사실을 호출부가 알 수 있도록
	 * [ExportPaymentsResult.truncated]로 알려주고, 화면이 "기간을 좁히라"고 안내한다.
	 * 조용히 일부만 담긴 파일을 받아가는 것이 이 기능에서 가장 위험한 실패다.
	 */
	const val MAX_EXPORT_ROWS: Int = 10_000
}
