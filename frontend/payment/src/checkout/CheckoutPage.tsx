import { useState } from 'react'
import { CheckoutApiError } from '../api/client'
import { Button } from '@/components/ui/button'
import { ConfirmationProgress } from './components/ConfirmationProgress'
import { PayScreen } from './components/PayScreen'
import { StatusScreen } from './components/StatusScreen'
import { isPollTimedOut, useCheckoutSession, useCheckoutStatus } from './useCheckout'

/**
 * 체크아웃 화면의 상태 분기.
 *
 * **이 파일은 어떤 화면을 그릴지만 정한다** — 실제 모양은 `components/` 아래에 있다.
 * 분기 기준은 서버가 준 상태 값이고, 프론트가 다음 상태를 스스로 추론하지 않는다
 * (`docs/architecture/checkout-api.md` 6절). 리다이렉트 시점도 마찬가지로 상태 응답의
 * `redirectUrl`이 채워지는 것을 신호로 삼는다.
 */
export function CheckoutPage({ sessionId }: { sessionId: string }) {
	const session = useCheckoutSession(sessionId)

	// 폴링은 고객이 Hash를 제출한 뒤부터 의미가 있다.
	const submitted =
		session.data?.checkoutSessionStatus === 'PAYMENT_SUBMITTED' ||
		session.data?.checkoutSessionStatus === 'COMPLETED'
	const [pollStartedAt] = useState(() => Date.now())
	const status = useCheckoutStatus(sessionId, { enabled: submitted, startedAt: pollStartedAt })

	if (session.isPending) {
		return <StatusScreen tone="pending" title="결제 정보를 불러오는 중…" />
	}

	if (session.error) {
		const error = session.error
		if (error instanceof CheckoutApiError && error.isNotFound) {
			return (
				<StatusScreen
					tone="notFound"
					title="결제를 찾을 수 없습니다"
					description="주소가 정확한지 확인해 주세요."
				/>
			)
		}
		if (error instanceof CheckoutApiError && error.isExpired) return <ExpiredScreen />

		return (
			<StatusScreen
				tone="error"
				title="결제 정보를 불러오지 못했습니다"
				description={error instanceof Error ? error.message : String(error)}
			>
				<Button onClick={() => session.refetch()}>다시 시도</Button>
			</StatusScreen>
		)
	}

	const data = session.data

	// 종료 상태도 조회는 허용된다(계약 3절) — 각 화면을 여기서 그린다.
	if (data.checkoutSessionStatus === 'CANCELLED') {
		return (
			<StatusScreen tone="cancelled" title="결제가 취소되었습니다">
				{data.cancelUrl ? (
					<Button asChild variant="outline">
						<a href={data.cancelUrl}>가맹점으로 돌아가기</a>
					</Button>
				) : (
					<p className="text-sm text-muted-foreground">이 창을 닫으셔도 됩니다.</p>
				)}
			</StatusScreen>
		)
	}
	if (data.checkoutSessionStatus === 'EXPIRED') return <ExpiredScreen />

	const redirectUrl = status.data?.redirectUrl
	if (redirectUrl) {
		return (
			<StatusScreen
				tone="success"
				title="결제가 완료되었습니다"
				description="가맹점 페이지로 이동합니다."
			>
				<Button asChild>
					<a href={redirectUrl}>바로 이동하기</a>
				</Button>
			</StatusScreen>
		)
	}

	if (submitted) {
		const failureReason = status.data?.failureReason
		if (failureReason) {
			return (
				// 실패 사유 코드를 고객에게 그대로 보여주지 않는다(계약 4.2) — 안내 문구로 옮기고,
				// 가맹점 문의 때 쓸 수 있도록 코드는 보조 정보로만 남긴다.
				<StatusScreen
					tone="error"
					title="결제가 실패했습니다"
					description="전송이 확인되지 않았습니다. 가맹점에 문의해 주세요."
				>
					<p className="text-xs text-muted-foreground">사유 코드: {failureReason}</p>
				</StatusScreen>
			)
		}

		const timedOut = isPollTimedOut(pollStartedAt) && !status.data?.redirectUrl
		return (
			<StatusScreen
				tone="pending"
				title="결제 확인 중"
				description="블록체인에서 전송을 확인하고 있습니다. 이 화면을 닫지 마세요."
			>
				<ConfirmationProgress
					confirmations={status.data?.confirmationCount ?? 0}
					required={status.data?.requiredConfirmationCount ?? data.payment.requiredConfirmationCount}
					transactionHash={status.data?.transactionHash ?? null}
				/>
				{timedOut && (
					<p className="text-sm text-destructive">
						확인이 예상보다 오래 걸립니다. 페이지를 새로고침해 주세요.
					</p>
				)}
			</StatusScreen>
		)
	}

	return <PayScreen session={data} onCancelled={() => session.refetch()} />
}

function ExpiredScreen() {
	return (
		<StatusScreen
			tone="expired"
			title="결제 시간이 만료되었습니다"
			description="가맹점에서 결제를 다시 시작해 주세요."
		/>
	)
}
