import { Progress } from '@/components/ui/progress'
import { shortenHex } from '../format'
import { DetailRow } from './DetailRow'

/**
 * 온체인 확인 진행 상황.
 *
 * Confirmation 수는 **서버가 준 값을 그대로 그린다** — 프론트가 블록을 세거나 다음
 * 상태를 예측하지 않는다. 필요한 Confirmation 수도 서버 값이다(네트워크마다 다르고
 * 백엔드 설정으로 바뀔 수 있다).
 */
export function ConfirmationProgress({
	confirmations,
	required,
	transactionHash,
}: {
	confirmations: number
	required: number
	transactionHash: string | null
}) {
	// required가 0으로 내려오는 경우에도 NaN이 되지 않게 한다.
	const percent = required > 0 ? Math.min(100, (confirmations / required) * 100) : 0

	return (
		<div className="w-full space-y-3">
			<Progress
				value={percent}
				aria-label={`확인 ${confirmations} / ${required}`}
			/>
			<p className="text-center text-sm tabular-nums text-muted-foreground">
				{confirmations} / {required} confirmations
			</p>

			{transactionHash && (
				<dl className="border-t pt-1">
					<DetailRow label="트랜잭션" fullValue={transactionHash} mono>
						{shortenHex(transactionHash, 10, 8)}
					</DetailRow>
				</dl>
			)}
		</div>
	)
}
