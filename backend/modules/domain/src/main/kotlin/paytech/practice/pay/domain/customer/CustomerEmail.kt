package paytech.practice.pay.domain.customer

/**
 * 구매자 이메일을 표현하는 Value Object다.
 *
 * **환불·분쟁에서 유일한 연락 수단**이라 이 시스템이 구매자 정보를 받는 가장 큰 이유다
 * (ADR-008) — 온체인 전송은 되돌릴 수 없어서 사람에게 연락하는 것 말고 길이 없다.
 *
 * `domain.identity`의 `Email`(계정 로그인용)과 **합치지 않았다.** 저쪽은 계정 식별자라
 * 유일해야 하고 초대·로그인 흐름에 묶여 있지만, 이쪽은 결제 1건에 붙는 연락처라 같은
 * 값이 여러 번 나타나는 것이 정상이다 — 규칙이 갈릴 자리다.
 *
 * @property value 이메일 평문. [normalized]가 Blind Index와 비교의 기준이다.
 */
@JvmInline
value class CustomerEmail(
	val value: String,
) {
	init {
		require(value.isNotBlank()) { "구매자 이메일은 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) { "구매자 이메일은 ${MAX_LENGTH}자를 초과할 수 없습니다." }
		require(FORMAT.matches(value)) { "구매자 이메일 형식이 올바르지 않습니다." }
	}

	/**
	 * Blind Index를 만들 때 쓰는 정규화된 값 — 소문자로 낮추고 앞뒤 공백을 지운다.
	 *
	 * **정규화하지 않으면 `A@b.com`과 `a@b.com`이 다른 인덱스를 갖는다** — 같은 사람인데
	 * 검색에 걸리지 않는다. 저장하는 평문은 사용자가 입력한 그대로 두고, 인덱스만 정규화한다.
	 */
	val normalized: String
		get() = value.trim().lowercase()

	/**
	 * 로컬 파트 앞 두 글자만 남긴다(`abcdef@example.com` → `ab***@example.com`).
	 *
	 * **도메인은 가리지 않는다** — 운영자가 "회사 메일인지 개인 메일인지" 정도는 알아야 문의를
	 * 판단할 수 있고, 도메인만으로는 개인을 특정할 수 없다.
	 *
	 * 로컬 파트가 두 글자 이하면 첫 글자만 남긴다(`ab@x.com` → `a***@x.com`) — 두 글자를 다
	 * 남기면 가린 것이 없다.
	 */
	val masked: String
		get() {
			val at = value.indexOf('@')
			val local = value.take(at)
			val domain = value.substring(at)
			val visible = if (local.length <= 2) 1 else 2
			return local.take(visible) + "***" + domain
		}

	companion object {
		/** `payment_customer.customer_email_masked` 컬럼의 최대 길이(`VARCHAR(255)`)와 맞춘다. */
		private const val MAX_LENGTH = 255

		/**
		 * **엄밀한 RFC 5322 검증을 하지 않는다** — 그 정규식은 읽을 수 없을 만큼 복잡한데,
		 * 통과시켜도 실제로 배달되는지는 여전히 모른다. 여기서 잡으려는 것은 오타 수준의
		 * 잘못된 입력이고, 진짜 검증은 그 주소로 메일이 가는지다(그건 이 범위 밖이다).
		 */
		private val FORMAT = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
	}
}
