import { useMemo, useState } from 'react'
import { CheckoutApiError, checkoutApi } from '../api/client'
import type { CheckoutSession } from '../api/types'
import { formatKrw, formatTokenAmount, remainingTime, shortenHex } from './format'
import { isPollTimedOut, useCheckoutSession, useCheckoutStatus } from './useCheckout'

/**
 * 체크아웃 화면.
 *
 * **화면 분기는 서버가 준 상태 값이 이끈다** — 프론트가 다음 상태를 스스로 추론하지
 * 않는다(`docs/architecture/checkout-api.md`의 6절). 리다이렉트 시점도 마찬가지로
 * 상태 응답의 `redirectUrl`이 채워지는 것을 신호로 삼는다.
 *
 * 지갑 연결·전송은 아직 없다(2단계). 지금은 그 자리에 무엇이 필요한지만 표시한다.
 */
export function CheckoutPage({ sessionId }: { sessionId: string }) {
	const session = useCheckoutSession(sessionId)

	// 폴링은 고객이 Hash를 제출한 뒤부터 의미가 있다.
	const submitted =
		session.data?.checkoutSessionStatus === 'PAYMENT_SUBMITTED' ||
		session.data?.checkoutSessionStatus === 'COMPLETED'
	const [pollStartedAt] = useState(() => Date.now())
	const status = useCheckoutStatus(sessionId, { enabled: submitted, startedAt: pollStartedAt })

	if (session.isPending) return <Panel><p>결제 정보를 불러오는 중…</p></Panel>

	if (session.error) {
		const error = session.error
		if (error instanceof CheckoutApiError && error.isNotFound) {
			return <Panel tone="error"><h2>결제를 찾을 수 없습니다</h2><p>주소가 정확한지 확인해 주세요.</p></Panel>
		}
		if (error instanceof CheckoutApiError && error.isExpired) {
			return <ExpiredPanel />
		}
		return (
			<Panel tone="error">
				<h2>결제 정보를 불러오지 못했습니다</h2>
				<p>{error instanceof Error ? error.message : String(error)}</p>
				<button onClick={() => session.refetch()}>다시 시도</button>
			</Panel>
		)
	}

	const data = session.data

	// 종료 상태는 조회가 허용되므로(계약 3절) 여기서 각 화면을 그린다.
	if (data.checkoutSessionStatus === 'CANCELLED') return <CancelledPanel cancelUrl={data.cancelUrl} />
	if (data.checkoutSessionStatus === 'EXPIRED') return <ExpiredPanel />

	const redirectUrl = status.data?.redirectUrl
	if (redirectUrl) return <SucceededPanel redirectUrl={redirectUrl} />

	if (submitted) {
		return (
			<ConfirmingPanel
				confirmations={status.data?.confirmationCount ?? 0}
				required={status.data?.requiredConfirmationCount ?? data.payment.requiredConfirmationCount}
				transactionHash={status.data?.transactionHash ?? null}
				failureReason={status.data?.failureReason ?? null}
				timedOut={isPollTimedOut(pollStartedAt) && !status.data?.redirectUrl}
			/>
		)
	}

	return <PayPanel session={data} onCancelled={() => session.refetch()} />
}

function PayPanel({ session, onCancelled }: { session: CheckoutSession; onCancelled: () => void }) {
	const [cancelError, setCancelError] = useState<string | null>(null)
	const [cancelling, setCancelling] = useState(false)

	const amount = useMemo(
		() => formatTokenAmount(session.payment.amount, session.payment.tokenDecimals),
		[session.payment.amount, session.payment.tokenDecimals],
	)
	const remaining = remainingTime(session.expiresAt)

	async function cancel() {
		setCancelling(true)
		setCancelError(null)
		try {
			const result = await checkoutApi.cancel(session.checkoutSessionId)
			if (result.redirectUrl) {
				window.location.href = result.redirectUrl
				return
			}
			onCancelled()
		} catch (error) {
			setCancelError(error instanceof Error ? error.message : String(error))
		} finally {
			setCancelling(false)
		}
	}

	return (
		<Panel>
			<h2>{session.order.orderName}</h2>
			<p className="order-amount">{formatKrw(session.order.orderAmount)} {session.order.orderCurrency}</p>

			<dl className="detail">
				<dt>보낼 금액</dt>
				<dd>
					<strong>{amount} {session.payment.asset}</strong>
				</dd>

				<dt>네트워크</dt>
				<dd>{session.payment.network} <span className="muted">(chainId {session.payment.chainId})</span></dd>

				<dt>수취 지갑</dt>
				<dd title={session.payment.receivingWallet}>{shortenHex(session.payment.receivingWallet)}</dd>

				<dt>토큰 Contract</dt>
				<dd title={session.payment.tokenContractAddress}>{shortenHex(session.payment.tokenContractAddress)}</dd>

				<dt>적용 환율</dt>
				<dd>{session.quote.appliedRate} KRW/{session.payment.asset}</dd>

				{remaining && (
					<>
						<dt>남은 시간</dt>
						<dd>{remaining}</dd>
					</>
				)}
			</dl>

			{/* 2단계에서 wagmi 지갑 연결과 ERC-20 transfer가 들어갈 자리다. */}
			<div className="wallet-placeholder">
				지갑 연결은 아직 구현되지 않았습니다(2단계).
				<br />
				현재 세션 상태: <code>{session.checkoutSessionStatus}</code>
			</div>

			{cancelError && <p className="error-text">{cancelError}</p>}
			<button onClick={cancel} disabled={cancelling} className="secondary">
				{cancelling ? '취소하는 중…' : '결제 취소'}
			</button>
		</Panel>
	)
}

function ConfirmingPanel({
	confirmations,
	required,
	transactionHash,
	failureReason,
	timedOut,
}: {
	confirmations: number
	required: number
	transactionHash: string | null
	failureReason: string | null
	timedOut: boolean
}) {
	if (failureReason) {
		return (
			<Panel tone="error">
				<h2>결제가 실패했습니다</h2>
				{/* 실패 사유 코드를 고객에게 그대로 보여주지 않는다(계약 4.2) — 안내 문구로 옮긴다. */}
				<p>전송이 확인되지 않았습니다. 가맹점에 문의해 주세요.</p>
				<p className="muted">사유 코드: {failureReason}</p>
			</Panel>
		)
	}

	return (
		<Panel>
			<h2>결제 확인 중</h2>
			<p>블록체인에서 전송을 확인하고 있습니다. 이 화면을 닫지 마세요.</p>
			<progress value={confirmations} max={required} />
			<p>
				{confirmations} / {required} confirmations
			</p>
			{transactionHash && (
				<p className="muted" title={transactionHash}>
					Tx {shortenHex(transactionHash, 10, 8)}
				</p>
			)}
			{timedOut && <p className="error-text">확인이 예상보다 오래 걸립니다. 페이지를 새로고침해 주세요.</p>}
		</Panel>
	)
}

function SucceededPanel({ redirectUrl }: { redirectUrl: string }) {
	return (
		<Panel tone="success">
			<h2>결제가 완료되었습니다</h2>
			<p>가맹점 페이지로 이동합니다.</p>
			<a href={redirectUrl}>바로 이동하기</a>
		</Panel>
	)
}

function ExpiredPanel() {
	return (
		<Panel tone="error">
			<h2>결제 시간이 만료되었습니다</h2>
			<p>가맹점에서 결제를 다시 시작해 주세요.</p>
		</Panel>
	)
}

function CancelledPanel({ cancelUrl }: { cancelUrl?: string | null }) {
	return (
		<Panel>
			<h2>결제가 취소되었습니다</h2>
			{cancelUrl ? <a href={cancelUrl}>가맹점으로 돌아가기</a> : <p>이 창을 닫으셔도 됩니다.</p>}
		</Panel>
	)
}

function Panel({ children, tone }: { children: React.ReactNode; tone?: 'error' | 'success' }) {
	return <section className={`panel ${tone ?? ''}`}>{children}</section>
}
