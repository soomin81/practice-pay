package paytech.practice.pay.application.payment

import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.WalletAddress

/**
 * 네트워크별 **PG 수취 지갑**을 담는다. `CreatePaymentUseCase`가 결제를 만들 때
 * 이 레지스트리에서 수취 지갑을 꺼낸다 — 호출부(가맹점)가 넘기지 않는다.
 *
 * 수취 지갑이 PG 것이어야 하는 이유는 `docs/architecture/mvp-scope.md`의
 * "수취 지갑 귀속" 절에 있다: 정산이 "PG가 USDC를 받아 매도하고 가맹점에 KRW
 * 채권을 세운다"는 구조라, 가맹점이 자기 주소를 지정할 수 있으면 USDC를 직접
 * 받으면서 KRW까지 받아 같은 대금이 두 번 나간다.
 *
 * [PaymentNetworkConfig]와 달리 **코드 상수가 아니라 주입받는 설정이다** —
 * 실제 자금을 보유하는 주소라 환경마다 다르고, 저장소에 값을 적어 둘 수 없다.
 * 구성은 각 앱의 Composition Root가 한다.
 */
class ReceivingWalletRegistry(
	private val walletsByNetwork: Map<BlockchainNetwork, WalletAddress>,
) {
	fun walletFor(network: BlockchainNetwork): WalletAddress =
		walletsByNetwork[network] ?: throw ReceivingWalletNotConfiguredException(network)
}
