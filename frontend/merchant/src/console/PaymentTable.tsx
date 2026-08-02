import { Link } from 'react-router-dom'
import type { PaymentSummary } from '@/api/types'
import { formatDateTime, formatKrw, formatTokenAmount } from '@/console/format'
import { DataTable, EmptyRow, Td, Th } from '@/components/console/DataTable'
import { StatusBadge } from '@/components/console/StatusBadge'

/**
 * 결제 내역 표. **토큰 금액은 `formatTokenAmount`로만 다룬다** — 백엔드가 Minor Unit을
 * 문자열로 주는 이유가 `Number` 변환에서 값이 조용히 달라질 수 있어서다.
 *
 * 시각·금액·Hash를 등폭으로 두어 **행끼리 자릿수가 맞는다** — 표를 훑으며 큰 금액을
 * 찾는 것이 이 화면의 주 용도라, 비례 폰트면 그 일이 안 된다.
 */
export function PaymentTable({ payments }: { payments: PaymentSummary[] }) {
	return (
		<DataTable
			head={
				<>
					<Th>생성 시각</Th>
					<Th>주문</Th>
					<Th align="right">주문 금액</Th>
					<Th align="right">결제 금액</Th>
					<Th>상태</Th>
					<Th>거래 Hash</Th>
				</>
			}
		>
			{payments.length === 0 ? (
				<EmptyRow colSpan={6}>조건에 맞는 결제가 없습니다.</EmptyRow>
			) : (
				payments.map((payment) => (
					<tr key={payment.paymentId} className="hover:bg-muted/40">
						<Td variant="mono">{formatDateTime(payment.createdAt)}</Td>
						<Td className="whitespace-normal">
							{/* 주문명이 상세로 가는 입구다(admin 목록과 같은 방식). */}
							<Link
								className="font-medium hover:underline hover:underline-offset-2"
								to={`/payments/${payment.paymentId}`}
							>
								{payment.orderName}
							</Link>
							<div className="mono-cell text-xs text-muted-foreground">{payment.merchantOrderId}</div>
						</Td>
						<Td variant="amount">{formatKrw(payment.orderAmount)}</Td>
						<Td variant="amount">
							{formatTokenAmount(payment.paymentAmount, payment.tokenDecimals)}{' '}
							<span className="text-muted-foreground">{payment.paymentAsset}</span>
						</Td>
						<Td>
							<StatusBadge kind="payment" status={payment.status} />
							{/* **실패 사유는 코드 그대로 보여준다** — 가맹점 운영자도 원인 코드를 그대로
							    본다(고객 대면 체크아웃에서만 문구로 번역한다). */}
							{payment.failureReason ? (
								<div className="mt-0.5 text-xs text-muted-foreground">{payment.failureReason}</div>
							) : null}
						</Td>
						{/* 전체 값은 title로 남긴다 — 운영자가 온체인 탐색기와 대조해야 한다. */}
						<Td variant="mono" className="text-xs">
							<span title={payment.transactionHash ?? undefined}>
								{payment.transactionHash ? shortenHex(payment.transactionHash) : '—'}
							</span>
						</Td>
					</tr>
				))
			)}
		</DataTable>
	)
}

function shortenHex(value: string): string {
	return value.length <= 14 ? value : `${value.slice(0, 8)}…${value.slice(-4)}`
}
