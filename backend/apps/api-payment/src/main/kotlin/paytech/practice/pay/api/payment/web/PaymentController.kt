package paytech.practice.pay.api.payment.web

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.api.payment.security.ApiKeyPrincipal
import paytech.practice.pay.application.payment.CreatePaymentCommand
import paytech.practice.pay.application.payment.CreatePaymentUseCase
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.shared.Money

/**
 * 결제 생성 API(`docs/architecture/identity-access-api-key.md`의
 * `POST /api/v1/payments`)를 노출하는 inbound Adapter다.
 *
 * `merchantId`는 요청 본문이 아니라 `@AuthenticationPrincipal`로 주입받는
 * [ApiKeyPrincipal]에서 가져온다 — `SecurityConfig`가 이 경로에 `SCOPE_PAYMENT_CREATE`
 * 권한을 요구하도록 이미 막아뒀으므로, 이 메서드가 실행된다는 것 자체가 유효한
 * API Key로 인증됐다는 뜻이다.
 *
 * 수취 지갑도 요청 본문에 없다 — `CreatePaymentUseCase`가 `ReceivingWalletRegistry`(서버
 * 설정)에서 꺼낸다(`docs/architecture/mvp-scope.md`의 "수취 지갑 귀속").
 *
 * [CreatePaymentRequest]의 나머지 문자열 필드를 도메인 Value Object로 바꾸는 것까지만
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
		@AuthenticationPrincipal principal: ApiKeyPrincipal,
	): CreatePaymentResponse {
		val command =
			CreatePaymentCommand(
				merchantId = principal.merchantId,
				merchantOrderId = MerchantOrderId(request.merchantOrderId),
				orderName = request.orderName,
				orderAmount = Money(request.orderAmount),
				network = BlockchainNetwork(request.network),
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
