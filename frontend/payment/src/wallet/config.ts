import { http, createConfig } from 'wagmi'
import { baseSepolia } from 'wagmi/chains'
import { injected } from 'wagmi/connectors'

/**
 * wagmi 설정.
 *
 * **"체인 정보를 상수로 박지 않는다"는 규칙과의 관계를 분명히 해 둔다.** 여기 있는
 * `baseSepolia`는 "이 결제가 어느 체인이다"라는 선언이 아니라 **이 앱이 말을 걸 수 있는
 * 체인 목록**이다(wagmi가 설정 시점에 체인을 알아야 RPC transport를 만들 수 있다).
 *
 * 결제가 실제로 어느 체인·어느 Contract를 쓰는지는 **여전히 서버 응답이 정한다** —
 * `session.payment.chainId`와 `tokenContractAddress`를 그대로 쓰고, 지갑이 다른 체인에
 * 붙어 있으면 서버가 준 `chainId`로 전환한다(`useWalletPayment.ts`). 서버가 여기 없는
 * 체인을 지목하면 추측하지 않고 오류로 처리한다.
 *
 * MVP는 Base Sepolia만 쓰므로 목록이 하나뿐이다. 네트워크가 늘면 여기에 추가한다.
 */
export const wagmiConfig = createConfig({
	chains: [baseSepolia],
	connectors: [
		// 브라우저 확장 지갑(MetaMask 등). WalletConnect는 Project ID가 필요해서 MVP에서 뺐다.
		injected(),
	],
	transports: {
		[baseSepolia.id]: http(),
	},
})

declare module 'wagmi' {
	interface Register {
		config: typeof wagmiConfig
	}
}
