package paytech.practice.pay.application.payment

/**
 * 결제 내역 조회의 페이징 정책이다. [ListPaymentsUseCase]와 [ListMerchantPaymentsUseCase]가
 * 같은 규칙을 써야 해서 한곳에 모았다 — Use Case가 다른 Use Case를 호출하지 않는다는 규칙
 * (`ApplicationPurityTest`) 때문에 위임 대신 공용 정책으로 뺐다(`LoginLockoutPolicy` 선례).
 */
internal object PaymentListPaging {
	const val DEFAULT_PAGE_SIZE: Int = 50

	/**
	 * 한 페이지 최대 건수. `docs/`에 값이 없어 고정한 MVP 상수다 — 호출부가 `size`를 아무리
	 * 크게 보내도 여기서 잘린다. **페이지 크기를 클라이언트가 무제한으로 정하게 두면 조회
	 * 하나로 DB와 응답 직렬화를 모두 밀어버릴 수 있다.**
	 *
	 * 엑셀 다운로드는 이 상한을 그대로 쓰지 않고 별도 경로로 스트리밍할 예정이다(다음
	 * 슬라이스) — 화면 페이징과 내보내기는 요구 조건이 다르다.
	 */
	const val MAX_PAGE_SIZE: Int = 200

	fun normalizePage(page: Int): Int = page.coerceAtLeast(0)

	fun normalizeSize(size: Int): Int = size.coerceIn(1, MAX_PAGE_SIZE)
}
