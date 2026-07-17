package paytech.practice.pay.api.payment.web

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.application.payment.CreatePaymentCommand
import paytech.practice.pay.application.payment.CreatePaymentUseCase
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.WalletAddress

/**
 * 결제 생성 API(`docs/architecture/identity-access-api-key.md`의
 * `POST /api/v1/payments`)를 노출하는 inbound Adapter다.
 *
 * [CreatePaymentRequest]의 문자열 필드를 도메인 Value Object로 바꾸는 것까지만
 * 이 계층의 책임이다 — 그 값들이 유효한지(`WalletAddress` 형식, 금액이 양수인지
 * 등)는 각 Value Object의 `init` 블록이 검증하며, 검증 실패는
 * [PaymentApiExceptionHandler]가 400으로 변환한다.
 */
@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
	private val createPaymentUseCase: CreatePaymentUseCase,
) {
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	fun createPayment(
		@Valid @RequestBody request: CreatePaymentRequest,
	): CreatePaymentResponse {
		val command =
			CreatePaymentCommand(
				merchantId = MerchantId(request.merchantId),
				merchantOrderId = MerchantOrderId(request.merchantOrderId),
				orderName = request.orderName,
				orderAmount = Money(request.orderAmount),
				network = BlockchainNetwork(request.network),
				receivingWallet = WalletAddress(request.receivingWallet),
				successUrl = HttpUrl(request.successUrl),
				cancelUrl = request.cancelUrl?.let { HttpUrl(it) },
			)

		val result = createPaymentUseCase.execute(command)

		return CreatePaymentResponse(
			paymentId = result.paymentId.value,
			checkoutSessionId = result.checkoutSessionId.value,
		)
	}
}
