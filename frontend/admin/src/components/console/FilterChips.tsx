/**
 * 참고 디자인의 "오늘 / 7일 / 30일 / 전체" 세그먼트다 — 자주 쓰는 기간을 한 번에 고른다.
 *
 * 날짜 입력 두 칸을 없애는 것이 아니라 **함께 둔다**: 칩은 흔한 질문("최근 일주일")에
 * 답하고, 날짜 입력은 그 밖의 구간을 담당한다. 칩을 누르면 날짜 입력의 값도 함께
 * 바뀌므로 지금 무엇이 걸려 있는지 두 곳에서 같은 답을 얻는다.
 */
export function FilterChips<T extends string>({
	options,
	value,
	onChange,
	ariaLabel,
}: {
	options: readonly { value: T; label: string }[]
	value: T | null
	onChange: (value: T) => void
	ariaLabel: string
}) {
	return (
		<div role="group" aria-label={ariaLabel} className="flex flex-wrap items-center gap-1.5">
			{options.map((option) => {
				const active = option.value === value
				return (
					<button
						key={option.value}
						type="button"
						aria-pressed={active}
						onClick={() => onChange(option.value)}
						className={`rounded-lg border px-3 py-1.5 text-sm transition-colors ${
							active
								? 'border-transparent bg-muted font-medium text-foreground'
								: 'bg-card text-muted-foreground hover:text-foreground'
						}`}
					>
						{option.label}
					</button>
				)
			})}
		</div>
	)
}
