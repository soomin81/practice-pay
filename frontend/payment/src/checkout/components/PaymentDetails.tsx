import type { CheckoutSession } from '@/api/types'
import { remainingTime, shortenHex } from '../format'
import { DetailRow } from './DetailRow'

/**
 * 전송에 필요한 사실들 — 네트워크, 수취 지갑, 토큰 Contract, 적용 환율.
 *
 * **여기 있는 값 중 어느 것도 상수로 박지 않는다.** `chainId`/`tokenContractAddress`/
 * `receivingWallet`은 전부 서버 응답에서 온다. 토큰을 Symbol로 판단하지 않고
 * (네트워크, Contract 주소) 조합으로 다룬다는 도메인 규칙이 화면에도 그대로 적용된다.
 *
 * 주소는 줄여 보여주되 전체 값을 `title`에 남긴다 — 고객이 지갑에서 대조할 수 있어야 한다.
 */
export function PaymentDetails({ session }: { session: CheckoutSession }) {
	const remaining = remainingTime(session.expiresAt)

	return (
		<dl className="divide-y">
			<DetailRow label="네트워크">
				{session.payment.network}
				<span className="ml-1 text-muted-foreground">(chainId {session.payment.chainId})</span>
			</DetailRow>

			<DetailRow label="수취 지갑" fullValue={session.payment.receivingWallet} mono>
				{shortenHex(session.payment.receivingWallet)}
			</DetailRow>

			<DetailRow label="토큰 Contract" fullValue={session.payment.tokenContractAddress} mono>
				{shortenHex(session.payment.tokenContractAddress)}
			</DetailRow>

			<DetailRow label="적용 환율">
				<span className="tabular-nums">{session.quote.appliedRate}</span> KRW/{session.payment.asset}
			</DetailRow>

			{remaining && (
				<DetailRow label="남은 시간">
					<span className="tabular-nums">{remaining}</span>
				</DetailRow>
			)}
		</dl>
	)
}
