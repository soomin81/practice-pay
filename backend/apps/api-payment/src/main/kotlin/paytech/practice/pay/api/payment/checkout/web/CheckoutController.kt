package paytech.practice.pay.api.payment.checkout.web

import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.application.checkout.CancelCheckoutSessionCommand
import paytech.practice.pay.application.checkout.CancelCheckoutSessionUseCase
import paytech.practice.pay.application.checkout.ConnectCheckoutWalletCommand
import paytech.practice.pay.application.checkout.ConnectCheckoutWalletUseCase
import paytech.practice.pay.application.checkout.GetCheckoutSessionUseCase
import paytech.practice.pay.application.checkout.GetCheckoutStatusUseCase
import paytech.practice.pay.application.checkout.SubmitCheckoutCustomerCommand
import paytech.practice.pay.application.checkout.SubmitCheckoutCustomerUseCase
import paytech.practice.pay.application.payment.PaymentNetworkConfig
import paytech.practice.pay.application.payment.SubmitPaymentTransactionCommand
import paytech.practice.pay.application.payment.SubmitPaymentTransactionUseCase
import paytech.practice.pay.application.port.outbound.CheckoutSessionView
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.customer.CustomerEmail
import paytech.practice.pay.domain.customer.CustomerName
import paytech.practice.pay.domain.customer.CustomerPhone
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.WalletAddress

/**
 * 고객 브라우저가 호출하는 Hosted Checkout API다
 * (`docs/architecture/checkout-api.md`).
 *
 * **이 앱에서 유일하게 인증 없이 열린 경로다.** 고객은 계정이 없고
 * `checkoutSessionId`를 아는 것 자체가 권한이다 — `SecurityConfig`가 이 기준 경로
 * 아래 전체를 `permitAll`로 열고, 같은 앱의 `POST /api/v1/payments`는 여전히
 * `SCOPE_PAYMENT_CREATE`를 요구한다. 그 대비가 깨지지 않는지는
 * `CheckoutControllerTest`가 검증한다.
 *
 * 패키지를 `api.payment.web`과 나눠 `api.payment.checkout.web`에 둔 이유는
 * `docs/architecture/checkout-api.md`의 2.1에 있다 — 나중에 별도 앱으로 떼어낼 때
 * 디렉토리 이동으로 끝나게 하려는 것이다.
 */
@RestController
@RequestMapping("/checkout/sessions")
class CheckoutController(
	private val getCheckoutSessionUseCase: GetCheckoutSessionUseCase,
	private val getCheckoutStatusUseCase: GetCheckoutStatusUseCase,
	private val submitCheckoutCustomerUseCase: SubmitCheckoutCustomerUseCase,
	private val connectCheckoutWalletUseCase: ConnectCheckoutWalletUseCase,
	private val submitPaymentTransactionUseCase: SubmitPaymentTransactionUseCase,
	private val cancelCheckoutSessionUseCase: CancelCheckoutSessionUseCase,
) {
	@GetMapping("/{checkoutSessionId}")
	fun getSession(
		@PathVariable checkoutSessionId: String,
	): CheckoutSessionResponse = getCheckoutSessionUseCase.execute(CheckoutSessionId(checkoutSessionId)).toResponse()

	@GetMapping("/{checkoutSessionId}/status")
	fun getStatus(
		@PathVariable checkoutSessionId: String,
	): CheckoutStatusResponse {
		val view = getCheckoutStatusUseCase.execute(CheckoutSessionId(checkoutSessionId))

		return CheckoutStatusResponse(
			checkoutSessionStatus = view.checkoutSessionStatus.name,
			paymentStatus = view.paymentStatus.name,
			confirmationCount = view.confirmationCount,
			requiredConfirmationCount = PaymentNetworkConfig.REQUIRED_CONFIRMATION_COUNT,
			transactionHash = view.transactionHash?.value,
			failureReason = view.failureReason?.name,
			// 성공했을 때만 돌아갈 곳을 알려준다 — 프론트가 리다이렉트 시점을 스스로 추론하지 않게 한다.
			redirectUrl = view.successUrl.value.takeIf { view.paymentStatus == PaymentStatus.SUCCEEDED },
		)
	}

	/**
	 * 구매자 정보(이름·이메일·휴대전화)를 받는다 — **지갑 연결보다 앞선 단계**다(ADR-008).
	 *
	 * 서명 이후에 입력을 요구하면 돈은 나갔는데 결제가 미완인 창이 생긴다. 순서를 강제하는
	 * 것은 프론트이고, API는 `PAYMENT_SUBMITTED` 전이라면 받는다.
	 */
	@PostMapping("/{checkoutSessionId}/customer")
	fun submitCustomer(
		@PathVariable checkoutSessionId: String,
		@Valid @RequestBody request: SubmitCustomerRequest,
	): SubmitCustomerResponse {
		val command =
			SubmitCheckoutCustomerCommand(
				checkoutSessionId = CheckoutSessionId(checkoutSessionId),
				name = CustomerName(request.name),
				email = CustomerEmail(request.email),
				phone = CustomerPhone(request.phone),
			)

		val result = submitCheckoutCustomerUseCase.execute(command)

		return SubmitCustomerResponse(
			checkoutSessionId = result.checkoutSessionId.value,
			checkoutSessionStatus = result.checkoutSessionStatus.name,
			nameMasked = result.nameMasked,
			emailMasked = result.emailMasked,
			phoneMasked = result.phoneMasked,
		)
	}

	@PostMapping("/{checkoutSessionId}/wallet")
	fun connectWallet(
		@PathVariable checkoutSessionId: String,
		@Valid @RequestBody request: ConnectWalletRequest,
	): ConnectWalletResponse {
		val command =
			ConnectCheckoutWalletCommand(
				checkoutSessionId = CheckoutSessionId(checkoutSessionId),
				walletAddress = WalletAddress(request.walletAddress),
			)

		val result = connectCheckoutWalletUseCase.execute(command)

		return ConnectWalletResponse(
			checkoutSessionId = result.checkoutSessionId.value,
			checkoutSessionStatus = result.checkoutSessionStatus.name,
			connectedWallet = result.connectedWallet.value,
		)
	}

	@PostMapping("/{checkoutSessionId}/transaction")
	fun submitTransaction(
		@PathVariable checkoutSessionId: String,
		@Valid @RequestBody request: SubmitTransactionRequest,
	): SubmitTransactionResponse {
		val command =
			SubmitPaymentTransactionCommand(
				checkoutSessionId = CheckoutSessionId(checkoutSessionId),
				transactionHash = TransactionHash(request.transactionHash),
			)

		val result = submitPaymentTransactionUseCase.execute(command)

		return SubmitTransactionResponse(
			blockchainTransactionId = result.blockchainTransactionId.value,
			checkoutSessionId = result.checkoutSessionId.value,
			checkoutSessionStatus = result.checkoutSessionStatus.name,
			paymentId = result.paymentId.value,
			paymentStatus = result.paymentStatus.name,
		)
	}

	@PostMapping("/{checkoutSessionId}/cancel")
	fun cancel(
		@PathVariable checkoutSessionId: String,
	): CancelCheckoutSessionResponse {
		val result = cancelCheckoutSessionUseCase.execute(CancelCheckoutSessionCommand(CheckoutSessionId(checkoutSessionId)))

		return CancelCheckoutSessionResponse(
			checkoutSessionId = result.checkoutSessionId.value,
			checkoutSessionStatus = result.checkoutSessionStatus.name,
			redirectUrl = result.cancelUrl?.value,
		)
	}

	private fun CheckoutSessionView.toResponse(): CheckoutSessionResponse =
		CheckoutSessionResponse(
			checkoutSessionId = checkoutSessionId.value,
			checkoutSessionStatus = checkoutSessionStatus.name,
			expiresAt = expiresAt,
			successUrl = successUrl.value,
			cancelUrl = cancelUrl?.value,
			connectedWallet = connectedWallet?.value,
			order =
				CheckoutOrderResponse(
					orderName = orderName,
					orderAmount = orderAmount.amount,
					// 이 코드베이스에서 Money는 언제나 KRW를 뜻한다(MVP는 KRW→USDC 한 쌍만 지원).
					orderCurrency = "KRW",
				),
			payment =
				CheckoutPaymentResponse(
					paymentId = paymentId.value,
					paymentStatus = paymentStatus.name,
					asset = paymentAsset.code,
					// Minor Unit은 문자열로 — JavaScript Number의 안전 정수 범위를 넘을 수 있다.
					amount = paymentAmount.amountMinor.toString(),
					tokenDecimals = tokenDecimals,
					network = network.code,
					chainId = PaymentNetworkConfig.expectedChainId(network).value,
					tokenContractAddress = PaymentNetworkConfig.expectedUsdcContractAddress(network).value,
					receivingWallet = receivingWallet.value,
					requiredConfirmationCount = PaymentNetworkConfig.REQUIRED_CONFIRMATION_COUNT,
				),
			quote =
				CheckoutQuoteResponse(
					appliedRate = appliedRate.value.toPlainString(),
					quotedAt = quotedAt,
					expiresAt = quoteExpiresAt,
				),
		)
}
