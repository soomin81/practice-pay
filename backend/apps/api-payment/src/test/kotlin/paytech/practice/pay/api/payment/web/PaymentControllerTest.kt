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
import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.PaymentId
import tools.jackson.databind.ObjectMapper

private val MERCHANT_ID = MerchantId("mrc_test_001")

private fun validRequest(): CreatePaymentRequest =
	CreatePaymentRequest(
		merchantOrderId = "order-001",
		orderName = "테스트 주문",
		orderAmount = 10_000,
		network = "BASE_SEPOLIA",
		receivingWallet = "0x" + "a".repeat(40),
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

		test("an invalid wallet address returns 400") {
			mockMvc
				.perform(
					post("/api/v1/payments")
						.with(authenticatedAs("SCOPE_PAYMENT_CREATE"))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest().copy(receivingWallet = "not-a-wallet"))),
				).andExpect(status().isBadRequest)
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
