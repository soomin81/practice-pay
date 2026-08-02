package paytech.practice.pay.api.admin.web

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldBeNull
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.api.admin.config.SecurityConfig
import paytech.practice.pay.api.admin.security.InternalUserPrincipal
import paytech.practice.pay.application.settlement.ExportSettlementReceivablesResult
import paytech.practice.pay.application.settlement.ExportSettlementReceivablesUseCase
import paytech.practice.pay.application.settlement.ListSettlementReceivablesCommand
import paytech.practice.pay.application.settlement.ListSettlementReceivablesUseCase
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import java.time.LocalDate

private val VIEWER = InternalUserPrincipal(InternalUserId("iu_vw01"), LoginId("viewer01"), InternalUserRole.VIEWER)

private fun authenticatedAs(principal: InternalUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

private fun exported(truncated: Boolean = false) =
	ExportSettlementReceivablesResult(spreadsheet = byteArrayOf(1, 2, 3), rowCount = 1, truncated = truncated)

@WebMvcTest(AdminSettlementReceivableController::class)
@Import(SecurityConfig::class, FixedClockConfiguration::class)
class AdminSettlementExportControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@MockkBean
	lateinit var listSettlementReceivablesUseCase: ListSettlementReceivablesUseCase

	@MockkBean
	lateinit var exportSettlementReceivablesUseCase: ExportSettlementReceivablesUseCase

	init {
		extensions(SpringExtension)

		test("export returns an xlsx attachment with a dated file name") {
			every { exportSettlementReceivablesUseCase.execute(any()) } returns exported()

			mockMvc
				.perform(get("/admin/settlement-receivables/export").with(authenticatedAs(VIEWER)))
				.andExpect(status().isOk)
				.andExpect(
					header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
				).andExpect(
					header().string("Content-Disposition", """attachment; filename="settlements-20260801-153000.xlsx""""),
				)
		}

		/**
		 * **조용히 잘린 파일을 받아가는 것이 이 기능에서 가장 위험한 실패다** — 본문이
		 * 바이너리라 JSON 필드로 알릴 수 없어 헤더로 전한다.
		 */
		test("export flags truncation in a response header") {
			every { exportSettlementReceivablesUseCase.execute(any()) } returns exported(truncated = true)

			mockMvc
				.perform(get("/admin/settlement-receivables/export").with(authenticatedAs(VIEWER)))
				.andExpect(header().string("X-Export-Truncated", "true"))
		}

		/** 이 콘솔은 조회가 전원에게 열려 있다(`GET /admin/payments`와 같은 스코핑). */
		test("a VIEWER may export because reading is open to every internal user") {
			every { exportSettlementReceivablesUseCase.execute(any()) } returns exported()

			mockMvc
				.perform(get("/admin/settlement-receivables/export").with(authenticatedAs(VIEWER)))
				.andExpect(status().isOk)
		}

		test("export passes the filters through and never sends paging") {
			val commandSlot = slot<ListSettlementReceivablesCommand>()
			every { exportSettlementReceivablesUseCase.execute(capture(commandSlot)) } returns exported()

			mockMvc
				.perform(
					get("/admin/settlement-receivables/export")
						.param("merchantId", "mrc_001")
						.param("status", "READY")
						.param("eligibleFrom", "2026-08-01")
						.param("eligibleTo", "2026-08-31")
						.with(authenticatedAs(VIEWER)),
				).andExpect(status().isOk)

			commandSlot.captured.merchantId shouldBe MerchantId("mrc_001")
			commandSlot.captured.status shouldBe SettlementReceivableStatus.READY
			commandSlot.captured.eligibleFrom shouldBe LocalDate.parse("2026-08-01")
			commandSlot.captured.eligibleTo shouldBe LocalDate.parse("2026-08-31")
		}

		/**
		 * **가맹점을 지정하지 않으면 전 가맹점**이다 — 내부 운영자 콘솔의 의도된 동작이고,
		 * 가맹점 콘솔과 Use Case를 갈라 둔 이유이기도 하다.
		 */
		test("export covers every merchant when none is given") {
			val commandSlot = slot<ListSettlementReceivablesCommand>()
			every { exportSettlementReceivablesUseCase.execute(capture(commandSlot)) } returns exported()

			mockMvc
				.perform(get("/admin/settlement-receivables/export").with(authenticatedAs(VIEWER)))
				.andExpect(status().isOk)

			commandSlot.captured.merchantId.shouldBeNull()
		}

		test("export requires authentication") {
			mockMvc.perform(get("/admin/settlement-receivables/export")).andExpect(status().isUnauthorized)
		}
	}
}
