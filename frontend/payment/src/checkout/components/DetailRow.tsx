import type { ReactNode } from 'react'

/**
 * 라벨-값 한 줄. 결제 상세와 확인 화면이 같은 모양을 쓴다.
 *
 * `<dl>` 안에서 쓰는 것을 전제로 `<dt>`/`<dd>`를 낸다 — 라벨과 값의 관계가 마크업에
 * 남아야 스크린리더가 짝을 읽어준다.
 */
export function DetailRow({
	label,
	children,
	/** 지갑 주소·해시처럼 줄여서 보여주는 값의 전체 문자열. hover하면 보인다. */
	fullValue,
	mono,
}: {
	label: string
	children: ReactNode
	fullValue?: string
	mono?: boolean
}) {
	return (
		<div className="flex items-baseline justify-between gap-4 py-2">
			<dt className="shrink-0 text-sm text-muted-foreground">{label}</dt>
			<dd
				className={mono ? 'text-right text-sm font-mono' : 'text-right text-sm'}
				title={fullValue}
			>
				{children}
			</dd>
		</div>
	)
}
