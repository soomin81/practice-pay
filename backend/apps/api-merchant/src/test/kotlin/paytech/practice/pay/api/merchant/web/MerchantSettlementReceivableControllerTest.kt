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
import paytech.practice.pay.application.port.outbound.SettlementReceivableListEntry
import paytech.practice.pay.application.settlement.ListMerchantSettlementReceivablesUseCase
import paytech.practice.pay.application.settlement.ListSettlementReceivablesCommand
import paytech.practice.pay.application.settlement.ListSettlementReceivablesResult
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

private val MERCHANT_ID = MerchantId("mrc_001")
private val OWNER = MerchantUserPrincipal(MerchantUserId("mu_owner"), MERCHANT_ID, LoginId("owner"), MerchantUserRole.OWNER)
private val VIEWER = MerchantUserPrincipal(MerchantUserId("mu_viewer"), MERCHANT_ID, LoginId("viewer"), MerchantUserRole.VIEWER)

private fun authenticatedAs(principal: MerchantUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

private fun entry() =
	SettlementReceivableListEntry(
		settlementReceivableId = SettlementReceivableId("str_001"),
		merchantId = MERCHANT_ID,
		merchantName = "테스트 가맹점",
		paymentId = PaymentId("pay_001"),
		merchantOrderId = MerchantOrderId("order-001"),
		status = SettlementReceivableStatus.READY,
		settlementCurrency = "KRW",
		grossAmount = 20_000,
		feeRate = BigDecimal("0.015"),
		feeAmount = 300,
		adjustmentAmount = 0,
		netAmount = 19_700,
		exchangeReceivedAmount = 20_101,
		exchangeProfitLossAmount = 101,
		eligibleDate = LocalDate.parse("2026-08-01"),
		createdAt = Instant.parse("2026-08-01T04:07:24Z"),
	)

@WebMvcTest(MerchantSettlementReceivableController::class)
@Import(SecurityConfig::class)
class MerchantSettlementReceivableControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@MockkBean
	lateinit var listMerchantSettlementReceivablesUseCase: ListMerchantSettlementReceivablesUseCase

	init {
		extensions(SpringExtension)

		test("returns receivables with the total net amount") {
			every { listMerchantSettlementReceivablesUseCase.execute(any(), any()) } returns
				ListSettlementReceivablesResult(listOf(entry()), totalCount = 1L, totalNetAmount = 19_700L, page = 0, size = 50)

			mockMvc
				.perform(get("/merchant/settlement-receivables").with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.settlementReceivables[0].settlementReceivableId").value("str_001"))
				.andExpect(jsonPath("$.settlementReceivables[0].netAmount").value(19700))
				.andExpect(jsonPath("$.settlementReceivables[0].exchangeProfitLossAmount").value(101))
				.andExpect(jsonPath("$.totalNetAmount").value(19700))
		}

		// 이 콘솔은 언제나 자기 가맹점 하나만 보므로 가맹점 열을 응답에 담지 않는다.
		test("does not expose merchant columns") {
			every { listMerchantSettlementReceivablesUseCase.execute(any(), any()) } returns
				ListSettlementReceivablesResult(listOf(entry()), totalCount = 1L, totalNetAmount = 19_700L, page = 0, size = 50)

			mockMvc
				.perform(get("/merchant/settlement-receivables").with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.settlementReceivables[0].merchantId").doesNotExist())
				.andExpect(jsonPath("$.settlementReceivables[0].merchantName").doesNotExist())
		}

		/**
		 * **정산은 결제보다 민감하다** — 범위가 새면 남의 매출과 수취 예정 금액이 드러난다.
		 * 조회 범위가 쿼리스트링이 아니라 인증 주체에서 오는지 직접 확인한다.
		 */
		test("scopes to the authenticated merchant, not to a merchantId in the query string") {
			val merchantSlot = slot<MerchantId>()
			every { listMerchantSettlementReceivablesUseCase.execute(capture(merchantSlot), any()) } returns
				ListSettlementReceivablesResult(emptyList(), totalCount = 0L, totalNetAmount = 0L, page = 0, size = 50)

			mockMvc
				.perform(
					get("/merchant/settlement-receivables")
						.param("merchantId", "mrc_someone_else")
						.with(authenticatedAs(OWNER)),
				).andExpect(status().isOk)

			merchantSlot.captured shouldBe MERCHANT_ID
		}

		test("allows VIEWER") {
			every { listMerchantSettlementReceivablesUseCase.execute(any(), any()) } returns
				ListSettlementReceivablesResult(emptyList(), totalCount = 0L, totalNetAmount = 0L, page = 0, size = 50)

			mockMvc
				.perform(get("/merchant/settlement-receivables").with(authenticatedAs(VIEWER)))
				.andExpect(status().isOk)
		}

		test("no authentication returns 401") {
			mockMvc.perform(get("/merchant/settlement-receivables")).andExpect(status().isUnauthorized)
		}

		// 기간은 날짜다 — 결제 목록의 ISO-8601 순간과 다르다(정산 예정일 기준).
		test("passes the status and eligible-date filters through") {
			val commandSlot = slot<ListSettlementReceivablesCommand>()
			every { listMerchantSettlementReceivablesUseCase.execute(any(), capture(commandSlot)) } returns
				ListSettlementReceivablesResult(emptyList(), totalCount = 0L, totalNetAmount = 0L, page = 0, size = 50)

			mockMvc
				.perform(
					get("/merchant/settlement-receivables")
						.param("status", "READY")
						.param("eligibleFrom", "2026-08-01")
						.param("eligibleTo", "2026-08-31")
						.with(authenticatedAs(OWNER)),
				).andExpect(status().isOk)

			commandSlot.captured.status shouldBe SettlementReceivableStatus.READY
			commandSlot.captured.eligibleFrom shouldBe LocalDate.parse("2026-08-01")
			commandSlot.captured.eligibleTo shouldBe LocalDate.parse("2026-08-31")
		}

		test("an unknown status returns 400") {
			mockMvc
				.perform(get("/merchant/settlement-receivables").param("status", "NOPE").with(authenticatedAs(OWNER)))
				.andExpect(status().isBadRequest)
		}
	}
}
