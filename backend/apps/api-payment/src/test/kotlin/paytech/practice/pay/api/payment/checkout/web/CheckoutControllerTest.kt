package paytech.practice.pay.api.payment.checkout.web

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.mockk.every
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.api.payment.config.SecurityConfig
import paytech.practice.pay.application.apikey.AuthenticateApiKeyUseCase
import paytech.practice.pay.application.checkout.CancelCheckoutSessionResult
import paytech.practice.pay.application.checkout.CancelCheckoutSessionUseCase
import paytech.practice.pay.application.checkout.CheckoutCustomerNotEditableException
import paytech.practice.pay.application.checkout.CheckoutSessionExpiredException
import paytech.practice.pay.application.checkout.CheckoutSessionNotCancellableException
import paytech.practice.pay.application.checkout.CheckoutSessionNotFoundException
import paytech.practice.pay.application.checkout.ConnectCheckoutWalletResult
import paytech.practice.pay.application.checkout.ConnectCheckoutWalletUseCase
import paytech.practice.pay.application.checkout.GetCheckoutSessionUseCase
import paytech.practice.pay.application.checkout.GetCheckoutStatusUseCase
import paytech.practice.pay.application.checkout.SubmitCheckoutCustomerResult
import paytech.practice.pay.application.checkout.SubmitCheckoutCustomerUseCase
import paytech.practice.pay.application.payment.SubmitPaymentTransactionUseCase
import paytech.practice.pay.application.port.outbound.CheckoutSessionView
import paytech.practice.pay.application.port.outbound.CheckoutStatusView
import paytech.practice.pay.domain.checkout.CheckoutSessionId
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.ExchangeRate
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.shared.WalletAddress
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant

private val SESSION_ID = CheckoutSessionId("cs_test_001")
private val NOW: Instant = Instant.parse("2026-07-19T10:00:00Z")

private fun sessionView(status: CheckoutSessionStatus = CheckoutSessionStatus.CREATED): CheckoutSessionView =
	CheckoutSessionView(
		checkoutSessionId = SESSION_ID,
		checkoutSessionStatus = status,
		expiresAt = NOW.plusSeconds(1_800),
		successUrl = HttpUrl("https://merchant.example.com/done"),
		cancelUrl = HttpUrl("https://merchant.example.com/cancel"),
		connectedWallet = null,
		orderName = "테스트 상품",
		orderAmount = Money(50_000),
		paymentId = PaymentId("pay_test_001"),
		paymentStatus = PaymentStatus.READY,
		paymentAsset = Asset.USDC,
		// Long 범위를 넘지는 않지만 JavaScript Number의 안전 범위(2^53-1)는 넘는 값이다 —
		// 응답에서 문자열로 나가는지 확인하려고 일부러 이 크기를 골랐다.
		paymentAmount = TokenAmount(9_007_199_254_740_993L),
		tokenDecimals = 6,
		network = BlockchainNetwork.BASE_SEPOLIA,
		receivingWallet = WalletAddress("0x" + "a".repeat(40)),
		appliedRate = ExchangeRate(BigDecimal("1370.250000000000")),
		quotedAt = NOW,
		quoteExpiresAt = NOW.plusSeconds(1_800),
	)

private fun statusView(
	paymentStatus: PaymentStatus = PaymentStatus.CONFIRMING,
	confirmationCount: Int = 5,
): CheckoutStatusView =
	CheckoutStatusView(
		checkoutSessionStatus = CheckoutSessionStatus.PAYMENT_SUBMITTED,
		paymentStatus = paymentStatus,
		confirmationCount = confirmationCount,
		transactionHash = null,
		failureReason = null,
		successUrl = HttpUrl("https://merchant.example.com/done"),
		cancelUrl = null,
	)

/**
 * `SecurityConfig`를 Import해서 **실제 인가 규칙까지** 검증한다.
 *
 * 이 테스트의 핵심은 개별 엔드포인트 동작이 아니라 **한 앱에 인증 모델 둘이 공존하는
 * 상태가 깨지지 않는지**다(`docs/architecture/checkout-api.md`의 2.1) — 체크아웃은
 * 인증 없이 열려 있어야 하고, 같은 앱의 `POST /api/v1/payments`는 그대로
 * `SCOPE_PAYMENT_CREATE`를 요구해야 한다. 후자가 깨지는 것이 이 병합에서 가장
 * 위험한 회귀라 마지막 두 테스트로 못박아 뒀다.
 */
@WebMvcTest(CheckoutController::class)
@Import(SecurityConfig::class)
@TestPropertySource(properties = ["app.checkout.allowed-origins=http://localhost:3000"])
class CheckoutControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var objectMapper: ObjectMapper

	@MockkBean
	lateinit var getCheckoutSessionUseCase: GetCheckoutSessionUseCase

	@MockkBean
	lateinit var getCheckoutStatusUseCase: GetCheckoutStatusUseCase

	@MockkBean
	lateinit var submitCheckoutCustomerUseCase: SubmitCheckoutCustomerUseCase

	@MockkBean
	lateinit var connectCheckoutWalletUseCase: ConnectCheckoutWalletUseCase

	@MockkBean
	lateinit var submitPaymentTransactionUseCase: SubmitPaymentTransactionUseCase

	@MockkBean
	lateinit var cancelCheckoutSessionUseCase: CancelCheckoutSessionUseCase

	@MockkBean
	lateinit var authenticateApiKeyUseCase: AuthenticateApiKeyUseCase

	init {
		extensions(SpringExtension)

		test("GET session returns 200 without any authentication") {
			every { getCheckoutSessionUseCase.execute(SESSION_ID) } returns sessionView()

			mockMvc
				.perform(get("/checkout/sessions/${SESSION_ID.value}"))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.checkoutSessionId").value(SESSION_ID.value))
				.andExpect(jsonPath("$.payment.chainId").value(84532))
				.andExpect(jsonPath("$.payment.tokenContractAddress").value("0x036CbD53842c5426634e7929541eC2318f3dCF7e"))
				.andExpect(jsonPath("$.payment.requiredConfirmationCount").value(12))
		}

		test("USDC amount is serialized as a string, not a number") {
			every { getCheckoutSessionUseCase.execute(SESSION_ID) } returns sessionView()

			// JavaScript Number의 안전 정수 범위를 넘는 값이 숫자로 직렬화되면
			// 브라우저에서 조용히 정밀도를 잃는다 — 문자열이어야 한다.
			mockMvc
				.perform(get("/checkout/sessions/${SESSION_ID.value}"))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.payment.amount").value("9007199254740993"))
		}

		test("GET status returns 200 and omits redirectUrl while still confirming") {
			every { getCheckoutStatusUseCase.execute(SESSION_ID) } returns statusView()

			mockMvc
				.perform(get("/checkout/sessions/${SESSION_ID.value}/status"))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.confirmationCount").value(5))
				.andExpect(jsonPath("$.requiredConfirmationCount").value(12))
				.andExpect(jsonPath("$.redirectUrl").doesNotExist())
		}

		test("GET status exposes redirectUrl once the payment SUCCEEDED") {
			every { getCheckoutStatusUseCase.execute(SESSION_ID) } returns statusView(paymentStatus = PaymentStatus.SUCCEEDED)

			mockMvc
				.perform(get("/checkout/sessions/${SESSION_ID.value}/status"))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.redirectUrl").value("https://merchant.example.com/done"))
		}

		test("POST customer returns 200 without authentication and echoes masked values only") {
			every { submitCheckoutCustomerUseCase.execute(any()) } returns
				SubmitCheckoutCustomerResult(
					checkoutSessionId = SESSION_ID,
					checkoutSessionStatus = CheckoutSessionStatus.OPEN,
					nameMasked = "홍*동",
					emailMasked = "gi***@example.com",
					phoneMasked = "010-****-5678",
				)

			mockMvc
				.perform(
					post("/checkout/sessions/${SESSION_ID.value}/customer")
						.contentType(MediaType.APPLICATION_JSON)
						.content(
							objectMapper.writeValueAsString(
								SubmitCustomerRequest("홍길동", "gildong@example.com", "010-1234-5678"),
							),
						),
				).andExpect(status().isOk)
				.andExpect(jsonPath("$.checkoutSessionStatus").value("OPEN"))
				.andExpect(jsonPath("$.emailMasked").value("gi***@example.com"))
		}

		/** 형식 검증은 도메인 VO가 한다 — 컨트롤러는 그 예외를 400으로 흘려보낸다. */
		test("a malformed email returns 400") {
			mockMvc
				.perform(
					post("/checkout/sessions/${SESSION_ID.value}/customer")
						.contentType(MediaType.APPLICATION_JSON)
						.content(
							objectMapper.writeValueAsString(
								SubmitCustomerRequest("홍길동", "not-an-email", "010-1234-5678"),
							),
						),
				).andExpect(status().isBadRequest)
		}

		test("submitting customer info after the payment was submitted returns 409") {
			every { submitCheckoutCustomerUseCase.execute(any()) } throws
				CheckoutCustomerNotEditableException(SESSION_ID, CheckoutSessionStatus.PAYMENT_SUBMITTED)

			mockMvc
				.perform(
					post("/checkout/sessions/${SESSION_ID.value}/customer")
						.contentType(MediaType.APPLICATION_JSON)
						.content(
							objectMapper.writeValueAsString(
								SubmitCustomerRequest("홍길동", "gildong@example.com", "010-1234-5678"),
							),
						),
				).andExpect(status().isConflict)
		}

		test("POST wallet returns 200 without authentication") {
			every { connectCheckoutWalletUseCase.execute(any()) } returns
				ConnectCheckoutWalletResult(
					checkoutSessionId = SESSION_ID,
					checkoutSessionStatus = CheckoutSessionStatus.WALLET_CONNECTED,
					connectedWallet = WalletAddress("0x" + "b".repeat(40)),
				)

			mockMvc
				.perform(
					post("/checkout/sessions/${SESSION_ID.value}/wallet")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(ConnectWalletRequest("0x" + "b".repeat(40)))),
				).andExpect(status().isOk)
				.andExpect(jsonPath("$.checkoutSessionStatus").value("WALLET_CONNECTED"))
		}

		test("an invalid wallet address returns 400") {
			mockMvc
				.perform(
					post("/checkout/sessions/${SESSION_ID.value}/wallet")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(ConnectWalletRequest("not-a-wallet"))),
				).andExpect(status().isBadRequest)
		}

		test("a wrong-state transition from the domain returns 409") {
			every { connectCheckoutWalletUseCase.execute(any()) } throws
				IllegalStateException("CheckoutSession 상태를 WALLET_CONNECTED 에서 WALLET_CONNECTED (으)로 전이할 수 없습니다.")

			mockMvc
				.perform(
					post("/checkout/sessions/${SESSION_ID.value}/wallet")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(ConnectWalletRequest("0x" + "b".repeat(40)))),
				).andExpect(status().isConflict)
		}

		test("an unknown session returns 404") {
			every { getCheckoutSessionUseCase.execute(any()) } throws CheckoutSessionNotFoundException(SESSION_ID)

			mockMvc
				.perform(get("/checkout/sessions/cs_no_such_session"))
				.andExpect(status().isNotFound)
		}

		test("cancelling an expired session returns 410 Gone, not 409") {
			every { cancelCheckoutSessionUseCase.execute(any()) } throws CheckoutSessionExpiredException(SESSION_ID)

			mockMvc
				.perform(post("/checkout/sessions/${SESSION_ID.value}/cancel"))
				.andExpect(status().isGone)
		}

		test("cancelling after PAYMENT_SUBMITTED returns 409") {
			every { cancelCheckoutSessionUseCase.execute(any()) } throws
				CheckoutSessionNotCancellableException(SESSION_ID, CheckoutSessionStatus.PAYMENT_SUBMITTED)

			mockMvc
				.perform(post("/checkout/sessions/${SESSION_ID.value}/cancel"))
				.andExpect(status().isConflict)
		}

		test("cancel returns 200 with the merchant cancelUrl") {
			every { cancelCheckoutSessionUseCase.execute(any()) } returns
				CancelCheckoutSessionResult(
					checkoutSessionId = SESSION_ID,
					checkoutSessionStatus = CheckoutSessionStatus.CANCELLED,
					cancelUrl = HttpUrl("https://merchant.example.com/cancel"),
				)

			mockMvc
				.perform(post("/checkout/sessions/${SESSION_ID.value}/cancel"))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.redirectUrl").value("https://merchant.example.com/cancel"))
		}

		// ── 인가 회귀 방지 ────────────────────────────────────────────────────
		// 체크아웃을 permitAll로 열면서 같은 앱의 결제 생성 API까지 열리지 않았는지
		// 확인한다. 이 두 테스트가 깨지면 가맹점 전용 API가 무인증으로 노출된 것이다.

		test("REGRESSION: POST /api/v1/payments still rejects unauthenticated calls") {
			val response =
				mockMvc
					.perform(
						post("/api/v1/payments")
							.contentType(MediaType.APPLICATION_JSON)
							.content("""{"merchantOrderId":"o-1"}"""),
					).andReturn()
					.response

			// 체크아웃 permitAll이 이 경로까지 덮으면 200/400이 나온다 — 그건 회귀다.
			(response.status == 401 || response.status == 403) shouldBe true
		}

		test("REGRESSION: an unknown non-checkout path is still not public") {
			val response = mockMvc.perform(get("/api/v1/merchants")).andReturn().response

			(response.status == 401 || response.status == 403) shouldBe true
		}
	}
}
