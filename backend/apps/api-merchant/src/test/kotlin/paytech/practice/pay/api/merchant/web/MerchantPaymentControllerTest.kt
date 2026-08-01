package paytech.practice.pay.api.merchant.web

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.slot
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.api.merchant.config.SecurityConfig
import paytech.practice.pay.api.merchant.security.MerchantUserPrincipal
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
import java.time.Instant

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

@WebMvcTest(MerchantPaymentController::class)
@Import(SecurityConfig::class)
class MerchantPaymentControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@MockkBean
	lateinit var listMerchantPaymentsUseCase: ListMerchantPaymentsUseCase

	init {
		extensions(SpringExtension)

		test("returns the payment list with paging metadata") {
			every { listMerchantPaymentsUseCase.execute(any(), any()) } returns
				ListPaymentsResult(entries = listOf(sampleEntry()), totalCount = 1L, page = 0, size = 50)

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
				ListPaymentsResult(entries = emptyList(), totalCount = 0L, page = 0, size = 50)

			mockMvc
				.perform(get("/merchant/payments").param("merchantId", "mrc_someone_else").with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)

			merchantSlot.captured shouldBe MERCHANT_ID
		}

		// 결제 내역은 조회 전용 역할이 봐야 하는 대표적인 자료라 VIEWER도 허용한다
		// (OWNER/ADMIN으로 좁힌 API Key 관리와 다른 판단이다).
		test("allows VIEWER") {
			every { listMerchantPaymentsUseCase.execute(any(), any()) } returns
				ListPaymentsResult(entries = emptyList(), totalCount = 0L, page = 0, size = 50)

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
				ListPaymentsResult(entries = emptyList(), totalCount = 0L, page = 0, size = 20)

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

		test("an unknown status returns 400") {
			mockMvc
				.perform(get("/merchant/payments").param("status", "NOT_A_STATUS").with(authenticatedAs(OWNER)))
				.andExpect(status().isBadRequest)
		}
	}
}
