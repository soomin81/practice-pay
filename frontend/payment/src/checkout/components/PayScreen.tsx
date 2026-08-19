import { useState } from 'react'
import { checkoutApi } from '@/api/client'
import type { CheckoutSession, SubmitCustomerResponse } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardFooter } from '@/components/ui/card'
import { Separator } from '@/components/ui/separator'
import { CustomerForm } from './CustomerForm'
import { DetailRow } from './DetailRow'
import { PaymentDetails } from './PaymentDetails'
import { PaymentSummary } from './PaymentSummary'
import { WalletPanel } from './WalletPanel'

/**
 * 결제를 진행하는 본 화면. 고객이 지갑으로 USDC를 보내기 전까지 머무는 곳이다.
 *
 * 취소는 여기서만 가능하다 — `PAYMENT_SUBMITTED` 이후에는 고객이 취소할 수 없고
 * (`docs/domain/state-transitions.md`), 그 상태에서는 호출부가 확인 화면을 그리므로
 * 이 컴포넌트 자체가 렌더링되지 않는다.
 *
 * ## 단계가 둘이다 — 구매자 정보를 받은 뒤에야 지갑이 나온다
 *
 * 서명 이후에 입력을 요구하면 **돈은 나갔는데 결제가 미완인 창**이 생기므로 순서를 뒤집지
 * 않는다(ADR-008). 순서를 강제하는 것은 이 화면이다 — API는 어느 쪽이 먼저 와도 받는다.
 *
 * **입력 여부를 서버가 알려주지 않아서 로컬 상태로 판단한다**(계약 8절의 알려진 gap).
 * 그래서 새로고침하면 입력 화면을 다시 만난다 — 다시 제출하면 덮어쓰므로 결과는 같고,
 * 세션 상태(`WALLET_CONNECTED`)를 근거로 건너뛰지 않는 것은 **정보를 받는 것이 이 단계의
 * 목적**이기 때문이다. 서버는 구매자 정보 없이도 결제를 진행시키므로 여기서 막지 않으면
 * 아무도 막지 않는다.
 */
export function PayScreen({
	session,
	onSessionChanged,
}: {
	session: CheckoutSession
	/** 세션 상태를 바꾼 뒤(취소·전송 제출) 호출한다. 호출부가 세션을 다시 읽어 화면을 넘긴다. */
	onSessionChanged: () => void
}) {
	const [customer, setCustomer] = useState<SubmitCustomerResponse | null>(null)
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

				{customer ? (
					<>
						<CustomerContact masked={customer} />
						<WalletPanel session={session} onSubmitted={onSessionChanged} />
					</>
				) : (
					<CustomerForm sessionId={session.checkoutSessionId} onSubmitted={setCustomer} />
				)}
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

/**
 * 접수된 구매자 정보를 가려진 형태로 되짚어 준다.
 *
 * **원문을 화면에 다시 띄우지 않는다** — 서버가 마스킹된 값만 돌려주므로 그대로 쓴다.
 * 고객이 자기가 넣은 값이 맞는지 확인하는 데는 이걸로 충분하고, 틀렸으면 새로고침해서
 * 다시 넣으면 덮어쓴다.
 */
function CustomerContact({ masked }: { masked: SubmitCustomerResponse }) {
	return (
		<dl className="divide-y rounded-lg border px-3">
			<DetailRow label="구매자">{masked.nameMasked}</DetailRow>
			<DetailRow label="이메일">{masked.emailMasked}</DetailRow>
			<DetailRow label="휴대전화">{masked.phoneMasked}</DetailRow>
		</dl>
	)
}
