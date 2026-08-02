import type { SettlementReceivableSummary } from '@/api/types'
import { formatKrw, holdReasonLabel } from '@/console/format'
import { DataTable, EmptyRow, Td, Th } from '@/components/console/DataTable'
import { StatusBadge } from '@/components/console/StatusBadge'

/**
 * 정산 채권 표.
 *
 * **금액은 전부 KRW 원 단위 정수라 `Number`로 다뤄도 안전하다** — 토큰 금액(Minor Unit
 * 문자열)과 다른 점이다.
 */
export function SettlementTable({ rows }: { rows: SettlementReceivableSummary[] }) {
	return (
		<DataTable
			head={
				<>
					<Th>정산 예정일</Th>
					<Th>주문</Th>
					<Th align="right">정산 기준</Th>
					<Th align="right">수수료</Th>
					<Th align="right">정산 예정</Th>
					<Th align="right">환전 손익</Th>
					<Th>상태</Th>
				</>
			}
		>
			{rows.length === 0 ? (
				<EmptyRow colSpan={7}>조건에 맞는 정산 채권이 없습니다.</EmptyRow>
			) : (
				rows.map((row) => (
					<tr key={row.settlementReceivableId} className="hover:bg-muted/40">
						<Td variant="mono">{row.eligibleDate}</Td>
						<Td className="whitespace-normal">
							<div className="mono-cell">{row.merchantOrderId}</div>
							<div className="mono-cell text-xs text-muted-foreground">{row.paymentId}</div>
						</Td>
						<Td variant="amount">{formatKrw(row.grossAmount)}</Td>
						<Td variant="amount" className="text-muted-foreground">
							−{formatKrw(row.feeAmount)}
							<div className="text-xs">{formatFeeRate(row.feeRate)}</div>
						</Td>
						<Td variant="amount" className="font-semibold">
							{formatKrw(row.netAmount)}
						</Td>
						{/* 환전 확보액과 정산 기준 금액의 차이 = PG 마진. READY 전에는 값이 없다. */}
						<Td variant="amount" className="text-muted-foreground">
							{row.exchangeProfitLossAmount === null || row.exchangeProfitLossAmount === undefined
								? '—'
								: formatSignedKrw(row.exchangeProfitLossAmount)}
						</Td>
						<Td className="whitespace-normal">
							<StatusBadge kind="settlement" status={row.status} />
							{/* 왜 막혔는지를 함께 적는다 — 이 콘솔에는 푸는 수단이 없지만, 이유를 모르면 문의밖에 남지 않는다. */}
							{row.holdReasonCode ? (
								<div className="text-xs text-muted-foreground">{holdReasonLabel(row.holdReasonCode)}</div>
							) : null}
						</Td>
					</tr>
				))
			)}
		</DataTable>
	)
}

/** `0.015` → `1.5%`. 서버가 `BigDecimal`을 숫자로 주므로 그대로 계산한다. */
function formatFeeRate(rate: number): string {
	return `${(rate * 100).toFixed(2).replace(/\.?0+$/, '')}%`
}

function formatSignedKrw(amount: number): string {
	return amount >= 0 ? `+${formatKrw(amount)}` : `−${formatKrw(Math.abs(amount))}`
}
