package paytech.practice.pay.application.checkout

import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.shared.WalletAddress

/** [ConnectCheckoutWalletUseCase]의 결과다. */
data class ConnectCheckoutWalletResult(
	val checkoutSessionId: CheckoutSessionId,
	val checkoutSessionStatus: CheckoutSessionStatus,
	val connectedWallet: WalletAddress,
)
