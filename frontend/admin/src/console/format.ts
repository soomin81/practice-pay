/**
 * 가맹점 OWNER의 초대 링크를 만든다.
 *
 * **이 링크는 이 콘솔이 아니라 가맹점 콘솔을 가리킨다.** 초대받은 사람은 가맹점 사용자라
 * 활성화도 가맹점 콘솔(`frontend/merchant`)에서 한다 — 그래서 merchant 앱처럼
 * `window.location.origin`을 쓸 수 없고 `VITE_MERCHANT_CONSOLE_URL`이 필요하다
 * (`.env.example`의 설명 참고). 이 슬라이스에서 가장 틀리기 쉬운 지점이라 테스트로 지킨다.
 *
 * MVP에는 초대 메일 발송이 없어 등록한 운영자가 이 링크를 직접 전달한다.
 */
export function merchantInvitationUrlFor(token: string): string {
	const base = import.meta.env.VITE_MERCHANT_CONSOLE_URL ?? 'http://localhost:5174'
	return `${base.replace(/\/$/, '')}/accept-invitation?token=${encodeURIComponent(token)}`
}

/**
 * 내부 운영자의 초대 링크를 만든다.
 *
 * **이 링크는 admin 콘솔 자신을 가리킨다** — 초대받은 사람이 내부 직원이라 활성화도 여기서
 * 하고, 요청도 `api-admin`의 `POST /admin/account-invitations/accept`로 간다.
 * [merchantInvitationUrlFor]와 **가리키는 곳이 다르다** — 둘을 바꿔 쓰면 화면상으로는
 * 멀쩡한데 상대가 열 수 없는 링크가 되므로, 두 함수를 나란히 두고 테스트로 지킨다.
 */
export function internalInvitationUrlFor(token: string): string {
	return `${window.location.origin}/accept-invitation?token=${encodeURIComponent(token)}`
}

/** ISO-8601(UTC) 시각을 콘솔 표시용 로컬 문자열로. 없으면 대시. */
export function formatDateTime(iso: string | null | undefined): string {
	if (!iso) return '—'
	const date = new Date(iso)
	if (Number.isNaN(date.getTime())) return iso
	return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

/**
 * KRW 주문 금액을 천 단위로 끊어 보여준다. 원 단위 정수라 `Number`로 다뤄도 안전하다
 * (토큰 금액과 다른 점 — 아래 [formatTokenAmount] 참고).
 */
export function formatKrw(amount: number): string {
	return `${amount.toLocaleString('ko-KR')}원`
}

/**
 * Minor Unit **문자열**을 사람이 읽는 소수로 바꾼다(`72992701`, 6 → `72.992701`).
 *
 * **`Number`로 변환하지 않는다** — 백엔드가 이 값을 문자열로 주는 이유가 토큰 금액이
 * JavaScript `Number`의 안전 정수 범위를 넘을 수 있어서다. 문자열 자리수만 잘라 쓴다
 * (`frontend/payment`의 같은 이름 함수와 같은 규칙).
 */
export function formatTokenAmount(minorUnits: string, decimals: number): string {
	const negative = minorUnits.startsWith('-')
	const digits = (negative ? minorUnits.slice(1) : minorUnits).padStart(decimals + 1, '0')
	const whole = digits.slice(0, digits.length - decimals)
	const fraction = decimals > 0 ? `.${digits.slice(digits.length - decimals)}` : ''
	return `${negative ? '-' : ''}${whole}${fraction}`
}
