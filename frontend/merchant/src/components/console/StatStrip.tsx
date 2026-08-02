import type { ReactNode } from 'react'

export type Stat = {
	label: string
	value: ReactNode
	/** 음수·취소처럼 눈에 띄어야 하는 값(참고 디자인의 취소금액이 빨강이다). */
	tone?: 'default' | 'bad'
}

/**
 * 세로 구분선으로 나뉜 한 줄 통계 — 참고 디자인의 "거래건수 / 승인금액 / 취소금액 /
 * 수수료" 줄이다.
 *
 * **지금 있는 값만 넣는다.** 참고 디자인처럼 네 칸을 채우려면 목록 API가 합계를
 * 함께 내려줘야 하는데, 결제 목록은 아직 `totalCount`만 준다(정산 목록은
 * `totalNetAmount`도 준다). 빈 칸을 그럴듯한 숫자로 채우면 화면은 완성돼 보이지만
 * **그 숫자가 무엇을 뜻하는지 아무도 설명할 수 없게 된다** — 없는 값은 넣지 않는다.
 */
export function StatStrip({ stats }: { stats: Stat[] }) {
	return (
		<dl className="grid grid-cols-2 divide-y divide-x rounded-xl border bg-card sm:grid-cols-4 sm:divide-y-0">
			{stats.map((stat) => (
				<div key={stat.label} className="px-5 py-4">
					<dt className="text-xs text-muted-foreground">{stat.label}</dt>
					<dd
						className={`mono-cell mt-1.5 text-lg font-semibold ${stat.tone === 'bad' ? 'text-destructive' : 'text-foreground'}`}
					>
						{stat.value}
					</dd>
				</div>
			))}
		</dl>
	)
}
