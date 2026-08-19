package paytech.practice.pay.api.payment.checkout.web

import com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippet
import com.epages.restdocs.apispec.ResourceSnippetParameters
import com.epages.restdocs.apispec.Schema
import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.api.payment.config.SecurityConfig
import paytech.practice.pay.application.apikey.AuthenticateApiKeyUseCase
import paytech.practice.pay.application.checkout.CancelCheckoutSessionResult
import paytech.practice.pay.application.checkout.CancelCheckoutSessionUseCase
import paytech.practice.pay.application.checkout.ConnectCheckoutWalletResult
import paytech.practice.pay.application.checkout.ConnectCheckoutWalletUseCase
import paytech.practice.pay.application.checkout.GetCheckoutSessionUseCase
import paytech.practice.pay.application.checkout.GetCheckoutStatusUseCase
import paytech.practice.pay.application.checkout.SubmitCheckoutCustomerResult
import paytech.practice.pay.application.checkout.SubmitCheckoutCustomerUseCase
import paytech.practice.pay.application.payment.SubmitPaymentTransactionResult
import paytech.practice.pay.application.payment.SubmitPaymentTransactionUseCase
import paytech.practice.pay.application.port.outbound.CheckoutSessionView
import paytech.practice.pay.application.port.outbound.CheckoutStatusView
import paytech.practice.pay.domain.blockchain.BlockchainTransactionId
import paytech.practice.pay.domain.blockchain.TransactionHash
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

private val SESSION_ID = CheckoutSessionId("cs_9f2c1a7b8d3e4f5a")
private val NOW: Instant = Instant.parse("2026-07-19T10:00:00Z")
private const val SESSION_ID_PARAM = "체크아웃 세션 식별자. 결제 생성 응답의 checkoutSessionId를 그대로 쓴다."

/**
 * 모든 체크아웃 엔드포인트가 공유하는 스니펫 형태를 한곳에 모은다.
 *
 * **`resource(...)`로 감싸고 `.build()`를 부르는 것이 핵심이다.** 빌더를 그대로
 * `document(identifier, resourceDetails)` 오버로드에 넘기면 컴파일도 되고 테스트도
 * 통과하지만 `responseFields`와 `pathParameters`가 조용히 사라진다 — 그 오버로드는
 * `ResourceSnippetDetails`(부모)만 읽어서 빌더 고유 필드를 버리기 때문이다. 실제로
 * 그렇게 짰다가 생성된 스펙의 스키마가 전부 빈 `type: object`로 나오는 것을 보고
 * 발견했다. **스펙을 눈으로 확인하지 않았으면 그대로 넘어갔을 종류의 오류다.**
 */
private fun checkoutResource(
	summary: String,
	description: String,
	responseSchema: String,
	responseFields: List<FieldDescriptor>,
	requestSchema: String? = null,
	requestFields: List<FieldDescriptor> = emptyList(),
): ResourceSnippet {
	val builder =
		ResourceSnippetParameters
			.builder()
			.tag("Checkout")
			.summary(summary)
			.description(description)
			.responseSchema(Schema(responseSchema))
			.pathParameters(parameterWithName("checkoutSessionId").description(SESSION_ID_PARAM))
			.responseFields(responseFields)

	requestSchema?.let { builder.requestSchema(Schema(it)) }
	if (requestFields.isNotEmpty()) {
		builder.requestFields(requestFields)
	}

	return resource(builder.build())
}

private fun sessionView(): CheckoutSessionView =
	CheckoutSessionView(
		checkoutSessionId = SESSION_ID,
		checkoutSessionStatus = CheckoutSessionStatus.CREATED,
		expiresAt = NOW.plusSeconds(1_800),
		successUrl = HttpUrl("https://merchant.example.com/order/1001/done"),
		cancelUrl = HttpUrl("https://merchant.example.com/order/1001/cancel"),
		connectedWallet = null,
		orderName = "테스트 상품",
		orderAmount = Money(50_000),
		paymentId = PaymentId("pay_3b81c2d4e5f6a7b8"),
		paymentStatus = PaymentStatus.READY,
		paymentAsset = Asset.USDC,
		paymentAmount = TokenAmount(35_893_755),
		tokenDecimals = 6,
		network = BlockchainNetwork.BASE_SEPOLIA,
		// 수취 지갑은 토큰 Contract 주소와 **반드시 달라야 한다**. 이 값이 생성되는
		// OpenAPI 예시에 그대로 실리는데, 둘이 같으면 "USDC를 USDC Contract로 보낸다"는
		// 잘못된 예시를 문서가 퍼뜨리게 된다(docs/architecture/checkout-api.md의 예시도
		// 이 둘을 구분해 두고 있다).
		receivingWallet = WalletAddress("0xAbC1000000000000000000000000000000000001"),
		appliedRate = ExchangeRate(BigDecimal("1393.000000000000")),
		quotedAt = NOW,
		quoteExpiresAt = NOW.plusSeconds(1_800),
	)

/**
 * 통과한 테스트에서 OpenAPI 스펙(`build/api-spec/openapi3.yaml`)을 만든다.
 *
 * **동작 검증은 [CheckoutControllerTest]가 하고, 이 클래스는 문서화만 한다.** 나누는
 * 이유는 필드별 설명이 붙으면 테스트가 서너 배로 길어져 동작 의도가 묻히기 때문이다 —
 * 그쪽은 인가 회귀를 포함한 13개 케이스를 짧게 유지하고, 여기는 대표 경로만 문서화한다.
 *
 * 애노테이션 기반(springdoc)이 아니라 REST Docs 기반을 고른 이유는 **스펙이 거짓말을
 * 할 수 없기 때문**이다. 애노테이션은 실제 응답과 어긋나도 빌드가 통과하지만, 여기서는
 * 실제로 요청을 보내고 응답을 받아야 스니펫이 나온다. 응답에 없는 필드를 문서화하면
 * 그 자리에서 테스트가 깨진다.
 *
 * **한계 — 오류 응답은 이 방식으로 검증되지 않는다.** `@WebMvcTest`의 MockMvc는
 * 컨테이너의 ERROR 디스패치를 재현하지 않아서, 여기서 문서화한 4xx가 실제 응답과
 * 다를 수 있다(이 저장소는 잘못된 요청 본문에 400이 아니라 401이 나가던 사고를 이미
 * 겪었다 — `backend/CLAUDE.md`의 "테스트가 잡지 못하는 층"). 그래서 오류 응답은
 * 문서화 대상에서 빼고, 실제 상태 코드는 `bootRun` + `curl`로 확인해
 * `docs/architecture/checkout-api.md`의 5절에 적어 둔다.
 */
@WebMvcTest(CheckoutController::class)
@Import(SecurityConfig::class)
@AutoConfigureRestDocs
@TestPropertySource(properties = ["app.checkout.allowed-origins=http://localhost:3000"])
class CheckoutApiDocumentationTest : FunSpec() {
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

		test("document GET checkout session") {
			every { getCheckoutSessionUseCase.execute(SESSION_ID) } returns sessionView()

			val snippet =
				checkoutResource(
					summary = "체크아웃 화면 렌더용 조회",
					description =
						"체크아웃 페이지를 그리는 데 필요한 모든 정보를 한 번에 준다. 인증이 없다 — " +
							"checkoutSessionId를 아는 것 자체가 권한이다. 만료·취소·완료된 세션도 그대로 " +
							"돌려주므로 프론트는 status로 화면을 나눈다.",
					responseSchema = "CheckoutSessionResponse",
					responseFields =
						listOf(
							fieldWithPath("checkoutSessionId").description("체크아웃 세션 식별자"),
							fieldWithPath("checkoutSessionStatus")
								.description("CREATED | OPEN | WALLET_CONNECTED | PAYMENT_SUBMITTED | COMPLETED | EXPIRED | CANCELLED"),
							fieldWithPath("expiresAt").description("세션 만료 시각(UTC). 지나면 변경 요청이 410으로 거부된다."),
							fieldWithPath("successUrl").description("결제 성공 후 고객을 보낼 가맹점 URL"),
							fieldWithPath("cancelUrl")
								.type(JsonFieldType.STRING)
								.description("취소 시 돌아갈 가맹점 URL. 가맹점이 지정하지 않았으면 null.")
								.optional(),
							// **예시 값이 null인 필드는 반드시 .type()을 명시한다.** 값이 null이면
							// restdocs-api-spec이 타입을 추론하지 못해 그 필드를 스펙에서 통째로
							// 빠뜨린다 — 실제로 connectedWallet/redirectUrl/failureReason 셋이
							// 생성된 타입에 없어서 프론트 타입 체크에서 발견됐다.
							fieldWithPath("connectedWallet")
								.type(JsonFieldType.STRING)
								.description("연결된 고객 지갑 주소. 연결 전에는 null.")
								.optional(),
							// 중첩 객체 자신도 문서화해야 required로 잡힌다. 잎 필드(order.orderName)만
							// 적으면 부모(order)가 optional로 생성돼, 프론트가 매번 undefined 검사를
							// 해야 한다 — 실제로는 항상 존재하는 값이다.
							fieldWithPath("order").description("가맹점 주문 정보"),
							fieldWithPath("order.orderName").description("가맹점이 정한 주문 이름"),
							fieldWithPath("order.orderAmount").description("주문 금액(KRW, 원 단위 정수)"),
							fieldWithPath("order.orderCurrency").description("주문 통화. MVP는 항상 KRW."),
							fieldWithPath("payment").description("이 주문에 대응하는 결제·전송 정보"),
							fieldWithPath("payment.paymentId").description("결제 식별자"),
							fieldWithPath("payment.paymentStatus")
								.description("CREATED | READY | PROCESSING | CONFIRMING | SUCCEEDED | EXPIRED | FAILED"),
							fieldWithPath("payment.asset").description("결제 자산. MVP는 USDC."),
							fieldWithPath("payment.amount")
								.description(
									"전송할 토큰 금액(Minor Unit). 문자열이다 — JavaScript Number의 안전 정수 범위를 " +
										"넘을 수 있어서 숫자로 다루면 정밀도를 잃는다. BigInt나 문자열로 처리한다.",
								),
							fieldWithPath("payment.tokenDecimals").description("토큰 소수 자릿수. USDC는 6."),
							fieldWithPath("payment.network").description("전송할 블록체인 네트워크"),
							fieldWithPath("payment.chainId").description("EVM Chain ID. 상수로 박지 말고 이 응답에서 받아 쓴다."),
							fieldWithPath("payment.tokenContractAddress")
								.description("USDC Contract 주소. 토큰을 Symbol로 판단하지 않고 (네트워크, Contract) 조합으로 다룬다."),
							fieldWithPath("payment.receivingWallet").description("이 금액을 보낼 수취 지갑 주소"),
							fieldWithPath("payment.requiredConfirmationCount").description("결제 확정에 필요한 Confirmation 수"),
							fieldWithPath("quote").description("적용된 환율 견적"),
							fieldWithPath("quote.appliedRate")
								.description("적용 환율(KRW/USDC). 문자열로 준다 — 부동소수점 변환으로 정밀도를 잃지 않기 위해서다."),
							fieldWithPath("quote.quotedAt").description("환율 산정 시각(UTC)"),
							fieldWithPath("quote.expiresAt").description("견적 만료 시각(UTC)"),
						),
				)

			mockMvc
				.perform(get("/checkout/sessions/{checkoutSessionId}", SESSION_ID.value))
				.andExpect(status().isOk)
				.andDo(document("checkout-get-session", snippet))
		}

		test("document GET checkout status") {
			every { getCheckoutStatusUseCase.execute(SESSION_ID) } returns
				CheckoutStatusView(
					checkoutSessionStatus = CheckoutSessionStatus.PAYMENT_SUBMITTED,
					paymentStatus = PaymentStatus.CONFIRMING,
					confirmationCount = 5,
					transactionHash = TransactionHash("0x" + "7f3a".repeat(16)),
					failureReason = null,
					successUrl = HttpUrl("https://merchant.example.com/order/1001/done"),
					cancelUrl = null,
				)

			val snippet =
				checkoutResource(
					summary = "결제 상태 폴링(경량)",
					description =
						"Confirm 대기 구간에서 3초 간격으로 호출한다. 전체 조회와 나눈 이유는 폴링이 10회 이상 " +
							"반복되는데 주문·견적 정보를 매번 다시 보낼 이유가 없어서다. 최대 5분 뒤에는 폴링을 " +
							"멈추고 새로고침을 안내한다.",
					responseSchema = "CheckoutStatusResponse",
					responseFields =
						listOf(
							fieldWithPath("checkoutSessionStatus").description("체크아웃 세션 상태"),
							fieldWithPath("paymentStatus").description("결제 상태. SUCCEEDED가 되면 결제가 확정된 것이다."),
							fieldWithPath("confirmationCount").description("현재 Confirmation 수. 아직 제출 전이면 0."),
							fieldWithPath("requiredConfirmationCount").description("확정에 필요한 Confirmation 수"),
							fieldWithPath("transactionHash")
								.type(JsonFieldType.STRING)
								.description("제출된 Transaction Hash. 제출 전에는 null.")
								.optional(),
							fieldWithPath("failureReason")
								.type(JsonFieldType.STRING)
								.description("실패 사유 코드. FAILED일 때만 채워진다. 고객에게 그대로 노출하지 말고 안내 문구로 번역한다.")
								.optional(),
							fieldWithPath("redirectUrl")
								.type(JsonFieldType.STRING)
								.description(
									"결제가 SUCCEEDED가 됐을 때만 채워지는 이동 대상(successUrl). 프론트는 리다이렉트 시점을 " +
										"스스로 추론하지 말고 이 필드가 채워지는 것을 신호로 삼는다.",
								).optional(),
						),
				)

			mockMvc
				.perform(get("/checkout/sessions/{checkoutSessionId}/status", SESSION_ID.value))
				.andExpect(status().isOk)
				.andDo(document("checkout-get-status", snippet))
		}

		test("document POST submit customer") {
			every { submitCheckoutCustomerUseCase.execute(any()) } returns
				SubmitCheckoutCustomerResult(
					checkoutSessionId = SESSION_ID,
					checkoutSessionStatus = CheckoutSessionStatus.OPEN,
					nameMasked = "홍*동",
					emailMasked = "gi***@example.com",
					phoneMasked = "010-****-5678",
				)

			val snippet =
				checkoutResource(
					summary = "구매자 정보 입력",
					description =
						"고객이 이름·이메일·휴대전화를 직접 입력한다. 지갑 연결보다 앞선 단계다 — 서명 이후에 " +
							"입력을 요구하면 돈은 나갔는데 결제가 미완인 창이 생긴다. 다시 호출하면 덮어쓰고, " +
							"결제 전송을 제출한 뒤에는 409다.",
					requestSchema = "SubmitCustomerRequest",
					requestFields =
						listOf(
							fieldWithPath("name").description("구매자 이름(100자 이내)"),
							fieldWithPath("email").description("구매자 이메일. 결제에 문제가 생겼을 때의 주 연락 수단이다"),
							fieldWithPath("phone").description("국내 휴대전화 번호(01X-XXXX-XXXX, 하이픈은 있어도 없어도 된다)"),
						),
					responseSchema = "SubmitCustomerResponse",
					responseFields =
						listOf(
							fieldWithPath("checkoutSessionId").description("체크아웃 세션 식별자"),
							fieldWithPath("checkoutSessionStatus").description("CREATED였다면 이 호출로 OPEN이 된다"),
							fieldWithPath("nameMasked").description("마스킹된 이름. 응답에는 원본이 실리지 않는다"),
							fieldWithPath("emailMasked").description("마스킹된 이메일"),
							fieldWithPath("phoneMasked").description("마스킹된 휴대전화 번호"),
						),
				)

			mockMvc
				.perform(
					post("/checkout/sessions/{checkoutSessionId}/customer", SESSION_ID.value)
						.contentType(MediaType.APPLICATION_JSON)
						.content(
							objectMapper.writeValueAsString(
								SubmitCustomerRequest("홍길동", "gildong@example.com", "010-1234-5678"),
							),
						),
				).andExpect(status().isOk)
				.andDo(document("checkout-submit-customer", snippet))
		}

		test("document POST connect wallet") {
			every { connectCheckoutWalletUseCase.execute(any()) } returns
				ConnectCheckoutWalletResult(
					checkoutSessionId = SESSION_ID,
					checkoutSessionStatus = CheckoutSessionStatus.WALLET_CONNECTED,
					connectedWallet = WalletAddress("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
				)

			val snippet =
				checkoutResource(
					summary = "고객 지갑 연결",
					description =
						"고객이 지갑을 연결한 시점을 기록한다. 지갑 재연결은 지원하지 않는다 — 이미 연결된 뒤 " +
							"다시 호출하면 409다.",
					requestSchema = "ConnectWalletRequest",
					requestFields = listOf(fieldWithPath("walletAddress").description("고객이 연결한 EVM 지갑 주소(0x + 40 hex)")),
					responseSchema = "ConnectWalletResponse",
					responseFields =
						listOf(
							fieldWithPath("checkoutSessionId").description("체크아웃 세션 식별자"),
							fieldWithPath("checkoutSessionStatus").description("연결에 성공하면 WALLET_CONNECTED"),
							fieldWithPath("connectedWallet").description("연결된 지갑 주소"),
						),
				)

			mockMvc
				.perform(
					post("/checkout/sessions/{checkoutSessionId}/wallet", SESSION_ID.value)
						.contentType(MediaType.APPLICATION_JSON)
						.content(
							objectMapper.writeValueAsString(
								ConnectWalletRequest("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
							),
						),
				).andExpect(status().isOk)
				.andDo(document("checkout-connect-wallet", snippet))
		}

		test("document POST submit transaction") {
			every { submitPaymentTransactionUseCase.execute(any()) } returns
				SubmitPaymentTransactionResult(
					blockchainTransactionId = BlockchainTransactionId("btx_5c19a2b3c4d5e6f7"),
					checkoutSessionId = SESSION_ID,
					checkoutSessionStatus = CheckoutSessionStatus.PAYMENT_SUBMITTED,
					paymentId = PaymentId("pay_3b81c2d4e5f6a7b8"),
					paymentStatus = PaymentStatus.PROCESSING,
				)

			val snippet =
				checkoutResource(
					summary = "전송한 Transaction Hash 제출",
					description =
						"고객 지갑이 USDC 전송을 브로드캐스트한 뒤 그 Hash를 제출한다. 성공 응답은 " +
							"'결제가 됐다'가 아니라 '제출을 접수했다'는 뜻이다 — 확정은 배치 Worker가 하므로 " +
							"프론트는 곧바로 상태 폴링으로 넘어간다.",
					requestSchema = "SubmitTransactionRequest",
					requestFields = listOf(fieldWithPath("transactionHash").description("브로드캐스트된 전송의 Transaction Hash")),
					responseSchema = "SubmitTransactionResponse",
					responseFields =
						listOf(
							fieldWithPath("blockchainTransactionId").description("PG가 추적하는 온체인 거래 식별자"),
							fieldWithPath("checkoutSessionId").description("체크아웃 세션 식별자"),
							fieldWithPath("checkoutSessionStatus")
								.description("제출에 성공하면 PAYMENT_SUBMITTED. 이후 고객 취소가 불가능해진다."),
							fieldWithPath("paymentId").description("결제 식별자"),
							fieldWithPath("paymentStatus").description("제출에 성공하면 PROCESSING"),
						),
				)

			mockMvc
				.perform(
					post("/checkout/sessions/{checkoutSessionId}/transaction", SESSION_ID.value)
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(SubmitTransactionRequest("0x" + "7f3a".repeat(16)))),
				).andExpect(status().isOk)
				.andDo(document("checkout-submit-transaction", snippet))
		}

		test("document POST cancel") {
			every { cancelCheckoutSessionUseCase.execute(any()) } returns
				CancelCheckoutSessionResult(
					checkoutSessionId = SESSION_ID,
					checkoutSessionStatus = CheckoutSessionStatus.CANCELLED,
					cancelUrl = HttpUrl("https://merchant.example.com/order/1001/cancel"),
				)

			val snippet =
				checkoutResource(
					summary = "고객 취소",
					description =
						"고객이 결제를 포기한다. 요청 본문이 없다. PAYMENT_SUBMITTED 이후에는 온체인 전송을 " +
							"되돌릴 수 없어 409로 거부된다.",
					responseSchema = "CancelCheckoutSessionResponse",
					responseFields =
						listOf(
							fieldWithPath("checkoutSessionId").description("체크아웃 세션 식별자"),
							fieldWithPath("checkoutSessionStatus").description("취소에 성공하면 CANCELLED"),
							fieldWithPath("redirectUrl")
								.type(JsonFieldType.STRING)
								.description("돌아갈 가맹점 URL(cancelUrl). 가맹점이 지정하지 않았으면 null.")
								.optional(),
						),
				)

			mockMvc
				.perform(post("/checkout/sessions/{checkoutSessionId}/cancel", SESSION_ID.value))
				.andExpect(status().isOk)
				.andDo(document("checkout-cancel", snippet))
		}
	}
}
