package paytech.practice.pay.api.merchant.web

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
import paytech.practice.pay.api.merchant.config.SecurityConfig
import paytech.practice.pay.api.merchant.security.MerchantUserPrincipal
import paytech.practice.pay.application.payment.ExportMerchantPaymentsUseCase
import paytech.practice.pay.application.payment.ExportPaymentsResult
import paytech.practice.pay.application.payment.GetMerchantPaymentDetailUseCase
import paytech.practice.pay.application.payment.ListMerchantPaymentsUseCase
import paytech.practice.pay.application.payment.ListPaymentsCommand
import paytech.practice.pay.application.payment.ListPaymentsResult
import paytech.practice.pay.application.port.outbound.PaymentListEntry
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val MERCHANT_ID = MerchantId("mrc_001")
private val OWNER = MerchantUserPrincipal(MerchantUserId("mu_owner"), MERCHANT_ID, LoginId("owner"), MerchantUserRole.OWNER)
private val VIEWER = MerchantUserPrincipal(MerchantUserId("mu_viewer"), MERCHANT_ID, LoginId("viewer"), MerchantUserRole.VIEWER)

private fun authenticatedAs(principal: MerchantUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

private fun sampleEntry() =
	PaymentListEntry(
		paymentId = PaymentId("pay_001"),
		merchantId = MERCHANT_ID,
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
		transactionHash = null,
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

@WebMvcTest(MerchantPaymentController::class)
@Import(SecurityConfig::class, FixedClockConfiguration::class)
class MerchantPaymentControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@MockkBean
	lateinit var listMerchantPaymentsUseCase: ListMerchantPaymentsUseCase

	@MockkBean
	lateinit var exportMerchantPaymentsUseCase: ExportMerchantPaymentsUseCase

	@MockkBean
	lateinit var getMerchantPaymentDetailUseCase: GetMerchantPaymentDetailUseCase

	init {
		extensions(SpringExtension)

		test("returns the payment list with paging metadata") {
			every { listMerchantPaymentsUseCase.execute(any(), any()) } returns
				ListPaymentsResult(
					entries = listOf(sampleEntry()),
					totalCount = 1L,
					succeededCount = 1L,
					succeededAmount = Money(1 * 20_000L),
					page = 0,
					size = 50,
				)

			mockMvc
				.perform(get("/merchant/payments").with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.payments[0].paymentId").value("pay_001"))
				.andExpect(jsonPath("$.payments[0].paymentAmount").value("9007199254740993"))
				.andExpect(jsonPath("$.totalCount").value(1))
				.andExpect(jsonPath("$.page").value(0))
		}

		/**
		 * **이 슬라이스에서 가장 위험한 회귀다** — 조회 범위가 요청 파라미터에서 오면
		 * 호출자가 남의 가맹점 결제를 읽을 수 있다. 인증 주체의 `merchantId`가 쓰이는지
		 * 직접 확인한다.
		 */
		test("scopes to the authenticated merchant, not to a merchantId in the query string") {
			val merchantSlot = slot<MerchantId>()
			every { listMerchantPaymentsUseCase.execute(capture(merchantSlot), any()) } returns
				ListPaymentsResult(
					entries = emptyList(),
					totalCount = 0L,
					succeededCount = 0L,
					succeededAmount = Money(0 * 20_000L),
					page = 0,
					size = 50,
				)

			mockMvc
				.perform(get("/merchant/payments").param("merchantId", "mrc_someone_else").with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)

			merchantSlot.captured shouldBe MERCHANT_ID
		}

		// 결제 내역은 조회 전용 역할이 봐야 하는 대표적인 자료라 VIEWER도 허용한다
		// (OWNER/ADMIN으로 좁힌 API Key 관리와 다른 판단이다).
		test("allows VIEWER") {
			every { listMerchantPaymentsUseCase.execute(any(), any()) } returns
				ListPaymentsResult(
					entries = emptyList(),
					totalCount = 0L,
					succeededCount = 0L,
					succeededAmount = Money(0 * 20_000L),
					page = 0,
					size = 50,
				)

			mockMvc
				.perform(get("/merchant/payments").with(authenticatedAs(VIEWER)))
				.andExpect(status().isOk)
		}

		test("no authentication returns 401") {
			mockMvc.perform(get("/merchant/payments")).andExpect(status().isUnauthorized)
		}

		test("passes the status and period filters through") {
			val commandSlot = slot<ListPaymentsCommand>()
			every { listMerchantPaymentsUseCase.execute(any(), capture(commandSlot)) } returns
				ListPaymentsResult(
					entries = emptyList(),
					totalCount = 0L,
					succeededCount = 0L,
					succeededAmount = Money(0 * 20_000L),
					page = 0,
					size = 20,
				)

			mockMvc
				.perform(
					get("/merchant/payments")
						.param("status", "SUCCEEDED")
						.param("from", "2026-07-01T00:00:00Z")
						.param("to", "2026-07-31T23:59:59Z")
						.param("page", "1")
						.param("size", "20")
						.with(authenticatedAs(OWNER)),
				).andExpect(status().isOk)

			commandSlot.captured.status shouldBe PaymentStatus.SUCCEEDED
			commandSlot.captured.createdFrom shouldBe Instant.parse("2026-07-01T00:00:00Z")
			commandSlot.captured.page shouldBe 1
			commandSlot.captured.size shouldBe 20
		}

		test("export returns an xlsx attachment with a dated file name") {
			every { exportMerchantPaymentsUseCase.execute(any(), any()) } returns
				ExportPaymentsResult(spreadsheet = byteArrayOf(1, 2, 3), rowCount = 1, truncated = false)

			mockMvc
				.perform(get("/merchant/payments/export").with(authenticatedAs(OWNER)))
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
			every { exportMerchantPaymentsUseCase.execute(any(), any()) } returns
				ExportPaymentsResult(spreadsheet = byteArrayOf(1), rowCount = 10_000, truncated = true)

			mockMvc
				.perform(get("/merchant/payments/export").with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)
				.andExpect(header().string("X-Export-Truncated", "true"))
		}

		test("export scopes to the authenticated merchant") {
			val merchantSlot = slot<MerchantId>()
			every { exportMerchantPaymentsUseCase.execute(capture(merchantSlot), any()) } returns
				ExportPaymentsResult(spreadsheet = byteArrayOf(), rowCount = 0, truncated = false)

			mockMvc
				.perform(
					get("/merchant/payments/export").param("merchantId", "mrc_someone_else").with(authenticatedAs(OWNER)),
				).andExpect(status().isOk)

			merchantSlot.captured shouldBe MERCHANT_ID
		}

		test("export requires authentication") {
			mockMvc.perform(get("/merchant/payments/export")).andExpect(status().isUnauthorized)
		}

		test("detail returns the payment without merchant columns") {
			every { getMerchantPaymentDetailUseCase.execute(MERCHANT_ID, PaymentId("pay_001")) } returns merchantDetailView()

			mockMvc
				.perform(get("/merchant/payments/pay_001").with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.payment.paymentId").value("pay_001"))
				.andExpect(jsonPath("$.payment.merchantId").doesNotExist())
				.andExpect(jsonPath("$.payment.merchantName").doesNotExist())
		}

		/**
		 * **이 슬라이스에서 가장 중요한 회귀다.** 단건 조회는 범위를 좁히는 필터가 없어서,
		 * 소유를 확인하지 않으면 남의 결제를 ID로 찍어 볼 수 있다. 조회 범위는 쿼리스트링이
		 * 아니라 인증 주체에서 와야 한다.
		 */
		test("detail scopes to the authenticated merchant") {
			val merchantSlot = slot<MerchantId>()
			every { getMerchantPaymentDetailUseCase.execute(capture(merchantSlot), any()) } returns merchantDetailView()

			mockMvc
				.perform(
					get("/merchant/payments/pay_001").param("merchantId", "mrc_someone_else").with(authenticatedAs(OWNER)),
				).andExpect(status().isOk)

			merchantSlot.captured shouldBe MERCHANT_ID
		}

		/**
		 * 남의 결제는 **없는 것과 똑같이 404**다 — 403으로 나누면 "그 결제는 존재한다"가
		 * 새어 나가고, 식별자를 훑어 다른 가맹점의 거래를 추정할 수 있다.
		 */
		test("another merchant's payment is a 404, not a 403") {
			every { getMerchantPaymentDetailUseCase.execute(any(), any()) } returns null

			mockMvc
				.perform(get("/merchant/payments/pay_someone_else").with(authenticatedAs(OWNER)))
				.andExpect(status().isNotFound)
		}

		// /export가 /{paymentId}에 잡아먹히면 엑셀 다운로드가 404로 조용히 죽는다.
		test("the export path is not swallowed by the detail path variable") {
			every { exportMerchantPaymentsUseCase.execute(any(), any()) } returns
				ExportPaymentsResult(spreadsheet = byteArrayOf(1), rowCount = 0, truncated = false)

			mockMvc
				.perform(get("/merchant/payments/export").with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)
				.andExpect(header().string("X-Export-Truncated", "false"))
		}

		test("detail requires authentication") {
			mockMvc.perform(get("/merchant/payments/pay_001")).andExpect(status().isUnauthorized)
		}

		test("an unknown status returns 400") {
			mockMvc
				.perform(get("/merchant/payments").param("status", "NOT_A_STATUS").with(authenticatedAs(OWNER)))
				.andExpect(status().isBadRequest)
		}
	}
}
