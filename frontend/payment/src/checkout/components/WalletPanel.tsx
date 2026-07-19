import { Wallet } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import type { CheckoutSession } from '@/api/types'
import { useWalletPayment, type PaymentStep } from '@/wallet/useWalletPayment'
import { formatTokenAmount, shortenHex } from '../format'

/** 진행 중일 때 버튼에 띄울 문구. 고객이 지금 어디를 봐야 하는지 알려주는 것이 목적이다. */
const STEP_LABEL: Record<Exclude<PaymentStep, 'idle'>, string> = {
	connecting: '지갑에서 연결을 승인해 주세요…',
	registering: '지갑을 등록하는 중…',
	switchingChain: '지갑에서 네트워크 전환을 승인해 주세요…',
	signing: '지갑에서 전송을 승인해 주세요…',
	submitting: '전송 정보를 제출하는 중…',
}

/**
 * 지갑 연결과 USDC 전송.
 *
 * 흐름 자체는 `useWalletPayment`가 갖고, 여기서는 **지금 어느 단계인지**만 그린다.
 * 결제 성공 여부는 이 컴포넌트가 판단하지 않는다 — 제출이 접수되면 호출부가 세션을
 * 다시 읽고, 그다음은 상태 폴링 화면이 이어받는다.
 */
export function WalletPanel({
	session,
	onSubmitted,
}: {
	session: CheckoutSession
	onSubmitted: () => void
}) {
	const payment = useWalletPayment({ session, onSubmitted })

	if (!payment.hasConnector) {
		return (
			<Alert variant="destructive">
				<Wallet />
				<AlertTitle>지갑을 찾을 수 없습니다</AlertTitle>
				<AlertDescription>
					<span>MetaMask 같은 브라우저 지갑 확장을 설치한 뒤 페이지를 새로고침해 주세요.</span>
				</AlertDescription>
			</Alert>
		)
	}

	const amount = formatTokenAmount(session.payment.amount, session.payment.tokenDecimals)

	return (
		<div className="space-y-3">
			{payment.isConnected && payment.address && (
				<div className="flex items-center justify-between rounded-lg border px-3 py-2 text-sm">
					<span className="text-muted-foreground">연결된 지갑</span>
					<span className="font-mono" title={payment.address}>
						{shortenHex(payment.address)}
					</span>
				</div>
			)}

			{payment.onTargetChain === false && (
				<p className="text-sm text-muted-foreground">
					지갑이 다른 네트워크에 연결되어 있습니다. 전송을 시작하면 {session.payment.network}로
					전환을 요청합니다.
				</p>
			)}

			{payment.isConnected ? (
				<Button className="w-full" size="lg" onClick={payment.pay} disabled={payment.busy}>
					{payment.busy && payment.step !== 'idle'
						? STEP_LABEL[payment.step]
						: `${amount} ${session.payment.asset} 보내기`}
				</Button>
			) : (
				<Button className="w-full" size="lg" onClick={payment.connect} disabled={payment.busy}>
					{payment.busy && payment.step !== 'idle' ? STEP_LABEL[payment.step] : '지갑 연결'}
				</Button>
			)}

			{payment.error && <p className="text-sm text-destructive">{payment.error}</p>}
		</div>
	)
}
