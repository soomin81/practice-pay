import type { CheckoutSession } from '@/api/types'
import { formatKrw, formatTokenAmount } from '../format'

/**
 * "무엇을, 얼마에 결제하는가" — 화면에서 가장 먼저 읽혀야 하는 블록.
 *
 * 주문 금액(KRW)이 고객이 아는 값이고 토큰 금액은 그것을 환산한 결과라, 원화를 위에
 * 크게 두고 보낼 토큰 금액을 그 아래에 강조해서 둔다.
 *
 * **토큰 금액은 문자열로만 다룬다** — `formatTokenAmount`가 Minor Unit 문자열을
 * 자리수로 잘라 쓴다. `Number`로 바꾸면 안전 정수 범위를 넘을 때 조용히 정밀도를
 * 잃는다(`format.ts`의 주석).
 */
export function PaymentSummary({ session }: { session: CheckoutSession }) {
	const tokenAmount = formatTokenAmount(session.payment.amount, session.payment.tokenDecimals)

	return (
		<div className="space-y-4">
			<div className="space-y-1">
				<p className="text-sm text-muted-foreground">{session.order.orderName}</p>
				<p className="text-3xl font-semibold tracking-tight tabular-nums">
					{formatKrw(session.order.orderAmount)}
					<span className="ml-1 text-lg font-normal text-muted-foreground">
						{session.order.orderCurrency}
					</span>
				</p>
			</div>

			<div className="rounded-lg border bg-muted/50 p-4">
				<p className="text-xs text-muted-foreground">보낼 금액</p>
				<p className="mt-1 text-xl font-semibold tabular-nums">
					{tokenAmount} <span className="text-base font-medium">{session.payment.asset}</span>
				</p>
			</div>
		</div>
	)
}
