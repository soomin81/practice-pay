import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AdminApiError } from '@/api/client'
import { usePaymentDetail } from '@/console/usePaymentDetail'
import { formatDateTime, formatKrw, formatTokenAmount } from '@/console/format'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

/**
 * 결제 한 건의 전체 맥락을 단계별로 보여준다.
 *
 * **이 화면의 목적은 "돈이 어디 있나"에 답하는 것이다** — ADR-007이 "사람이
 * `blockchain_transaction`을 조회해서 판단한다"고 전제한 그 조회를 화면으로 만든 것이라,
 * 결제가 실패해도 온체인 수령 사실을 그대로 보여준다.
 *
 * **비어 있는 단계를 감추지 않고 "아직 진행되지 않음"으로 그린다** — 어디까지 갔는지가 곧
 * 진단 정보이기 때문이다. 감추면 "없는 것"과 "아직인 것"이 화면에서 같아진다.
 */
export function PaymentDetailPage() {
	const { paymentId = '' } = useParams()
	const detail = usePaymentDetail(paymentId)

	if (detail.isPending) return <p className="text-sm text-muted-foreground">불러오는 중…</p>

	if (detail.isError) {
		return (
			<div className="flex flex-col items-start gap-3">
				<p className="text-sm text-destructive">{errorMessage(detail.error)}</p>
				<Button variant="outline" size="sm" asChild>
					<Link to="/payments">결제 내역으로</Link>
				</Button>
			</div>
		)
	}

	const { payment, quote, checkoutSession, blockchainTransaction, exchangeOrder, settlementReceivable, webhookDeliveries } =
		detail.data

	return (
		<div className="flex flex-col gap-6">
			<div className="flex items-start justify-between gap-3">
				<div>
					<h1 className="font-heading text-xl font-medium">{payment.orderName}</h1>
					<p className="font-mono text-xs break-all text-muted-foreground">{payment.paymentId}</p>
				</div>
				<Button variant="outline" size="sm" asChild>
					<Link to="/payments">목록으로</Link>
				</Button>
			</div>

			<Section title="결제" description="주문과 결제 금액, 현재 상태">
				<Field label="상태">
					<Badge variant={statusTone(payment.status)}>{payment.status}</Badge>
					{payment.failureReason && <span className="ml-2 text-xs text-destructive">{payment.failureReason}</span>}
				</Field>
				<Field label="가맹점">{payment.merchantName}</Field>
				<Field label="주문 번호">{payment.merchantOrderId}</Field>
				<Field label="주문 금액">{formatKrw(payment.orderAmount)}</Field>
				<Field label="결제 금액">
					{formatTokenAmount(payment.paymentAmount, payment.tokenDecimals)} {payment.paymentAsset}
				</Field>
				<Field label="네트워크">{payment.network}</Field>
				<Field label="수취 지갑" mono>
					{payment.receivingWallet}
				</Field>
				<Field label="고객 지갑" mono>
					{payment.customerWallet ?? '아직 연결되지 않음'}
				</Field>
				<Field label="생성 / 완료">
					{formatDateTime(payment.createdAt)} / {payment.paidAt ? formatDateTime(payment.paidAt) : '—'}
				</Field>
			</Section>

			<Section title="견적" description="확정 시점에 고정된 환율 스냅샷(이후 변하지 않는다)">
				<Field label="시장 환율">{quote.marketRate}</Field>
				<Field label="적용 환율">{quote.appliedRate}</Field>
				<Field label="스프레드">{quote.spreadRate}</Field>
				<Field label="견적 시각">{formatDateTime(quote.quotedAt)}</Field>
			</Section>

			<Section title="체크아웃" description="고객이 결제를 진행한 세션">
				<Field label="상태">{checkoutSession.status}</Field>
				<Field label="연결된 지갑" mono>
					{checkoutSession.connectedWallet ?? '아직 연결되지 않음'}
				</Field>
			</Section>

			<Section title="온체인 거래" description="체인에서 실제로 일어난 일. 결제가 실패해도 이 기록은 남는다.">
				{blockchainTransaction ? (
					<>
						<Field label="상태">
							{blockchainTransaction.status} ({blockchainTransaction.confirmationCount} /{' '}
							{blockchainTransaction.requiredConfirmationCount} Confirm)
						</Field>
						<Field label="블록">{blockchainTransaction.blockNumber ?? '—'}</Field>
						<Field label="거래 Hash" mono>
							{blockchainTransaction.transactionHash}
						</Field>
						<Field label="수령 금액">
							{blockchainTransaction.amountMinor
								? `${formatTokenAmount(blockchainTransaction.amountMinor, payment.tokenDecimals)} ${payment.paymentAsset}`
								: '—'}
						</Field>
						<Field label="보낸 주소" mono>
							{blockchainTransaction.fromAddress ?? '—'}
						</Field>
						<Field label="받은 주소" mono>
							{blockchainTransaction.toAddress ?? '—'}
						</Field>
					</>
				) : (
					<NotYet>고객이 아직 거래 Hash를 제출하지 않았습니다.</NotYet>
				)}
			</Section>

			<Section title="환전" description="받은 USDC를 KRW로 매도한 결과">
				{exchangeOrder ? (
					<>
						<Field label="상태">
							{exchangeOrder.status} ({exchangeOrder.providerCode})
						</Field>
						<Field label="체결 환율">{exchangeOrder.averageExecutionRate ?? '—'}</Field>
						<Field label="확보 금액">{optionalKrw(exchangeOrder.receivedAmount)}</Field>
						<Field label="체결 시각">
							{exchangeOrder.completedAt ? formatDateTime(exchangeOrder.completedAt) : '—'}
						</Field>
					</>
				) : (
					<NotYet>아직 매도되지 않았습니다(결제가 완료되어야 진행됩니다).</NotYet>
				)}
			</Section>

			<Section title="정산" description="가맹점에 지급할 채권">
				{settlementReceivable ? (
					<>
						<Field label="상태">{settlementReceivable.status}</Field>
						<Field label="정산 기준">{formatKrw(settlementReceivable.grossAmount)}</Field>
						<Field label="수수료">−{formatKrw(settlementReceivable.feeAmount)}</Field>
						<Field label="정산 예정">
							<strong>{formatKrw(settlementReceivable.netAmount)}</strong>
						</Field>
						<Field label="환전 손익">{optionalKrw(settlementReceivable.exchangeProfitLossAmount)}</Field>
						<Field label="정산 예정일">{settlementReceivable.eligibleDate}</Field>
					</>
				) : (
					<NotYet>아직 정산 채권이 만들어지지 않았습니다.</NotYet>
				)}
			</Section>

			<Section title="Webhook 전송" description="가맹점에 알린 이력. 실패해도 기록이 남는다.">
				{webhookDeliveries.length === 0 ? (
					<NotYet>전송 이력이 없습니다(가맹점이 Webhook URL을 설정하지 않았을 수 있습니다).</NotYet>
				) : (
					<div className="overflow-x-auto sm:col-span-2">
						<table className="w-full text-sm">
							<thead>
								<tr className="border-b text-left text-muted-foreground">
									<th className="py-2 pr-3 font-medium">이벤트</th>
									<th className="py-2 pr-3 font-medium">상태</th>
									<th className="py-2 pr-3 font-medium">시도</th>
									<th className="py-2 pr-3 font-medium">응답</th>
									<th className="py-2 font-medium">시각</th>
								</tr>
							</thead>
							<tbody>
								{webhookDeliveries.map((delivery) => (
									<tr key={delivery.webhookDeliveryId} className="border-b last:border-0">
										<td className="py-2 pr-3">{delivery.eventType}</td>
										<td className="py-2 pr-3">
											<Badge variant={delivery.status === 'SUCCEEDED' ? 'default' : 'destructive'}>
												{delivery.status}
											</Badge>
										</td>
										<td className="py-2 pr-3">{delivery.attemptCount}회</td>
										<td className="py-2 pr-3">{delivery.lastHttpStatus ?? '—'}</td>
										<td className="py-2 whitespace-nowrap">{formatDateTime(delivery.createdAt)}</td>
									</tr>
								))}
							</tbody>
						</table>
					</div>
				)}
			</Section>
		</div>
	)
}

function Section({ title, description, children }: { title: string; description: string; children: ReactNode }) {
	return (
		<Card>
			<CardHeader>
				<CardTitle className="text-base">{title}</CardTitle>
				<CardDescription>{description}</CardDescription>
			</CardHeader>
			<CardContent className="grid gap-x-6 gap-y-3 sm:grid-cols-2">{children}</CardContent>
		</Card>
	)
}

/** 주소·Hash는 줄이지 않고 전체를 보여준다 — 운영자가 온체인 탐색기와 대조해야 한다. */
function Field({ label, mono, children }: { label: string; mono?: boolean; children: ReactNode }) {
	return (
		<div className="flex flex-col gap-0.5 text-sm">
			<span className="text-xs text-muted-foreground">{label}</span>
			<span className={mono ? 'font-mono text-xs break-all' : undefined}>{children}</span>
		</div>
	)
}

/** 비어 있는 단계를 감추지 않고 이유를 적는다 — 어디까지 갔는지가 곧 진단 정보다. */
function NotYet({ children }: { children: ReactNode }) {
	return <p className="text-sm text-muted-foreground sm:col-span-2">{children}</p>
}

/** 금액이 `null`이면 `0`이 아니라 빈 표식으로 그린다(정산 표와 같은 규칙). */
function optionalKrw(amount: number | null | undefined): string {
	return amount === null || amount === undefined ? '—' : formatKrw(amount)
}

function statusTone(status: string): 'default' | 'destructive' | 'secondary' {
	if (status === 'SUCCEEDED') return 'default'
	if (status === 'FAILED') return 'destructive'
	return 'secondary'
}

function errorMessage(error: unknown): string {
	if (error instanceof AdminApiError) {
		if (error.status === 404) return '결제를 찾을 수 없습니다.'
		return error.message
	}
	return '결제 상세를 불러오지 못했습니다.'
}
