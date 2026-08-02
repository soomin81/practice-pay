/**
 * 기간 빠른 선택 — 참고 디자인의 "오늘 / 7일 / 30일 / 전체" 칩이 쓰는 계산이다.
 *
 * **경계 처리는 날짜 입력과 같은 규칙을 쓴다**: 시작은 그날 00:00:00, 끝은
 * 23:59:59.999. 끝을 00:00으로 두면 "오늘까지"가 오늘 자정까지가 되어 오늘 결제가
 * 통째로 빠진다 — 각 페이지의 `endOfDayIso` 주석과 같은 이유다.
 */
export type RangePreset = 'today' | 'week' | 'month' | 'all'

export const RANGE_OPTIONS: readonly { value: RangePreset; label: string }[] = [
	{ value: 'today', label: '오늘' },
	{ value: 'week', label: '7일' },
	{ value: 'month', label: '30일' },
	{ value: 'all', label: '전체' },
]

const DAYS_BACK: Record<Exclude<RangePreset, 'all'>, number> = { today: 0, week: 6, month: 29 }

/** 결제 목록용(생성 시각 기준, ISO-8601 순간). `all`은 두 값을 비워 필터를 없앤다. */
export function rangeFilters(preset: RangePreset, now: Date = new Date()): { from?: string; to?: string } {
	if (preset === 'all') return { from: undefined, to: undefined }

	const start = new Date(now)
	start.setDate(start.getDate() - DAYS_BACK[preset])
	start.setHours(0, 0, 0, 0)
	const end = new Date(now)
	end.setHours(23, 59, 59, 999)

	return { from: start.toISOString(), to: end.toISOString() }
}

/** 정산 목록용(정산 예정일 기준 `YYYY-MM-DD`) — 날짜라 시간대 경계 문제가 없다. */
export function rangeDates(preset: RangePreset, now: Date = new Date()): { eligibleFrom?: string; eligibleTo?: string } {
	if (preset === 'all') return { eligibleFrom: undefined, eligibleTo: undefined }

	const start = new Date(now)
	start.setDate(start.getDate() - DAYS_BACK[preset])

	return { eligibleFrom: localDate(start), eligibleTo: localDate(now) }
}

function localDate(date: Date): string {
	const month = String(date.getMonth() + 1).padStart(2, '0')
	const day = String(date.getDate()).padStart(2, '0')
	return `${date.getFullYear()}-${month}-${day}`
}
