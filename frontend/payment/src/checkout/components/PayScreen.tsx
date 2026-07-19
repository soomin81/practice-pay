import { useState } from 'react'
import { checkoutApi } from '@/api/client'
import type { CheckoutSession } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { PaymentDetails } from './PaymentDetails'
import { PaymentSummary } from './PaymentSummary'
import { WalletPanel } from './WalletPanel'

/**
 * 결제를 진행하는 본 화면. 고객이 지갑으로 USDC를 보내기 전까지 머무는 곳이다.
 *
 * 취소는 여기서만 가능하다 — `PAYMENT_SUBMITTED` 이후에는 고객이 취소할 수 없고
 * (`docs/domain/state-transitions.md`), 그 상태에서는 호출부가 확인 화면을 그리므로
 * 이 컴포넌트 자체가 렌더링되지 않는다.
 */
export function PayScreen({
	session,
	onSessionChanged,
}: {
	session: CheckoutSession
	/** 세션 상태를 바꾼 뒤(취소·전송 제출) 호출한다. 호출부가 세션을 다시 읽어 화면을 넘긴다. */
	onSessionChanged: () => void
}) {
	const [cancelError, setCancelError] = useState<string | null>(null)
	const [cancelling, setCancelling] = useState(false)

	async function cancel() {
		setCancelling(true)
		setCancelError(null)
		try {
			const result = await checkoutApi.cancel(session.checkoutSessionId)
			// 가맹점이 cancelUrl을 준 경우 서버가 돌려보낼 주소를 정해준다.
			if (result.redirectUrl) {
				window.location.href = result.redirectUrl
				return
			}
			onSessionChanged()
		} catch (error) {
			setCancelError(error instanceof Error ? error.message : String(error))
		} finally {
			setCancelling(false)
		}
	}

	return (
		<Card>
			<CardContent className="space-y-4">
				<PaymentSummary session={session} />
				<Separator />
				<PaymentDetails session={session} />
				<WalletPanel session={session} onSubmitted={onSessionChanged} />
			</CardContent>

			<CardFooter className="flex-col items-stretch gap-2">
				{cancelError && <p className="text-sm text-destructive">{cancelError}</p>}
				<Button variant="ghost" onClick={cancel} disabled={cancelling}>
					{cancelling ? '취소하는 중…' : '결제 취소'}
				</Button>
			</CardFooter>
		</Card>
	)
}
