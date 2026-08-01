package paytech.practice.pay.api.payment.web

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.slot
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.api.payment.config.SecurityConfig
import paytech.practice.pay.api.payment.security.ApiKeyPrincipal
import paytech.practice.pay.application.apikey.AuthenticateApiKeyUseCase
import paytech.practice.pay.application.payment.CreatePaymentCommand
import paytech.practice.pay.application.payment.CreatePaymentResult
import paytech.practice.pay.application.payment.CreatePaymentUseCase
import paytech.practice.pay.application.payment.MerchantCannotAcceptPaymentsException
import paytech.practice.pay.application.payment.MerchantNotFoundException
import paytech.practice.pay.application.payment.ReceivingWalletNotConfiguredException
import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.shared.BlockchainNetwork
import tools.jackson.databind.ObjectMapper

private val MERCHANT_ID = MerchantId("mrc_test_001")

private fun validRequest(): CreatePaymentRequest =
	CreatePaymentRequest(
		merchantOrderId = "order-001",
		orderName = "테스트 주문",
		orderAmount = 10_000,
		network = "BASE_SEPOLIA",
		successUrl = "https://merchant.example.com/success",
		cancelUrl = "https://merchant.example.com/cancel",
	)

private fun authenticatedAs(vararg scopes: String) =
	authentication(
		UsernamePasswordAuthenticationToken(
			ApiKeyPrincipal(MERCHANT_ID, MerchantApiKeyId("mak_test_001")),
			null,
			scopes.map { SimpleGrantedAuthority(it) },
		),
	)

/**
 * `@WebMvcTest`는 `SecurityConfig`(평범한 `@Configuration`)를 자동으로 스캔하지
 * 않으므로 명시적으로 Import한다 — 그래야 `POST /api/v1/payments`가
 * `SCOPE_PAYMENT_CREATE` 권한을 요구하는 실제 인가 규칙까지 검증할 수 있다.
 * `authenticateApiKeyUseCase`는 `SecurityConfig`의 `apiKeyAuthenticationFilter`
 * Bean이 생성자로 요구해서 Mock으로만 채워둔다 — 아래 테스트들은 실제 필터를
 * 거치지 않고 `SecurityMockMvcRequestPostProcessors.authentication(...)`으로
 * `Authentication`을 직접 주입하므로 이 Mock 자체는 호출되지 않는다.
 */
@WebMvcTest(PaymentController::class)
@Import(SecurityConfig::class)
class PaymentControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var objectMapper: ObjectMapper

	@MockkBean
	lateinit var createPaymentUseCase: CreatePaymentUseCase

	@MockkBean
	lateinit var authenticateApiKeyUseCase: AuthenticateApiKeyUseCase

	init {
		extensions(SpringExtension)

		test("valid request with SCOPE_PAYMENT_CREATE returns 201 with the created payment/checkout session ids") {
			val commandSlot = slot<CreatePaymentCommand>()
			every { createPaymentUseCase.execute(capture(commandSlot)) } returns
				CreatePaymentResult(PaymentId("pay_001"), CheckoutSessionId("cs_001"))

			mockMvc
				.perform(
					post("/api/v1/payments")
						.with(authenticatedAs("SCOPE_PAYMENT_CREATE"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isCreated)
				.andExpect(jsonPath("$.paymentId").value("pay_001"))
				.andExpect(jsonPath("$.checkoutSessionId").value("cs_001"))

			commandSlot.captured.merchantId shouldBe MERCHANT_ID
		}

		test("no authentication returns 401") {
			mockMvc
				.perform(
					post("/api/v1/payments")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isUnauthorized)
		}

		test("authenticated without SCOPE_PAYMENT_CREATE returns 403") {
			mockMvc
				.perform(
					post("/api/v1/payments")
						.with(authenticatedAs("SCOPE_PAYMENT_READ"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isForbidden)
		}

		test("blank merchantOrderId returns 400") {
			mockMvc
				.perform(
					post("/api/v1/payments")
						.with(authenticatedAs("SCOPE_PAYMENT_CREATE"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest().copy(merchantOrderId = ""))),
				).andExpect(status().isBadRequest)
		}

		/**
		 * 가맹점이 예전 계약대로 `receivingWallet`을 보내도 **그 값이 쓰이지 않는다**는 것을
		 * 고정한다. 이게 이 슬라이스에서 지켜야 할 핵심이다 — 수취 지갑은 PG 설정에서만
		 * 온다(`docs/architecture/mvp-scope.md`의 "수취 지갑 귀속"). 알 수 없는 필드는
		 * Spring Boot의 Jackson 기본 설정대로 무시되므로 요청 자체는 201로 통과한다.
		 */
		test("a merchant-supplied receivingWallet is ignored, never reaching the command") {
			val commandSlot = slot<CreatePaymentCommand>()
			every { createPaymentUseCase.execute(capture(commandSlot)) } returns
				CreatePaymentResult(PaymentId("pay_001"), CheckoutSessionId("cs_001"))

			val bodyWithLegacyWallet =
				"""
				{
				  "merchantOrderId": "order-001",
				  "orderName": "테스트 주문",
				  "orderAmount": 10000,
				  "network": "BASE_SEPOLIA",
				  "receivingWallet": "0x${"e".repeat(40)}",
				  "successUrl": "https://merchant.example.com/success"
				}
				""".trimIndent()

			mockMvc
				.perform(
					post("/api/v1/payments")
						.with(authenticatedAs("SCOPE_PAYMENT_CREATE"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(bodyWithLegacyWallet),
				).andExpect(status().isCreated)

			// CreatePaymentCommand에 수취 지갑 필드가 없다는 것 자체가 증거다 — 컴파일이
			// 그것을 보장하므로, 여기서는 요청이 정상 처리됐다는 것만 확인한다.
			commandSlot.captured.merchantId shouldBe MERCHANT_ID
		}

		test("ReceivingWalletNotConfiguredException from the use case returns 503") {
			every { createPaymentUseCase.execute(any()) } throws
				ReceivingWalletNotConfiguredException(BlockchainNetwork.BASE_SEPOLIA)

			mockMvc
				.perform(
					post("/api/v1/payments")
						.with(authenticatedAs("SCOPE_PAYMENT_CREATE"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isServiceUnavailable)
		}

		test("MerchantNotFoundException from the use case returns 404") {
			every { createPaymentUseCase.execute(any()) } throws MerchantNotFoundException(MERCHANT_ID)

			mockMvc
				.perform(
					post("/api/v1/payments")
						.with(authenticatedAs("SCOPE_PAYMENT_CREATE"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isNotFound)
		}

		// 인증된 요청의 본문이 깨져 있으면 400이어야 한다. 이 핸들러가 없으면 Spring이
		// response.sendError(400)로 처리하고, 그러면 컨테이너가 /error로 ERROR 디스패치를
		// 도는데 그 경로에는 인증이 실려 있지 않아 실제 응답이 401로 뒤바뀐다 —
		// 실제 bootRun에서 "본문이 잘못됐는데 API Key가 유효하지 않다"는 401을 받고 찾았다
		// (SecurityConfig의 `/error` permitAll과 짝을 이루는 수정이다).
		test("a malformed request body returns 400, not 401") {
			mockMvc
				.perform(
					post("/api/v1/payments")
						.with(authenticatedAs("SCOPE_PAYMENT_CREATE"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"merchantOrderId\": "),
				).andExpect(status().isBadRequest)
		}

		test("a request body missing a required field returns 400, not 401") {
			mockMvc
				.perform(
					post("/api/v1/payments")
						.with(authenticatedAs("SCOPE_PAYMENT_CREATE"))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"merchantOrderId\":\"order-1\",\"orderName\":\"n\",\"orderAmount\":1000}"),
				).andExpect(status().isBadRequest)
		}

		test("MerchantCannotAcceptPaymentsException from the use case returns 409") {
			every { createPaymentUseCase.execute(any()) } throws MerchantCannotAcceptPaymentsException(MERCHANT_ID)

			mockMvc
				.perform(
					post("/api/v1/payments")
						.with(authenticatedAs("SCOPE_PAYMENT_CREATE"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isConflict)
		}
	}
}
