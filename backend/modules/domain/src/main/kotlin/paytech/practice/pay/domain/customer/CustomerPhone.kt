package paytech.practice.pay.domain.customer

/**
 * 구매자 휴대전화 번호를 표현하는 Value Object다.
 *
 * **국내 CS에서 가장 빠른 연락 수단이면서 셋 중 민감도가 가장 높다**(ADR-008).
 *
 * MVP는 **국내 휴대전화만** 받는다(`01X-XXXX-XXXX`). 국제 번호를 허용하려면 국가번호와
 * 형식이 나라마다 달라 마스킹 규칙부터 갈리는데, 지금 그것을 요구하는 흐름이 없다.
 *
 * @property value 입력 그대로의 평문. 하이픈이 있을 수도 없을 수도 있다.
 */
@JvmInline
value class CustomerPhone(
	val value: String,
) {
	init {
		require(value.isNotBlank()) { "구매자 휴대전화 번호는 공백일 수 없습니다." }
		require(FORMAT.matches(value.filterNot { it == '-' })) {
			"구매자 휴대전화 번호 형식이 올바르지 않습니다(01X-XXXX-XXXX)."
		}
	}

	/**
	 * 숫자만 남긴 값 — Blind Index와 비교의 기준이다.
	 *
	 * **정규화하지 않으면 `01012345678`과 `010-1234-5678`이 다른 인덱스를 갖는다.** 같은
	 * 번호인데 검색에 걸리지 않는다.
	 */
	val normalized: String
		get() = value.filterNot { it == '-' }

	/**
	 * 가운데 자리를 가린다(`01012345678` → `010-****-5678`).
	 *
	 * **뒤 네 자리는 남긴다** — 고객이 전화로 "제 번호 뒷자리 5678입니다"라고 말했을 때
	 * 대조할 수 있어야 CS가 성립한다. 앞자리(`010`)는 사실상 전 국민이 같아 가릴 의미가 없다.
	 */
	val masked: String
		get() {
			val digits = normalized
			return "${digits.take(3)}-****-${digits.takeLast(4)}"
		}

	companion object {
		/** 하이픈을 뺀 숫자 기준. `010`뿐 아니라 `011`/`016`~`019`도 허용한다(옛 번호가 남아 있다). */
		private val FORMAT = Regex("^01[0-9]\\d{7,8}$")
	}
}
