import type { ReactNode } from 'react'

/**
 * 표의 겉모양을 한곳에 모은다 — 넉넉한 행 높이, 얇은 구분선, 좁은 화면에서 가로 스크롤.
 *
 * 콘솔의 표가 여덟 개쯤 되는데 그동안 각자 `<table className="...">`을 적고 있어서,
 * 행 높이와 구분선이 화면마다 조금씩 달랐다. 참고 디자인은 표가 화면의 주인공이라
 * 그 차이가 그대로 드러난다.
 */
export function DataTable({ head, children }: { head: ReactNode; children: ReactNode }) {
	return (
		<div className="-mx-5 overflow-x-auto">
			<table className="w-full min-w-max border-collapse text-sm">
				<thead>
					<tr className="border-y bg-muted/40 text-xs text-muted-foreground">{head}</tr>
				</thead>
				<tbody className="divide-y">{children}</tbody>
			</table>
		</div>
	)
}

/** 표 머리 칸. [align]이 `right`면 금액처럼 우측 정렬한다. */
export function Th({ children, align = 'left' }: { children: ReactNode; align?: 'left' | 'right' }) {
	return (
		<th className={`px-5 py-2.5 font-medium whitespace-nowrap ${align === 'right' ? 'text-right' : 'text-left'}`}>
			{children}
		</th>
	)
}

/**
 * 표 본문 칸.
 *
 * - `variant="mono"` — 식별자·일시처럼 값 전체가 기계적인 칸
 * - `variant="amount"` — 금액. 등폭 + **우측 정렬**이라 자릿수가 세로로 맞는다
 */
export function Td({
	children,
	variant = 'text',
	className = '',
}: {
	children: ReactNode
	variant?: 'text' | 'mono' | 'amount'
	className?: string
}) {
	const variantClass =
		variant === 'amount' ? 'mono-cell text-right' : variant === 'mono' ? 'mono-cell text-muted-foreground' : ''
	return <td className={`px-5 py-3.5 whitespace-nowrap ${variantClass} ${className}`}>{children}</td>
}

/** 결과가 없을 때 표 안에 그대로 보여준다 — 표를 지우고 문구만 남기면 열 이름이 사라진다. */
export function EmptyRow({ colSpan, children }: { colSpan: number; children: ReactNode }) {
	return (
		<tr>
			<td colSpan={colSpan} className="px-5 py-10 text-center text-sm text-muted-foreground">
				{children}
			</td>
		</tr>
	)
}
