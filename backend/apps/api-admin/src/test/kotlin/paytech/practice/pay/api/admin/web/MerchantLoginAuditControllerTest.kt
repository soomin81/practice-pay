package paytech.practice.pay.api.admin.web

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeIn
import io.mockk.every
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
import paytech.practice.pay.api.admin.config.SecurityConfig
import paytech.practice.pay.api.admin.security.InternalUserPrincipal
import paytech.practice.pay.application.identity.ListMerchantLoginAuditResult
import paytech.practice.pay.application.identity.ListMerchantLoginAuditUseCase
import paytech.practice.pay.application.port.outbound.MerchantLoginAuditEntry
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.LoginOutcome
import paytech.practice.pay.domain.identity.MerchantLoginAuditId
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Instant

private val SUPER_ADMIN = InternalUserPrincipal(InternalUserId("iu_sa01"), LoginId("super-admin"), InternalUserRole.SUPER_ADMIN)
private val OPERATOR = InternalUserPrincipal(InternalUserId("iu_op01"), LoginId("operator01"), InternalUserRole.OPERATOR)
private val VIEWER = InternalUserPrincipal(InternalUserId("iu_vi01"), LoginId("viewer01"), InternalUserRole.VIEWER)

private fun authenticatedAs(principal: InternalUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

@WebMvcTest(MerchantLoginAuditController::class)
@Import(SecurityConfig::class)
class MerchantLoginAuditControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@MockkBean
	lateinit var listMerchantLoginAuditUseCase: ListMerchantLoginAuditUseCase

	init {
		extensions(SpringExtension)

		test("OPERATOR gets 200 with entries, unknown-merchant attempts having null merchant fields") {
			every { listMerchantLoginAuditUseCase.execute(any()) } returns
				ListMerchantLoginAuditResult(
					entries =
						listOf(
							MerchantLoginAuditEntry(
								auditId = MerchantLoginAuditId("mla_001"),
								merchantId = MerchantId("mrc_001"),
								merchantName = "테스트 가맹점",
								attemptedMerchantCode = "test-merchant",
								attemptedLoginId = "owner01",
								userName = "오너",
								outcome = LoginOutcome.SUCCESS,
								clientIp = "203.0.113.7",
								occurredAt = Instant.parse("2026-07-19T00:00:00Z"),
							),
							MerchantLoginAuditEntry(
								auditId = MerchantLoginAuditId("mla_002"),
								merchantId = null,
								merchantName = null,
								attemptedMerchantCode = "ghost",
								attemptedLoginId = "ghost",
								userName = null,
								outcome = LoginOutcome.INVALID_CREDENTIALS,
								clientIp = null,
								occurredAt = Instant.parse("2026-07-19T00:01:00Z"),
							),
						),
				)

			mockMvc
				.perform(get("/admin/merchant-login-audit").with(authenticatedAs(OPERATOR)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.entries[0].merchantName").value("테스트 가맹점"))
				.andExpect(jsonPath("$.entries[0].outcome").value("SUCCESS"))
				.andExpect(jsonPath("$.entries[1].merchantName").doesNotExist())
				.andExpect(jsonPath("$.entries[1].merchantId").doesNotExist())
		}

		test("SUPER_ADMIN also gets 200") {
			every { listMerchantLoginAuditUseCase.execute(any()) } returns ListMerchantLoginAuditResult(entries = emptyList())

			mockMvc
				.perform(get("/admin/merchant-login-audit").with(authenticatedAs(SUPER_ADMIN)))
				.andExpect(status().isOk)
		}

		test("VIEWER gets 403 (merchant login audit is SUPER_ADMIN/OPERATOR only)") {
			mockMvc
				.perform(get("/admin/merchant-login-audit").with(authenticatedAs(VIEWER)))
				.andExpect(status().isForbidden)
		}

		test("no authentication returns 401 or 403") {
			val result = mockMvc.perform(get("/admin/merchant-login-audit")).andReturn()

			result.response.status shouldBeIn listOf(401, 403)
		}
	}
}
