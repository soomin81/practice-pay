import { Wallet } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'

/**
 * 지갑 연결과 ERC-20 `transfer`가 들어갈 자리(2단계).
 *
 * 아직 wagmi/viem을 붙이지 않았다. 화면에서 이 블록만 교체하면 되도록 자리를 잡아
 * 두는 것이 목적이라, 결제 화면의 다른 부분은 이 컴포넌트가 무엇으로 바뀌든 영향을
 * 받지 않는다.
 */
// 스펙이 상태를 `string`으로 준다(`schema.d.ts`) — 여기서 좁히지 않고 그대로 보여준다.
export function WalletConnectSlot({ sessionStatus }: { sessionStatus: string }) {
	return (
		<Alert>
			<Wallet />
			<AlertTitle>지갑 연결은 아직 구현되지 않았습니다</AlertTitle>
			<AlertDescription>
				<span>
					2단계에서 wagmi + viem으로 지갑을 연결하고 USDC를 전송합니다. 현재 세션 상태는{' '}
					<code className="font-mono">{sessionStatus}</code>입니다.
				</span>
			</AlertDescription>
		</Alert>
	)
}
