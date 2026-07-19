package paytech.practice.pay.api.payment.checkout.web

import jakarta.validation.constraints.NotBlank

/** `POST /checkout/sessions/{checkoutSessionId}/wallet`의 요청 본문이다. */
data class ConnectWalletRequest(
	@field:NotBlank
	val walletAddress: String,
)

/** 지갑 연결 결과다. */
data class ConnectWalletResponse(
	val checkoutSessionId: String,
	val checkoutSessionStatus: String,
	val connectedWallet: String,
)
