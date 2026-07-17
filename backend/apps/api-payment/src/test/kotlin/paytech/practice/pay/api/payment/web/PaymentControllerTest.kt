package paytech.practice.pay.api.payment.web

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.slot
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.application.payment.CreatePaymentCommand
import paytech.practice.pay.application.payment.CreatePaymentResult
import paytech.practice.pay.application.payment.CreatePaymentUseCase
import paytech.practice.pay.application.payment.MerchantCannotAcceptPaymentsException
import paytech.practice.pay.application.payment.MerchantNotFoundException
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.PaymentId
import tools.jackson.databind.ObjectMapper

private fun validRequest(): CreatePaymentRequest =
	CreatePaymentRequest(
		merchantId = "mrc_test_001",
		merchantOrderId = "order-001",
		orderName = "테스트 주문",
		orderAmount = 10_000,
		network = "BASE_SEPOLIA",
		receivingWallet = "0x" + "a".repeat(40),
		successUrl = "https://merchant.example.com/success",
		cancelUrl = "https://merchant.example.com/cancel",
	)

@WebMvcTest(PaymentController::class)
class PaymentControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var objectMapper: ObjectMapper

	@MockkBean
	lateinit var createPaymentUseCase: CreatePaymentUseCase

	init {
		extensions(SpringExtension)

		test("valid request returns 201 with the created payment/checkout session ids") {
			val commandSlot = slot<CreatePaymentCommand>()
			every { createPaymentUseCase.execute(capture(commandSlot)) } returns
				CreatePaymentResult(PaymentId("pay_001"), CheckoutSessionId("cs_001"))

			mockMvc
				.perform(
					post("/api/v1/payments")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isCreated)
				.andExpect(jsonPath("$.paymentId").value("pay_001"))
				.andExpect(jsonPath("$.checkoutSessionId").value("cs_001"))

			commandSlot.captured.merchantId shouldBe MerchantId("mrc_test_001")
		}

		test("blank merchantOrderId returns 400") {
			mockMvc
				.perform(
					post("/api/v1/payments")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest().copy(merchantOrderId = ""))),
				).andExpect(status().isBadRequest)
		}

		test("an invalid wallet address returns 400") {
			mockMvc
				.perform(
					post("/api/v1/payments")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest().copy(receivingWallet = "not-a-wallet"))),
				).andExpect(status().isBadRequest)
		}

		test("MerchantNotFoundException from the use case returns 404") {
			every { createPaymentUseCase.execute(any()) } throws MerchantNotFoundException(MerchantId("mrc_test_001"))

			mockMvc
				.perform(
					post("/api/v1/payments")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isNotFound)
		}

		test("MerchantCannotAcceptPaymentsException from the use case returns 409") {
			every { createPaymentUseCase.execute(any()) } throws MerchantCannotAcceptPaymentsException(MerchantId("mrc_test_001"))

			mockMvc
				.perform(
					post("/api/v1/payments")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isConflict)
		}
	}
}
