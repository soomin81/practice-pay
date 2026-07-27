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
import paytech.practice.pay.application.identity.ListInternalLoginAuditResult
import paytech.practice.pay.application.identity.ListInternalLoginAuditUseCase
import paytech.practice.pay.application.port.outbound.InternalLoginAuditEntry
import paytech.practice.pay.domain.identity.InternalLoginAuditId
import paytech.practice.pay.domain.identity.InternalLoginOutcome
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import java.time.Instant

private val SUPER_ADMIN = InternalUserPrincipal(InternalUserId("iu_sa01"), LoginId("super-admin"), InternalUserRole.SUPER_ADMIN)
private val OPERATOR = InternalUserPrincipal(InternalUserId("iu_op01"), LoginId("operator01"), InternalUserRole.OPERATOR)

private fun authenticatedAs(principal: InternalUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

@WebMvcTest(LoginAuditController::class)
@Import(SecurityConfig::class)
class LoginAuditControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@MockkBean
	lateinit var listInternalLoginAuditUseCase: ListInternalLoginAuditUseCase

	init {
		extensions(SpringExtension)

		test("SUPER_ADMIN gets 200 with the recent audit entries, unknown-account attempts having null user fields") {
			every { listInternalLoginAuditUseCase.execute(any()) } returns
				ListInternalLoginAuditResult(
					entries =
						listOf(
							InternalLoginAuditEntry(
								auditId = InternalLoginAuditId("ila_001"),
								internalUserId = InternalUserId("iu_001"),
								attemptedLoginId = "admin01",
								userName = "관리자",
								outcome = InternalLoginOutcome.SUCCESS,
								clientIp = "203.0.113.7",
								occurredAt = Instant.parse("2026-07-19T00:00:00Z"),
							),
							InternalLoginAuditEntry(
								auditId = InternalLoginAuditId("ila_002"),
								internalUserId = null,
								attemptedLoginId = "ghost",
								userName = null,
								outcome = InternalLoginOutcome.INVALID_CREDENTIALS,
								clientIp = null,
								occurredAt = Instant.parse("2026-07-19T00:01:00Z"),
							),
						),
				)

			mockMvc
				.perform(get("/admin/login-audit").with(authenticatedAs(SUPER_ADMIN)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.entries[0].attemptedLoginId").value("admin01"))
				.andExpect(jsonPath("$.entries[0].outcome").value("SUCCESS"))
				.andExpect(jsonPath("$.entries[1].userName").doesNotExist())
				.andExpect(jsonPath("$.entries[1].internalUserId").doesNotExist())
		}

		test("OPERATOR gets 403 (login audit is SUPER_ADMIN only)") {
			mockMvc
				.perform(get("/admin/login-audit").with(authenticatedAs(OPERATOR)))
				.andExpect(status().isForbidden)
		}

		test("no authentication returns 401 or 403") {
			val result = mockMvc.perform(get("/admin/login-audit")).andReturn()

			result.response.status shouldBeIn listOf(401, 403)
		}
	}
}
