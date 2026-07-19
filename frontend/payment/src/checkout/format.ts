/**
 * 금액 표시 유틸.
 *
 * **토큰 금액은 절대 `Number`로 변환하지 않는다.** API가 Minor Unit을 문자열로 주는
 * 이유가 그것이다 — JavaScript `Number`의 안전 정수 범위(2^53-1)를 넘으면 조용히
 * 정밀도를 잃는다. 백엔드도 같은 종류의 사고를 겪었다(`BigInteger.toLong()`이 값을
 * 잘라 `TokenAmount`가 음수가 됐다). 여기서는 `BigInt`로만 다룬다.
 */

/**
 * Minor Unit 문자열을 사람이 읽는 소수 표기로 바꾼다.
 *
 * `formatTokenAmount("35893755", 6)` → `"35.893755"`
 *
 * 나눗셈을 쓰지 않고 문자열을 자리수로 자른다 — `Number`도 부동소수점도 거치지
 * 않으므로 자릿수가 아무리 커도 정확하다.
 */
export function formatTokenAmount(minorAmount: string, decimals: number): string {
	const negative = minorAmount.startsWith('-')
	const digits = (negative ? minorAmount.slice(1) : minorAmount).padStart(decimals + 1, '0')

	const whole = digits.slice(0, digits.length - decimals)
	const fraction = decimals > 0 ? digits.slice(digits.length - decimals) : ''

	const withGrouping = whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
	const trimmedFraction = fraction.replace(/0+$/, '')

	const body = trimmedFraction.length > 0 ? `${withGrouping}.${trimmedFraction}` : withGrouping
	return negative ? `-${body}` : body
}

/** KRW는 원 단위 정수라 `Number`로 안전하다(백엔드가 숫자로 내려주는 이유이기도 하다). */
export function formatKrw(amount: number): string {
	return new Intl.NumberFormat('ko-KR').format(amount)
}

/** 지갑 주소·해시를 화면에 줄여서 보여준다. 전체 값은 title 속성으로 남긴다. */
export function shortenHex(value: string, head = 6, tail = 4): string {
	if (value.length <= head + tail + 2) return value
	return `${value.slice(0, head)}…${value.slice(-tail)}`
}

/** 만료까지 남은 시간을 `mm:ss`로. 이미 지났으면 `null`. */
export function remainingTime(expiresAt: string, now: number = Date.now()): string | null {
	const remainingMs = new Date(expiresAt).getTime() - now
	if (remainingMs <= 0) return null

	const totalSeconds = Math.floor(remainingMs / 1000)
	const minutes = Math.floor(totalSeconds / 60)
	const seconds = totalSeconds % 60
	return `${minutes}:${String(seconds).padStart(2, '0')}`
}
