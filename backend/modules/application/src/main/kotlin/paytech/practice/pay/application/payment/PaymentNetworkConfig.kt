package paytech.practice.pay.application.payment

import paytech.practice.pay.domain.blockchain.ChainId
import paytech.practice.pay.domain.blockchain.ContractAddress
import paytech.practice.pay.domain.shared.BlockchainNetwork

/**
 * `docs/`에 값이 정해져 있지 않아 MVP가 고정한, 네트워크별 플랫폼 상수다 —
 * `CreatePaymentUseCase`의 `TOKEN_DECIMALS`와 같은 성격의 단순화다.
 * [SubmitPaymentTransactionUseCase]와 [ConfirmBlockchainTransactionUseCase]가
 * 둘 다 필요로 해서(제출 시점에 "기대값"을 기록하고, Confirm 시점에 그 기대값과
 * 실제 온체인 값을 비교) 하나로 모아뒀다 — 두 Use Case가 각자 따로 상수를 들고
 * 있으면 나중에 값이 어긋날 위험이 있다.
 */
object PaymentNetworkConfig {
	/**
	 * 네트워크별 실제 Chain ID. Base Sepolia 값은 공개 RPC(`https://sepolia.base.org`)의
	 * `eth_chainId` 응답으로 직접 확인했다(`0x14a34` = `84532`).
	 */
	val EXPECTED_CHAIN_IDS: Map<BlockchainNetwork, ChainId> =
		mapOf(
			BlockchainNetwork.BASE_SEPOLIA to ChainId(84_532),
		)

	/**
	 * 네트워크별 허용 USDC Contract 주소. `Asset`(예: `USDC`)은 순수 표시용 코드일
	 * 뿐이라("Token Symbol만으로 자산을 판단하지 않는다") 별도로 관리한다. Base
	 * Sepolia 값은 Circle 공식 문서(developers.circle.com/stablecoins/usdc-contract-addresses)의
	 * Base Sepolia USDC Contract 주소를 그대로 썼다.
	 */
	val EXPECTED_USDC_CONTRACT_ADDRESSES: Map<BlockchainNetwork, ContractAddress> =
		mapOf(
			BlockchainNetwork.BASE_SEPOLIA to ContractAddress("0x036CbD53842c5426634e7929541eC2318f3dCF7e"),
		)

	/** 결제가 `SUCCEEDED`로 확정되는 데 필요한 블록 확인 수. `docs/`에 값이 없어 고정한 MVP 상수다. */
	const val REQUIRED_CONFIRMATION_COUNT: Int = 12

	fun expectedChainId(network: BlockchainNetwork): ChainId = EXPECTED_CHAIN_IDS[network] ?: unsupportedNetwork(network)

	fun expectedUsdcContractAddress(network: BlockchainNetwork): ContractAddress =
		EXPECTED_USDC_CONTRACT_ADDRESSES[network] ?: unsupportedNetwork(network)

	private fun unsupportedNetwork(network: BlockchainNetwork): Nothing = error("지원하지 않는 네트워크입니다: ${network.code}")
}
