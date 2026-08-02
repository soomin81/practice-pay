import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { AdminApiError } from '@/api/client'
import { useMarkTransactionReorged, usePaymentDetail, useRedeliverWebhook } from '@/console/usePaymentDetail'
import { formatDateTime, formatKrw, formatTokenAmount } from '@/console/format'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/console/PageHeader'
import { Panel } from '@/components/console/Panel'
import { DataTable, Td, Th } from '@/components/console/DataTable'
import { StatusBadge } from '@/components/console/StatusBadge'

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
	const redeliver = useRedeliverWebhook(paymentId)
	const markReorged = useMarkTransactionReorged(paymentId)

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
		<>
			<PageHeader
				title={payment.orderName}
				description={payment.paymentId}
				action={
					<Button variant="outline" size="sm" asChild>
						<Link to="/payments">목록으로</Link>
					</Button>
				}
			/>

			<div className="flex flex-col gap-6">
			<Section title="결제" description="주문과 결제 금액, 현재 상태">
				<Field label="상태">
					<StatusBadge kind="payment" status={payment.status} />
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
				<Field label="상태">
					<StatusBadge kind="checkout" status={checkoutSession.status} />
				</Field>
				<Field label="연결된 지갑" mono>
					{checkoutSession.connectedWallet ?? '아직 연결되지 않음'}
				</Field>
			</Section>

			<Section title="온체인 거래" description="체인에서 실제로 일어난 일. 결제가 실패해도 이 기록은 남는다.">
				{blockchainTransaction ? (
					<>
						<Field label="상태">
							<StatusBadge kind="onchain" status={blockchainTransaction.status} />{' '}
							<span className="tabular text-xs text-muted-foreground">
								{blockchainTransaction.confirmationCount} / {blockchainTransaction.requiredConfirmationCount} Confirm
							</span>
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

						{/* **확정된 거래에만 나온다.** 확정 전이면 자동 폴링이 유예를 두고 판단하므로
						    사람이 끼어들 이유가 없다(서버도 409로 거절한다). */}
						{blockchainTransaction.status === 'CONFIRMED' ? (
							<div className="sm:col-span-2">
								<ReorgAction
									blockchainTransactionId={blockchainTransaction.blockchainTransactionId}
									markReorged={markReorged}
								/>
							</div>
						) : null}

						{/* 이미 표시된 뒤에는 **무슨 뜻인지**를 적는다 — 결제가 여전히 "결제 완료"라
						    목록만 보면 정상으로 보이기 때문이다. */}
						{blockchainTransaction.status === 'REORGED' ? (
							<Alert variant="destructive" className="sm:col-span-2">
								<AlertTitle>이 입금은 체인에서 사라졌습니다</AlertTitle>
								<AlertDescription>
									결제와 환전은 <strong>그때 실제로 일어난 일이라 그대로 둡니다.</strong> 실제 손실은 아래 정산이{' '}
									<strong>보류</strong> 상태인지로 막힙니다 — 보류가 아니라면 지급이 나갈 수 있으니 확인하세요.
								</AlertDescription>
							</Alert>
						) : null}
					</>
				) : (
					<NotYet>고객이 아직 거래 Hash를 제출하지 않았습니다.</NotYet>
				)}
			</Section>

			<Section title="환전" description="받은 USDC를 KRW로 매도한 결과">
				{exchangeOrder ? (
					<>
						<Field label="상태">
							<StatusBadge kind="exchange" status={exchangeOrder.status} />{' '}
							<span className="text-xs text-muted-foreground">{exchangeOrder.providerCode}</span>
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
						<Field label="상태">
							<StatusBadge kind="settlement" status={settlementReceivable.status} />
						</Field>
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
					<div className="flex flex-col gap-3 sm:col-span-2">
						<DataTable
							head={
								<>
									<Th>이벤트</Th>
									<Th>상태</Th>
									<Th align="right">시도</Th>
									<Th align="right">응답</Th>
									<Th>시각</Th>
									<Th align="right"> </Th>
								</>
							}
						>
							{webhookDeliveries.map((delivery) => (
								<tr key={delivery.webhookDeliveryId}>
									<Td className="mono-cell text-foreground">{delivery.eventType}</Td>
									<Td>
										<StatusBadge kind="webhook" status={delivery.status} />
									</Td>
									<Td variant="amount">{delivery.attemptCount}회</Td>
									<Td variant="amount">{delivery.lastHttpStatus ?? '—'}</Td>
									<Td variant="mono">{formatDateTime(delivery.createdAt)}</Td>
									<Td className="text-right">
										{/* **실패한 전송에만** 버튼을 그린다 — 성공한 것을 다시 보내는 것은
										    재전송이 아니라 중복 발송이고, 서버도 409로 거절한다. */}
										{delivery.status === 'FAILED' ? (
											<Button
												size="sm"
												variant="outline"
												disabled={redeliver.isPending}
												onClick={() => redeliver.mutate(delivery.webhookDeliveryId)}
											>
												{redeliver.isPending ? '예약 중…' : '재전송'}
											</Button>
										) : null}
									</Td>
								</tr>
							))}
						</DataTable>

						{redeliver.error ? (
							<p className="text-sm text-destructive">{redeliverErrorMessage(redeliver.error)}</p>
						) : null}
						{/* **"보냈다"가 아니라 "예약했다"** — 실제 발송은 발행 Worker가 하므로,
						    이 시점에는 아직 결과를 모른다. 성공으로 읽히면 사용자가 거짓 정보를 갖는다. */}
						{redeliver.isSuccess && !redeliver.error ? (
							<p className="text-sm text-muted-foreground">
								재전송을 예약했습니다. 잠시 뒤 다시 시도되며, 결과는 이 표에서 확인하세요.
							</p>
						) : null}
					</div>
				)}
			</Section>
			</div>
		</>
	)
}

function Section({ title, description, children }: { title: string; description: string; children: ReactNode }) {
	return (
		<Panel title={title} meta={description} bodyClassName="grid gap-x-6 gap-y-4 px-5 pb-5 sm:grid-cols-2">
			{children}
		</Panel>
	)
}

/** 주소·Hash는 줄이지 않고 전체를 보여준다 — 운영자가 온체인 탐색기와 대조해야 한다. */
function Field({ label, mono, children }: { label: string; mono?: boolean; children: ReactNode }) {
	return (
		<div className="flex flex-col gap-0.5 text-sm">
			<span className="text-xs text-muted-foreground">{label}</span>
			<span className={mono ? 'mono-cell text-xs break-all' : 'tabular'}>{children}</span>
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

function errorMessage(error: unknown): string {
	if (error instanceof AdminApiError) {
		if (error.status === 404) return '결제를 찾을 수 없습니다.'
		return error.message
	}
	return '결제 상세를 불러오지 못했습니다.'
}

/**
 * 재전송 실패 문구. **`409`는 "잘못된 요청"이 아니라 "지금은 그 상태가 아니다"**라
 * 서버가 준 설명을 그대로 보여준다 — 왜 안 되는지 모르면 같은 버튼을 계속 누른다.
 */
function redeliverErrorMessage(error: unknown): string {
	return error instanceof AdminApiError ? error.message : '재전송을 예약하지 못했습니다.'
}

/**
 * 확정 이후 체인 재구성 표시. **되돌릴 수 없고 가맹점에게 지급될 돈을 막는 동작**이라
 * 확인 절차 뒤에 둔다(`SUPER_ADMIN`이 아니면 서버가 403으로 막는다).
 */
function ReorgAction({
	blockchainTransactionId,
	markReorged,
}: {
	blockchainTransactionId: string
	markReorged: ReturnType<typeof useMarkTransactionReorged>
}) {
	const [confirming, setConfirming] = useState(false)

	if (!confirming) {
		return (
			<div className="flex flex-col gap-2">
				<Button size="sm" variant="outline" onClick={() => setConfirming(true)}>
					체인 재구성으로 표시
				</Button>
				{markReorged.error ? (
					<p className="text-sm text-destructive">{reorgErrorMessage(markReorged.error)}</p>
				) : null}
				{/* **막지 못한 쪽이 오히려 위험하다** — 채권이 아직 없다는 뜻이고, 매도 Worker가
				    이 결제를 집어 채권을 만들 수 있다. */}
				{markReorged.data?.settlementHeld === false ? (
					<p className="text-sm text-destructive">
						표시했지만 <strong>막을 정산 채권이 아직 없습니다.</strong> 곧 만들어질 수 있으니 정산 화면에서 확인하고
						직접 보류하세요.
					</p>
				) : null}
			</div>
		)
	}

	return (
		<Alert variant="destructive" className="flex flex-col gap-3">
			<AlertTitle>되돌릴 수 없습니다</AlertTitle>
			<AlertDescription>
				이 거래를 <strong>체인 재구성</strong>으로 표시하고 딸린 정산 채권을 <strong>보류</strong>합니다 — 가맹점에게
				지급이 나가지 않습니다. <strong>결제와 환전 상태는 바꾸지 않습니다</strong>(그때 실제로 일어난 일이라
				기록으로 남깁니다). 탐색기에서 이 거래가 정말 사라졌는지 먼저 확인하세요.
			</AlertDescription>
			<div className="flex items-center gap-2">
				<Button
					size="sm"
					variant="destructive"
					disabled={markReorged.isPending}
					onClick={() => {
						markReorged.mutate(blockchainTransactionId, { onSuccess: () => setConfirming(false) })
					}}
				>
					{markReorged.isPending ? '표시 중…' : '표시합니다'}
				</Button>
				<Button size="sm" variant="outline" onClick={() => setConfirming(false)}>
					취소
				</Button>
			</div>
		</Alert>
	)
}

/** `409`는 "왜 안 되는지"를 담고 있다 — 그대로 보여주지 않으면 같은 버튼을 계속 누른다. */
function reorgErrorMessage(error: unknown): string {
	return error instanceof AdminApiError ? error.message : '체인 재구성으로 표시하지 못했습니다.'
}
