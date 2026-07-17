package paytech.practice.pay.domain.shared

/**
 * 결제·정산에서 다루는 디지털 자산의 코드를 표현하는 Value Object다.
 *
 * DB의 `payment_asset_code`, `base_asset_code` 같은 컬럼(`VARCHAR(20)`)과 대응한다.
 * "Token Symbol만으로 자산을 판단하지 않는다"는 프로젝트 규칙에 따라, 실제 온체인
 * 검증은 이 코드가 아니라 Network와 Contract 주소 조합으로 별도 수행한다 — 이 타입은
 * 표시·조회용 코드일 뿐이다.
 *
 * @property code 자산 코드 문자열(예: `"USDC"`). 공백일 수 없다.
 */
@JvmInline
value class Asset(
	val code: String,
) {
	init {
		require(code.isNotBlank()) { "Asset 코드는 공백일 수 없습니다." }
	}

	companion object {
		/** MVP의 결제 자산(`docs/architecture/mvp-scope.md`). */
		val USDC = Asset("USDC")
	}
}
