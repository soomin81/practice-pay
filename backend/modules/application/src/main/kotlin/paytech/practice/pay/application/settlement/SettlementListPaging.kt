package paytech.practice.pay.application.settlement

/**
 * 정산 채권 조회의 페이징 정책이다.
 *
 * 값은 결제 목록(`PaymentListPaging`)과 같지만 **그쪽을 가져다 쓰지 않고 여기 따로 둔다** —
 * 이름이 `PaymentListPaging`인 상수를 정산 슬라이스가 참조하면 읽는 사람이 두 화면이 같은
 * 정책을 *공유해야 한다*고 오해한다. 두 화면의 페이지 크기가 갈릴 이유가 생기면 여기만
 * 고치면 된다. 지금 공유할 근거가 없어서 공유하지 않는 것이지, 중복을 못 본 것이 아니다.
 */
internal object SettlementListPaging {
	const val DEFAULT_PAGE_SIZE: Int = 50

	/**
	 * 한 페이지 최대 건수. 클라이언트가 페이지 크기를 무제한으로 정하게 두면 조회 하나로
	 * DB와 응답 직렬화를 모두 밀어버릴 수 있다.
	 */
	const val MAX_PAGE_SIZE: Int = 200

	fun normalizePage(page: Int): Int = page.coerceAtLeast(0)

	fun normalizeSize(size: Int): Int = size.coerceIn(1, MAX_PAGE_SIZE)
}
