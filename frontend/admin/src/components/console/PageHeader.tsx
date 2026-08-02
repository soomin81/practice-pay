import type { ReactNode } from 'react'

/**
 * 본문 맨 위의 페이지 제목 줄 — 제목, 한 줄 설명, 우측 슬롯.
 *
 * 설명을 **선택이 아니라 기본**으로 둔 것은 참고 디자인을 따른 것이다("승인·취소·환불
 * 거래를 조회하고…"). 이 콘솔의 화면들은 이름만으로는 무엇을 하는 곳인지 알기 어려운
 * 것이 많아서(정산 채권, Webhook 설정) 한 줄 설명이 실제로 값을 한다.
 */
export function PageHeader({ title, description, action }: { title: string; description?: string; action?: ReactNode }) {
	return (
		<div className="flex flex-wrap items-start justify-between gap-3 pb-6">
			<div className="min-w-0">
				<h1 className="font-heading text-xl font-semibold tracking-tight">{title}</h1>
				{description ? <p className="mt-1 text-sm text-muted-foreground">{description}</p> : null}
			</div>
			{action ? <div className="flex shrink-0 items-center gap-2">{action}</div> : null}
		</div>
	)
}

/**
 * 참고 디자인 우상단의 "● 2026.08.02.14:30 KST" 표시.
 *
 * **지금 보고 있는 값이 언제 것인지**를 알려준다 — 결제·정산 화면은 초 단위로 값이
 * 바뀌는데, 그걸 모르면 오래된 화면을 보며 판단하게 된다.
 */
export function LiveStamp({ at }: { at: Date }) {
	return (
		<span className="inline-flex items-center gap-2 rounded-full border bg-card px-3 py-1.5 text-xs text-muted-foreground">
			<span className="size-1.5 rounded-full bg-state-ok-fg" aria-hidden />
			<span className="mono-cell">{formatStamp(at)} KST</span>
		</span>
	)
}

function formatStamp(at: Date): string {
	// KST 고정 — 이 콘솔을 보는 사람이 한국에 있다는 전제이고, 화면의 다른 시각
	// 표기(format.ts)도 같은 전제를 쓴다.
	const parts = new Intl.DateTimeFormat('ko-KR', {
		timeZone: 'Asia/Seoul',
		year: 'numeric',
		month: '2-digit',
		day: '2-digit',
		hour: '2-digit',
		minute: '2-digit',
		hour12: false,
	}).formatToParts(at)
	const get = (type: string) => parts.find((part) => part.type === type)?.value ?? ''
	return `${get('year')}.${get('month')}.${get('day')}.${get('hour')}:${get('minute')}`
}
