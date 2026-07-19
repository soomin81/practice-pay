import type { ReactNode } from 'react'
import { AlertTriangle, Ban, CheckCircle2, Clock, Loader2, SearchX } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { cn } from '@/lib/utils'

/**
 * 결과·대기 상태를 보여주는 화면.
 *
 * 종료 상태(완료/만료/취소/실패)와 로딩·조회 실패가 전부 같은 모양이라 하나로 묶었다.
 * **어떤 tone을 쓸지는 이 컴포넌트가 정하지 않는다** — 호출부가 서버에서 받은 상태를
 * 보고 넘긴다(`docs/architecture/checkout-api.md` 6절: 프론트가 다음 상태를 스스로
 * 추론하지 않는다).
 */
export type StatusTone = 'success' | 'error' | 'expired' | 'cancelled' | 'pending' | 'notFound'

const TONE = {
	success: { Icon: CheckCircle2, className: 'text-emerald-600 dark:text-emerald-500' },
	error: { Icon: AlertTriangle, className: 'text-destructive' },
	expired: { Icon: Clock, className: 'text-amber-600 dark:text-amber-500' },
	cancelled: { Icon: Ban, className: 'text-muted-foreground' },
	pending: { Icon: Loader2, className: 'text-muted-foreground animate-spin' },
	notFound: { Icon: SearchX, className: 'text-muted-foreground' },
} as const satisfies Record<StatusTone, { Icon: typeof CheckCircle2; className: string }>

export function StatusScreen({
	tone,
	title,
	description,
	children,
}: {
	tone: StatusTone
	title: string
	/** 고객에게 보여줄 안내 문구. 오류 코드를 그대로 넣지 않는다(계약 4.2). */
	description?: ReactNode
	/** 버튼이나 보조 정보 등 본문 아래에 붙일 것. */
	children?: ReactNode
}) {
	const { Icon, className } = TONE[tone]

	return (
		<Card>
			<CardContent className="flex flex-col items-center gap-3 py-10 text-center">
				<Icon className={cn('size-10', className)} aria-hidden />
				<h2 className="text-lg font-semibold tracking-tight">{title}</h2>
				{description && <p className="text-sm text-muted-foreground">{description}</p>}
				{children && <div className="mt-2 flex w-full flex-col items-center gap-2">{children}</div>}
			</CardContent>
		</Card>
	)
}
