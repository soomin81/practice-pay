package paytech.practice.pay.application.checkout

import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.shared.WalletAddress

/**
 * [ConnectCheckoutWalletUseCase]의 입력이다.
 *
 * 고객이 체크아웃 페이지에서 외부 EVM 지갑(MetaMask 등)을 연결한 시점의 입력이다 —
 * `docs/domain/state-transitions.md`의 `CheckoutSession` 상태 중
 * `(CREATED 또는) OPEN → WALLET_CONNECTED` 전이를 일으킨다.
 */
data class ConnectCheckoutWalletCommand(
	val checkoutSessionId: CheckoutSessionId,
	val walletAddress: WalletAddress,
)
