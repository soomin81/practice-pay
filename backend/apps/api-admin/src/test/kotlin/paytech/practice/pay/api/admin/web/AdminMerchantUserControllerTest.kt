package paytech.practice.pay.api.admin.web

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeIn
import io.mockk.every
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.api.admin.config.SecurityConfig
import paytech.practice.pay.api.admin.security.InternalUserPrincipal
import paytech.practice.pay.application.identity.AdminChangeMerchantUserRoleUseCase
import paytech.practice.pay.application.identity.AdminChangeMerchantUserStatusUseCase
import paytech.practice.pay.application.identity.AdminListMerchantUsersUseCase
import paytech.practice.pay.application.identity.ChangeMerchantUserRoleResult
import paytech.practice.pay.application.identity.ChangeMerchantUserStatusResult
import paytech.practice.pay.application.identity.InvalidMerchantUserTransitionException
import paytech.practice.pay.application.identity.LastActiveOwnerException
import paytech.practice.pay.application.identity.ListMerchantUsersResult
import paytech.practice.pay.application.identity.MerchantUserNotFoundException
import paytech.practice.pay.application.port.outbound.MerchantUserSummary
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import tools.jackson.databind.ObjectMapper
import java.time.Instant

private val SUPER_ADMIN =
	InternalUserPrincipal(InternalUserId("iu_sa01"), LoginId("super-admin"), paytech.practice.pay.domain.identity.InternalUserRole.SUPER_ADMIN)
private val OPERATOR =
	InternalUserPrincipal(InternalUserId("iu_op01"), LoginId("operator01"), paytech.practice.pay.domain.identity.InternalUserRole.OPERATOR)
private val VIEWER =
	InternalUserPrincipal(InternalUserId("iu_vi01"), LoginId("viewer01"), paytech.practice.pay.domain.identity.InternalUserRole.VIEWER)

private fun authenticatedAs(principal: InternalUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

private const val BASE = "/admin/merchants/mrc_001/users"

@WebMvcTest(AdminMerchantUserController::class)
@Import(SecurityConfig::class)
class AdminMerchantUserControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var objectMapper: ObjectMapper

	@MockkBean
	lateinit var adminListMerchantUsersUseCase: AdminListMerchantUsersUseCase

	@MockkBean
	lateinit var adminChangeMerchantUserStatusUseCase: AdminChangeMerchantUserStatusUseCase

	@MockkBean
	lateinit var adminChangeMerchantUserRoleUseCase: AdminChangeMerchantUserRoleUseCase

	init {
		extensions(SpringExtension)

		test("any authenticated internal user (VIEWER included) can list a merchant's users") {
			// GET은 메서드로 좁혀지지 않아 VIEWER에게도 열린다 — GET /admin/merchants와 같은 스코핑.
			every { adminListMerchantUsersUseCase.execute(any()) } returns
				ListMerchantUsersResult(
					merchantUsers =
						listOf(
							MerchantUserSummary(
								merchantUserId = MerchantUserId("mu_001"),
								loginId = LoginId("owner01"),
								email = Email("owner01@example.com"),
								userName = "오너",
								role = MerchantUserRole.OWNER,
								status = AccountStatus.ACTIVE,
								lastLoginAt = null,
								createdAt = Instant.parse("2026-07-19T00:00:00Z"),
								pendingInvitationExpiresAt = null,
							),
						),
				)

			mockMvc
				.perform(get(BASE).with(authenticatedAs(VIEWER)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.merchantUsers[0].loginId").value("owner01"))
				.andExpect(jsonPath("$.merchantUsers[0].passwordHash").doesNotExist())
		}

		test("OPERATOR suspending a merchant user returns 200") {
			every { adminChangeMerchantUserStatusUseCase.execute(any()) } returns
				ChangeMerchantUserStatusResult(
					merchantUserId = MerchantUserId("mu_001"),
					status = AccountStatus.SUSPENDED,
					changedAt = Instant.parse("2026-07-25T00:00:00Z"),
				)

			mockMvc
				.perform(post("$BASE/mu_001/suspend").with(csrf()).with(authenticatedAs(OPERATOR)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.status").value("SUSPENDED"))
		}

		test("VIEWER hitting a management action returns 403 (POST is scoped to SUPER_ADMIN/OPERATOR)") {
			mockMvc
				.perform(post("$BASE/mu_001/suspend").with(csrf()).with(authenticatedAs(VIEWER)))
				.andExpect(status().isForbidden)
		}

		test("no authentication for a management action returns 401 or 403") {
			val result = mockMvc.perform(post("$BASE/mu_001/suspend").with(csrf())).andReturn()

			result.response.status shouldBeIn listOf(401, 403)
		}

		test("MerchantUserNotFoundException (unknown or cross-merchant target) returns 404") {
			every { adminChangeMerchantUserStatusUseCase.execute(any()) } throws
				MerchantUserNotFoundException("MerchantUser(mu_001)를 찾을 수 없습니다.")

			mockMvc
				.perform(post("$BASE/mu_001/terminate").with(csrf()).with(authenticatedAs(SUPER_ADMIN)))
				.andExpect(status().isNotFound)
		}

		test("LastActiveOwnerException returns 409") {
			every { adminChangeMerchantUserStatusUseCase.execute(any()) } throws
				LastActiveOwnerException("마지막 활성 OWNER입니다.")

			mockMvc
				.perform(post("$BASE/mu_001/suspend").with(csrf()).with(authenticatedAs(OPERATOR)))
				.andExpect(status().isConflict)
		}

		test("InvalidMerchantUserTransitionException returns 409") {
			every { adminChangeMerchantUserStatusUseCase.execute(any()) } throws
				InvalidMerchantUserTransitionException("종료된 계정은 재개할 수 없습니다.")

			mockMvc
				.perform(post("$BASE/mu_001/reactivate").with(csrf()).with(authenticatedAs(OPERATOR)))
				.andExpect(status().isConflict)
		}

		test("SUPER_ADMIN changing a merchant user role returns 200") {
			every { adminChangeMerchantUserRoleUseCase.execute(any()) } returns
				ChangeMerchantUserRoleResult(
					merchantUserId = MerchantUserId("mu_001"),
					role = MerchantUserRole.VIEWER,
					changedAt = Instant.parse("2026-07-25T00:00:00Z"),
				)

			mockMvc
				.perform(
					post("$BASE/mu_001/role")
						.with(csrf())
						.with(authenticatedAs(SUPER_ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(ChangeMerchantUserRoleRequest(role = "VIEWER"))),
				).andExpect(status().isOk)
				.andExpect(jsonPath("$.role").value("VIEWER"))
		}

		test("promoting to OWNER via role change returns 400 (domain require failure)") {
			every { adminChangeMerchantUserRoleUseCase.execute(any()) } throws
				IllegalArgumentException("역할 변경으로는 OWNER를 만들 수 없습니다.")

			mockMvc
				.perform(
					post("$BASE/mu_001/role")
						.with(csrf())
						.with(authenticatedAs(SUPER_ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(ChangeMerchantUserRoleRequest(role = "OWNER"))),
				).andExpect(status().isBadRequest)
		}
	}
}
