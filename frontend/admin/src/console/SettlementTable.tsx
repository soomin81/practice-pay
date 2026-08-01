import type { SettlementReceivableSummary } from '@/api/types'
import { formatKrw } from '@/console/format'
import { Badge } from '@/components/ui/badge'

/**
 * 정산 채권 표.
 *
 * **금액은 전부 KRW 원 단위 정수라 `Number`로 다뤄도 안전하다** — 토큰 금액(Minor Unit
 * 문자열)과 다른 점이다.
 */
export function SettlementTable({ rows }: { rows: SettlementReceivableSummary[] }) {
	if (rows.length === 0) {
		return <p className="text-sm text-muted-foreground">조건에 맞는 정산 채권이 없습니다.</p>
	}

	return (
		<div className="overflow-x-auto">
			<table className="w-full text-sm">
				<thead>
					<tr className="border-b text-left text-muted-foreground">
						<th className="py-2 pr-3 font-medium">정산 예정일</th>
						<th className="py-2 pr-3 font-medium">가맹점</th>
						<th className="py-2 pr-3 font-medium">주문</th>
						<th className="py-2 pr-3 font-medium text-right">정산 기준</th>
						<th className="py-2 pr-3 font-medium text-right">수수료</th>
						<th className="py-2 pr-3 font-medium text-right">정산 예정</th>
						<th className="py-2 pr-3 font-medium text-right">환전 손익</th>
						<th className="py-2 font-medium">상태</th>
					</tr>
				</thead>
				<tbody>
					{rows.map((row) => (
						<tr key={row.settlementReceivableId} className="border-b last:border-0">
							<td className="py-2 pr-3 whitespace-nowrap">{row.eligibleDate}</td>
							<td className="py-2 pr-3">{row.merchantName}</td>
							<td className="py-2 pr-3">
								<div>{row.merchantOrderId}</div>
								<div className="font-mono text-xs text-muted-foreground">{row.paymentId}</div>
							</td>
							<td className="py-2 pr-3 text-right whitespace-nowrap">{formatKrw(row.grossAmount)}</td>
							<td className="py-2 pr-3 text-right whitespace-nowrap text-muted-foreground">
								−{formatKrw(row.feeAmount)}
								<div className="text-xs">{formatFeeRate(row.feeRate)}</div>
							</td>
							<td className="py-2 pr-3 text-right font-medium whitespace-nowrap">{formatKrw(row.netAmount)}</td>
							{/* 환전 확보액과 정산 기준 금액의 차이 = PG 마진. READY 전에는 값이 없다. */}
							<td className="py-2 pr-3 text-right whitespace-nowrap text-muted-foreground">
								{row.exchangeProfitLossAmount === null || row.exchangeProfitLossAmount === undefined
									? '—'
									: formatSignedKrw(row.exchangeProfitLossAmount)}
							</td>
							<td className="py-2">
								<Badge variant={row.status === 'READY' ? 'default' : 'secondary'}>{row.status}</Badge>
							</td>
						</tr>
					))}
				</tbody>
			</table>
		</div>
	)
}

/** `0.015` → `1.5%`. 서버가 `BigDecimal`을 숫자로 주므로 그대로 계산한다. */
function formatFeeRate(rate: number): string {
	return `${(rate * 100).toFixed(2).replace(/\.?0+$/, '')}%`
}

function formatSignedKrw(amount: number): string {
	return amount >= 0 ? `+${formatKrw(amount)}` : `−${formatKrw(Math.abs(amount))}`
}
