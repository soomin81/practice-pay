/** ISO-8601(UTC) 시각을 콘솔 표시용 로컬 문자열로. 없으면 대시. */
export function formatDateTime(iso: string | null | undefined): string {
	if (!iso) return '—'
	const date = new Date(iso)
	if (Number.isNaN(date.getTime())) return iso
	return new Intl.DateTimeFormat('ko-KR', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}
