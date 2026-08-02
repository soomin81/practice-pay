package paytech.practice.pay.api.admin.web

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.slot
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.api.admin.config.SecurityConfig
import paytech.practice.pay.api.admin.security.InternalUserPrincipal
import paytech.practice.pay.application.payment.ExportPaymentsResult
import paytech.practice.pay.application.payment.ExportPaymentsUseCase
import paytech.practice.pay.application.payment.GetPaymentDetailUseCase
import paytech.practice.pay.application.payment.ListPaymentsCommand
import paytech.practice.pay.application.payment.ListPaymentsResult
import paytech.practice.pay.application.payment.ListPaymentsUseCase
import paytech.practice.pay.application.port.outbound.PaymentDetailBlockchainTransaction
import paytech.practice.pay.application.port.outbound.PaymentDetailCheckoutSession
import paytech.practice.pay.application.port.outbound.PaymentDetailExchangeOrder
import paytech.practice.pay.application.port.outbound.PaymentDetailPayment
import paytech.practice.pay.application.port.outbound.PaymentDetailQuote
import paytech.practice.pay.application.port.outbound.PaymentDetailSettlement
import paytech.practice.pay.application.port.outbound.PaymentDetailView
import paytech.practice.pay.application.port.outbound.PaymentDetailWebhookDelivery
import paytech.practice.pay.application.port.outbound.PaymentListEntry
import paytech.practice.pay.domain.blockchain.BlockchainTransactionStatus
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import paytech.practice.pay.domain.exchange.ExchangeOrderStatus
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import paytech.practice.pay.domain.webhook.WebhookDeliveryStatus
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-08-01T04:07:24Z")
private val SUPER_ADMIN =
	InternalUserPrincipal(InternalUserId("iu_super"), LoginId("super"), InternalUserRole.SUPER_ADMIN)
private val VIEWER =
	InternalUserPrincipal(InternalUserId("iu_viewer"), LoginId("viewer"), InternalUserRole.VIEWER)

private fun authenticatedAs(principal: InternalUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

private fun sampleEntry() =
	PaymentListEntry(
		paymentId = PaymentId("pay_001"),
		merchantId = MerchantId("mrc_001"),
		merchantName = "테스트 가맹점",
		merchantOrderId = MerchantOrderId("order-001"),
		orderName = "테스트 주문",
		orderAmount = Money(50_000),
		paymentAsset = Asset.USDC,
		// 안전 정수 범위를 넘는 값을 일부러 넣는다 — 응답이 문자열이어야 하는 이유다.
		paymentAmount = TokenAmount(9_007_199_254_740_993L),
		tokenDecimals = 6,
		network = BlockchainNetwork.BASE_SEPOLIA,
		status = PaymentStatus.SUCCEEDED,
		failureReason = null,
		transactionHash = TransactionHash("0x" + "d".repeat(64)),
		paidAt = Instant.parse("2026-07-20T10:05:00Z"),
		createdAt = Instant.parse("2026-07-20T10:00:00Z"),
	)

/**
 * 컨트롤러가 다운로드 파일 이름에 현재 시각을 넣으므로 [Clock] Bean이 필요하다 —
 * `@WebMvcTest` 슬라이스는 `UseCaseConfiguration`을 로드하지 않아 직접 준다.
 */
@TestConfiguration
class FixedClockConfiguration {
	@Bean
	fun clock(): Clock = Clock.fixed(Instant.parse("2026-08-01T06:30:00Z"), ZoneOffset.UTC)
}

private fun detailView(
	blockchainTransaction: PaymentDetailBlockchainTransaction? =
		PaymentDetailBlockchainTransaction(
			transactionHash = TransactionHash("0x" + "d".repeat(64)),
			status = BlockchainTransactionStatus.CONFIRMED,
			blockNumber = 44_910_246,
			confirmationCount = 433,
			requiredConfirmationCount = 12,
			fromAddress = "0x" + "b".repeat(40),
			toAddress = "0x" + "9".repeat(40),
			tokenContractAddress = "0x036CbD53842c5426634e7929541eC2318f3dCF7e",
			amountMinor = 14_357_502,
			failureCode = null,
			submittedAt = NOW,
			detectedAt = NOW,
			confirmedAt = NOW,
		),
	exchangeOrder: PaymentDetailExchangeOrder? =
		PaymentDetailExchangeOrder(
			exchangeOrderId = "exo_001",
			providerCode = "FAKE",
			status = ExchangeOrderStatus.COMPLETED,
			executedAmountMinor = 14_357_502,
			averageExecutionRate = BigDecimal("1400"),
			receivedAmount = 20_101,
			feeAmount = 0,
			completedAt = NOW,
		),
	settlement: PaymentDetailSettlement? =
		PaymentDetailSettlement(
			settlementReceivableId = "str_001",
			status = SettlementReceivableStatus.READY,
			grossAmount = 20_000,
			feeRate = BigDecimal("0.015"),
			feeAmount = 300,
			adjustmentAmount = 0,
			netAmount = 19_700,
			exchangeProfitLossAmount = 101,
			eligibleDate = LocalDate.parse("2026-08-01"),
		),
	webhooks: List<PaymentDetailWebhookDelivery> =
		listOf(
			PaymentDetailWebhookDelivery(
				webhookDeliveryId = "whd_001",
				eventType = "payment.created",
				destinationUrl = "http://localhost:9000/webhook",
				status = WebhookDeliveryStatus.SUCCEEDED,
				attemptCount = 1,
				lastHttpStatus = 200,
				lastErrorMessage = null,
				nextRetryAt = null,
				deliveredAt = NOW,
				createdAt = NOW,
			),
		),
) = PaymentDetailView(
	payment =
		PaymentDetailPayment(
			paymentId = PaymentId("pay_001"),
			merchantId = MerchantId("mrc_001"),
			merchantName = "테스트 가맹점",
			merchantOrderId = MerchantOrderId("order-001"),
			orderName = "테스트 주문",
			orderAmount = 20_000,
			orderCurrency = "KRW",
			paymentAsset = "USDC",
			paymentAmountMinor = 14_357_502,
			tokenDecimals = 6,
			network = "BASE_SEPOLIA",
			receivingWallet = "0x" + "9".repeat(40),
			customerWallet = "0x" + "b".repeat(40),
			status = PaymentStatus.SUCCEEDED,
			failureReason = null,
			expiresAt = NOW,
			paidAt = NOW,
			createdAt = NOW,
		),
	quote =
		PaymentDetailQuote(
			marketProviderCode = "FAKE",
			marketRate = BigDecimal("1400"),
			appliedRate = BigDecimal("1393"),
			spreadRate = BigDecimal("0.005"),
			quotedAt = NOW,
			expiresAt = NOW,
		),
	checkoutSession =
		PaymentDetailCheckoutSession(
			checkoutSessionId = "cs_001",
			status = CheckoutSessionStatus.COMPLETED,
			connectedWallet = "0x" + "b".repeat(40),
			expiresAt = NOW,
		),
	blockchainTransaction = blockchainTransaction,
	exchangeOrder = exchangeOrder,
	settlementReceivable = settlement,
	webhookDeliveries = webhooks,
)

@WebMvcTest(AdminPaymentController::class)
@Import(SecurityConfig::class, FixedClockConfiguration::class)
class AdminPaymentControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@MockkBean
	lateinit var listPaymentsUseCase: ListPaymentsUseCase

	@MockkBean
	lateinit var exportPaymentsUseCase: ExportPaymentsUseCase

	@MockkBean
	lateinit var getPaymentDetailUseCase: GetPaymentDetailUseCase

	init {
		extensions(SpringExtension)

		test("returns payments across every merchant with the merchant name") {
			every { listPaymentsUseCase.execute(any()) } returns
				ListPaymentsResult(
					entries = listOf(sampleEntry()),
					totalCount = 1L,
					succeededCount = 1L,
					succeededAmount = Money(1 * 20_000L),
					page = 0,
					size = 50,
				)

			mockMvc
				.perform(get("/admin/payments").with(authenticatedAs(SUPER_ADMIN)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.payments[0].merchantName").value("테스트 가맹점"))
				.andExpect(jsonPath("$.payments[0].paymentAmount").value("9007199254740993"))
				.andExpect(jsonPath("$.totalCount").value(1))
		}

		// 조회는 내부 사용자 전원에게 열려 있다(GET /admin/merchants와 같은 스코핑).
		test("allows VIEWER") {
			every { listPaymentsUseCase.execute(any()) } returns
				ListPaymentsResult(
					entries = emptyList(),
					totalCount = 0L,
					succeededCount = 0L,
					succeededAmount = Money(0 * 20_000L),
					page = 0,
					size = 50,
				)

			mockMvc
				.perform(get("/admin/payments").with(authenticatedAs(VIEWER)))
				.andExpect(status().isOk)
		}

		test("no authentication returns 401") {
			mockMvc.perform(get("/admin/payments")).andExpect(status().isUnauthorized)
		}

		test("passes the merchant, status and period filters through") {
			val commandSlot = slot<ListPaymentsCommand>()
			every { listPaymentsUseCase.execute(capture(commandSlot)) } returns
				ListPaymentsResult(
					entries = emptyList(),
					totalCount = 0L,
					succeededCount = 0L,
					succeededAmount = Money(0 * 20_000L),
					page = 0,
					size = 50,
				)

			mockMvc
				.perform(
					get("/admin/payments")
						.param("merchantId", "mrc_001")
						.param("status", "FAILED")
						.param("from", "2026-07-01T00:00:00Z")
						.with(authenticatedAs(SUPER_ADMIN)),
				).andExpect(status().isOk)

			commandSlot.captured.merchantId shouldBe MerchantId("mrc_001")
			commandSlot.captured.status shouldBe PaymentStatus.FAILED
			commandSlot.captured.createdFrom shouldBe Instant.parse("2026-07-01T00:00:00Z")
		}

		// 빈 문자열 파라미터는 "필터 없음"이다 — 프론트가 선택 안 함을 빈 값으로 보내기 쉽다.
		test("treats blank filter params as absent") {
			val commandSlot = slot<ListPaymentsCommand>()
			every { listPaymentsUseCase.execute(capture(commandSlot)) } returns
				ListPaymentsResult(
					entries = emptyList(),
					totalCount = 0L,
					succeededCount = 0L,
					succeededAmount = Money(0 * 20_000L),
					page = 0,
					size = 50,
				)

			mockMvc
				.perform(
					get("/admin/payments")
						.param("merchantId", "")
						.param("status", "")
						.with(authenticatedAs(SUPER_ADMIN)),
				).andExpect(status().isOk)

			commandSlot.captured.merchantId shouldBe null
			commandSlot.captured.status shouldBe null
		}

		test("export returns an xlsx attachment with a dated file name") {
			every { exportPaymentsUseCase.execute(any()) } returns
				ExportPaymentsResult(spreadsheet = byteArrayOf(1, 2, 3), rowCount = 1, truncated = false)

			mockMvc
				.perform(get("/admin/payments/export").with(authenticatedAs(SUPER_ADMIN)))
				.andExpect(status().isOk)
				.andExpect(
					header().string(
						"Content-Type",
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
					),
				).andExpect(header().string("Content-Disposition", """attachment; filename="payments-20260801-153000.xlsx""""))
				.andExpect(header().string("X-Export-Truncated", "false"))
		}

		/**
		 * **조용히 잘린 파일을 받아가는 것이 이 기능에서 가장 위험한 실패다** — 본문이
		 * 바이너리라 JSON 필드로 알릴 수 없어 헤더로 전한다.
		 */
		test("export flags truncation in a response header") {
			every { exportPaymentsUseCase.execute(any()) } returns
				ExportPaymentsResult(spreadsheet = byteArrayOf(1), rowCount = 10_000, truncated = true)

			mockMvc
				.perform(get("/admin/payments/export").with(authenticatedAs(SUPER_ADMIN)))
				.andExpect(status().isOk)
				.andExpect(header().string("X-Export-Truncated", "true"))
		}

		test("export passes the same filters as the list endpoint") {
			val commandSlot = slot<ListPaymentsCommand>()
			every { exportPaymentsUseCase.execute(capture(commandSlot)) } returns
				ExportPaymentsResult(spreadsheet = byteArrayOf(), rowCount = 0, truncated = false)

			mockMvc
				.perform(
					get("/admin/payments/export")
						.param("merchantId", "mrc_001")
						.param("status", "FAILED")
						.with(authenticatedAs(VIEWER)),
				).andExpect(status().isOk)

			commandSlot.captured.merchantId shouldBe MerchantId("mrc_001")
			commandSlot.captured.status shouldBe PaymentStatus.FAILED
		}

		test("export requires authentication") {
			mockMvc.perform(get("/admin/payments/export")).andExpect(status().isUnauthorized)
		}

		test("detail returns the full context of one payment") {
			every { getPaymentDetailUseCase.execute(PaymentId("pay_001")) } returns detailView()

			mockMvc
				.perform(get("/admin/payments/pay_001").with(authenticatedAs(VIEWER)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.payment.paymentId").value("pay_001"))
				.andExpect(jsonPath("$.payment.paymentAmount").value("14357502"))
				.andExpect(jsonPath("$.quote.appliedRate").value(1393))
				.andExpect(jsonPath("$.blockchainTransaction.confirmationCount").value(433))
				.andExpect(jsonPath("$.settlementReceivable.netAmount").value(19700))
				.andExpect(jsonPath("$.webhookDeliveries[0].status").value("SUCCEEDED"))
		}

		/**
		 * 흐름이 진행되지 않은 부분은 `null`이고, **그 `null` 자체가 "어디까지 갔는지"를
		 * 말해준다** — 화면이 그 구분으로 단계를 그린다.
		 */
		test("detail leaves later stages null when the flow has not reached them") {
			every { getPaymentDetailUseCase.execute(PaymentId("pay_early")) } returns
				detailView(blockchainTransaction = null, exchangeOrder = null, settlement = null, webhooks = emptyList())

			mockMvc
				.perform(get("/admin/payments/pay_early").with(authenticatedAs(VIEWER)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.blockchainTransaction").doesNotExist())
				.andExpect(jsonPath("$.exchangeOrder").doesNotExist())
				.andExpect(jsonPath("$.settlementReceivable").doesNotExist())
				.andExpect(jsonPath("$.webhookDeliveries").isEmpty)
		}

		test("detail returns 404 for an unknown payment") {
			every { getPaymentDetailUseCase.execute(any()) } returns null

			mockMvc
				.perform(get("/admin/payments/pay_nope").with(authenticatedAs(VIEWER)))
				.andExpect(status().isNotFound)
		}

		/**
		 * **`/export`가 `/{paymentId}`에 잡아먹히지 않아야 한다.** Spring이 리터럴 세그먼트를
		 * 경로 변수보다 먼저 고르는 데 기대고 있으므로 회귀로 고정한다 — 순서가 뒤집히면
		 * 엑셀 다운로드가 "pay_export를 찾을 수 없다"는 404로 조용히 죽는다.
		 */
		test("the export path is not swallowed by the detail path variable") {
			every { exportPaymentsUseCase.execute(any()) } returns
				ExportPaymentsResult(spreadsheet = byteArrayOf(1), rowCount = 0, truncated = false)

			mockMvc
				.perform(get("/admin/payments/export").with(authenticatedAs(VIEWER)))
				.andExpect(status().isOk)
				.andExpect(header().string("X-Export-Truncated", "false"))
		}

		test("detail requires authentication") {
			mockMvc.perform(get("/admin/payments/pay_001")).andExpect(status().isUnauthorized)
		}

		test("an unknown status returns 400") {
			mockMvc
				.perform(get("/admin/payments").param("status", "NOT_A_STATUS").with(authenticatedAs(SUPER_ADMIN)))
				.andExpect(status().isBadRequest)
		}
	}
}
