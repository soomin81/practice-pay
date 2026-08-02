/**
 * 초대 Token으로 활성화 링크를 만든다.
 *
 * **이 형식의 유일한 출처다** — 최초 발급과 재발송이 같은 링크를 만들어야 하고,
 * 초대 수락 경로(`/accept-invitation`)가 바뀔 때 여러 곳이 갈리면 한쪽 링크가 조용히
 * 죽는다. MVP에 초대 메일 발송이 없어 발급한 사람이 이 링크를 직접 전달한다.
 */
export function invitationUrlFor(token: string): string {
	return `${window.location.origin}/accept-invitation?token=${encodeURIComponent(token)}`
}

/**
 * `INVITED` 사용자의 초대 상태를 한 줄로 요약한다.
 *
 * 만료는 수락 시점에만 검사되고 상태는 `PENDING`으로 남으므로(만료 배치가 없다),
 * 화면이 만료 시각을 현재와 비교해 판단한다 — 서버가 "만료됨"을 알려주지 않는다.
 */
export function describeInvitation(expiresAt: string | null | undefined): { text: string; expired: boolean } {
	if (!expiresAt) return { text: '유효한 초대 없음', expired: true }
	const expiry = new Date(expiresAt)
	if (Number.isNaN(expiry.getTime())) return { text: '유효한 초대 없음', expired: true }
	if (expiry.getTime() <= Date.now()) return { text: '초대 만료됨', expired: true }
	return { text: `${formatDateTime(expiresAt)}까지 유효`, expired: false }
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

/**
 * 정산 보류 사유 코드를 사람이 읽는 문장으로 바꾼다.
 *
 * **가맹점에게도 보여준다** — 자기 돈이 멈춘 이유를 모르면 결국 문의로 돌아온다. 다만 이
 * 콘솔에는 푸는 수단이 없다(보류·해제·취소는 PG 내부 운영자만 한다).
 *
 * 모르는 코드는 그대로 보여준다 — 서버가 사유를 늘렸을 때 빈칸이 되면 "이유 없이 막혔다"로
 * 읽힌다(`labelFor`가 상태 코드에 쓰는 것과 같은 규칙). **admin 쪽에 같은 함수가 있다 —
 * 한쪽을 고치면 다른 쪽도 함께 본다.**
 */
export function holdReasonLabel(code: string): string {
	return HOLD_REASONS[code] ?? code
}

const HOLD_REASONS: Record<string, string> = {
	TRANSACTION_REORGED: '확정 이후 입금이 체인에서 사라짐',
}
