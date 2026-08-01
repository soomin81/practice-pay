import { Link } from 'react-router-dom'
import type { PaymentSummary } from '@/api/types'
import { formatDateTime, formatKrw, formatTokenAmount } from '@/console/format'
import { Badge } from '@/components/ui/badge'

/**
 * 결제 내역 표. **토큰 금액은 `formatTokenAmount`로만 다룬다** — 백엔드가 Minor Unit을
 * 문자열로 주는 이유가 `Number` 변환에서 값이 조용히 달라질 수 있어서다.
 */
export function PaymentTable({ payments }: { payments: PaymentSummary[] }) {
	if (payments.length === 0) {
		return <p className="text-sm text-muted-foreground">조건에 맞는 결제가 없습니다.</p>
	}

	return (
		<div className="overflow-x-auto">
			<table className="w-full text-sm">
				<thead>
					<tr className="border-b text-left text-muted-foreground">
						<th className="py-2 pr-3 font-medium">생성 시각</th>
						<th className="py-2 pr-3 font-medium">가맹점</th>
						<th className="py-2 pr-3 font-medium">주문</th>
						<th className="py-2 pr-3 font-medium">주문 금액</th>
						<th className="py-2 pr-3 font-medium">결제 금액</th>
						<th className="py-2 pr-3 font-medium">상태</th>
						<th className="py-2 font-medium">거래 Hash</th>
					</tr>
				</thead>
				<tbody>
					{payments.map((payment) => (
						<tr key={payment.paymentId} className="border-b last:border-0">
							<td className="py-2 pr-3 whitespace-nowrap">{formatDateTime(payment.createdAt)}</td>
							<td className="py-2 pr-3">{payment.merchantName}</td>
							<td className="py-2 pr-3">
								{/* 주문명을 상세로 가는 입구로 쓴다(가맹점 목록이 이름을 링크로 쓰는 것과 같은 방식). */}
								<Link className="underline underline-offset-2" to={`/payments/${payment.paymentId}`}>
									{payment.orderName}
								</Link>
								<div className="text-xs text-muted-foreground">{payment.merchantOrderId}</div>
							</td>
							<td className="py-2 pr-3 whitespace-nowrap">{formatKrw(payment.orderAmount)}</td>
							<td className="py-2 pr-3 whitespace-nowrap">
								{formatTokenAmount(payment.paymentAmount, payment.tokenDecimals)} {payment.paymentAsset}
							</td>
							<td className="py-2 pr-3">
								<PaymentStatusBadge status={payment.status} failureReason={payment.failureReason} />
							</td>
							{/* 전체 값은 title로 남긴다 — 운영자가 온체인 탐색기와 대조해야 한다. */}
							<td className="py-2 font-mono text-xs" title={payment.transactionHash ?? undefined}>
								{payment.transactionHash ? shortenHex(payment.transactionHash) : '—'}
							</td>
						</tr>
					))}
				</tbody>
			</table>
		</div>
	)
}

/**
 * 상태 배지. **실패 사유는 코드 그대로 보여준다** — 내부 운영자용 화면이라 원인 코드가
 * 오히려 정확하다(고객 대면 체크아웃에서는 문구로 번역한다).
 */
function PaymentStatusBadge({ status, failureReason }: { status: string; failureReason?: string | null }) {
	const variant = status === 'SUCCEEDED' ? 'default' : status === 'FAILED' ? 'destructive' : 'secondary'
	return (
		<div className="flex flex-col gap-0.5">
			<Badge variant={variant}>{status}</Badge>
			{failureReason && <span className="text-xs text-muted-foreground">{failureReason}</span>}
		</div>
	)
}

function shortenHex(value: string): string {
	return value.length <= 14 ? value : `${value.slice(0, 8)}…${value.slice(-4)}`
}
