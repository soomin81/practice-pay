package paytech.practice.pay.api.merchant.web

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
import paytech.practice.pay.api.merchant.config.SecurityConfig
import paytech.practice.pay.api.merchant.security.MerchantUserPrincipal
import paytech.practice.pay.application.identity.ChangeMerchantUserRoleResult
import paytech.practice.pay.application.identity.ChangeMerchantUserRoleUseCase
import paytech.practice.pay.application.identity.ChangeMerchantUserStatusResult
import paytech.practice.pay.application.identity.ChangeMerchantUserStatusUseCase
import paytech.practice.pay.application.identity.DuplicateMerchantUserException
import paytech.practice.pay.application.identity.InvalidMerchantUserTransitionException
import paytech.practice.pay.application.identity.InviteMerchantSubAccountResult
import paytech.practice.pay.application.identity.InviteMerchantSubAccountUseCase
import paytech.practice.pay.application.identity.LastActiveOwnerException
import paytech.practice.pay.application.identity.ListMerchantUsersResult
import paytech.practice.pay.application.identity.ListMerchantUsersUseCase
import paytech.practice.pay.application.identity.MerchantUserCannotInviteSubAccountsException
import paytech.practice.pay.application.port.outbound.MerchantUserSummary
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId
import tools.jackson.databind.ObjectMapper
import java.time.Instant

private val OWNER = MerchantUserPrincipal(MerchantUserId("mu_owner"), MerchantId("mrc_001"), LoginId("owner"), MerchantUserRole.OWNER)
private val ADMIN = MerchantUserPrincipal(MerchantUserId("mu_admin"), MerchantId("mrc_001"), LoginId("admin"), MerchantUserRole.ADMIN)
private val VIEWER = MerchantUserPrincipal(MerchantUserId("mu_viewer"), MerchantId("mrc_001"), LoginId("viewer"), MerchantUserRole.VIEWER)

private fun validRequest(): InviteMerchantSubAccountRequest =
	InviteMerchantSubAccountRequest(
		loginId = "new-admin",
		email = "new-admin@example.com",
		userName = "새 하위 계정",
		role = "ADMIN",
	)

private fun authenticatedAs(principal: MerchantUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

/**
 * `@WebMvcTest`는 `SecurityConfig`를 자동으로 스캔하지 않으므로 명시적으로
 * Import한다 — 그래야 `/merchant/merchant-users`가 `OWNER`/`ADMIN` 역할을
 * 요구하는 실제 인가 규칙까지 검증할 수 있다(`MerchantRegistrationControllerTest`와
 * 같은 이유).
 */
@WebMvcTest(MerchantSubAccountController::class)
@Import(SecurityConfig::class)
class MerchantSubAccountControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var objectMapper: ObjectMapper

	@MockkBean
	lateinit var inviteMerchantSubAccountUseCase: InviteMerchantSubAccountUseCase

	@MockkBean
	lateinit var listMerchantUsersUseCase: ListMerchantUsersUseCase

	@MockkBean
	lateinit var changeMerchantUserStatusUseCase: ChangeMerchantUserStatusUseCase

	@MockkBean
	lateinit var changeMerchantUserRoleUseCase: ChangeMerchantUserRoleUseCase

	init {
		extensions(SpringExtension)

		test("OWNER with a valid request returns 201 with the sub-account and invitation token") {
			every { inviteMerchantSubAccountUseCase.execute(any()) } returns
				InviteMerchantSubAccountResult(
					merchantUserId = MerchantUserId("mu_001"),
					loginId = LoginId("new-admin"),
					email = Email("new-admin@example.com"),
					userName = "새 하위 계정",
					role = MerchantUserRole.ADMIN,
					invitationToken = "raw-invitation-token",
					invitationExpiresAt = Instant.parse("2026-07-26T00:00:00Z"),
				)

			mockMvc
				.perform(
					post("/merchant/merchant-users")
						.with(csrf())
						.with(authenticatedAs(OWNER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isCreated)
				.andExpect(jsonPath("$.loginId").value("new-admin"))
				.andExpect(jsonPath("$.role").value("ADMIN"))
				.andExpect(jsonPath("$.invitationToken").value("raw-invitation-token"))
		}

		test("ADMIN with a valid request also returns 201") {
			every { inviteMerchantSubAccountUseCase.execute(any()) } returns
				InviteMerchantSubAccountResult(
					merchantUserId = MerchantUserId("mu_001"),
					loginId = LoginId("new-viewer"),
					email = Email("new-viewer@example.com"),
					userName = "새 뷰어",
					role = MerchantUserRole.VIEWER,
					invitationToken = "raw-invitation-token",
					invitationExpiresAt = Instant.parse("2026-07-26T00:00:00Z"),
				)

			mockMvc
				.perform(
					post("/merchant/merchant-users")
						.with(csrf())
						.with(authenticatedAs(ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest().copy(role = "VIEWER"))),
				).andExpect(status().isCreated)
		}

		test("no authentication returns 401 or 403") {
			val result =
				mockMvc
					.perform(
						post("/merchant/merchant-users")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(validRequest())),
					).andReturn()

			result.response.status shouldBeIn listOf(401, 403)
		}

		test("authenticated as VIEWER returns 403 (blocked by SecurityConfig before the use case runs)") {
			mockMvc
				.perform(
					post("/merchant/merchant-users")
						.with(csrf())
						.with(authenticatedAs(VIEWER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isForbidden)
		}

		test("blank loginId returns 400") {
			mockMvc
				.perform(
					post("/merchant/merchant-users")
						.with(csrf())
						.with(authenticatedAs(OWNER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest().copy(loginId = ""))),
				).andExpect(status().isBadRequest)
		}

		test("an invalid role string returns 400") {
			mockMvc
				.perform(
					post("/merchant/merchant-users")
						.with(csrf())
						.with(authenticatedAs(OWNER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest().copy(role = "NOT_A_ROLE"))),
				).andExpect(status().isBadRequest)
		}

		test("MerchantUserCannotInviteSubAccountsException from the use case returns 403") {
			every { inviteMerchantSubAccountUseCase.execute(any()) } throws
				MerchantUserCannotInviteSubAccountsException("권한이 없습니다.")

			mockMvc
				.perform(
					post("/merchant/merchant-users")
						.with(csrf())
						.with(authenticatedAs(OWNER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isForbidden)
		}

		test("DuplicateMerchantUserException from the use case returns 409") {
			every { inviteMerchantSubAccountUseCase.execute(any()) } throws
				DuplicateMerchantUserException("로그인 아이디가 이미 사용 중입니다.")

			mockMvc
				.perform(
					post("/merchant/merchant-users")
						.with(csrf())
						.with(authenticatedAs(OWNER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isConflict)
		}

		test("OWNER listing merchant users returns 200 with the roster, no password material") {
			every { listMerchantUsersUseCase.execute(any()) } returns
				ListMerchantUsersResult(
					merchantUsers =
						listOf(
							MerchantUserSummary(
								merchantUserId = MerchantUserId("mu_001"),
								loginId = LoginId("member01"),
								email = Email("member01@example.com"),
								userName = "팀원",
								role = MerchantUserRole.ADMIN,
								status = AccountStatus.INVITED,
								lastLoginAt = null,
								createdAt = Instant.parse("2026-07-19T00:00:00Z"),
							),
						),
				)

			mockMvc
				.perform(get("/merchant/merchant-users").with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.merchantUsers[0].loginId").value("member01"))
				.andExpect(jsonPath("$.merchantUsers[0].status").value("INVITED"))
				.andExpect(jsonPath("$.merchantUsers[0].passwordHash").doesNotExist())
		}

		test("VIEWER listing merchant users returns 403 (blocked by SecurityConfig before the use case runs)") {
			mockMvc
				.perform(get("/merchant/merchant-users").with(authenticatedAs(VIEWER)))
				.andExpect(status().isForbidden)
		}

		test("no authentication for listing returns 401 or 403") {
			val result = mockMvc.perform(get("/merchant/merchant-users")).andReturn()

			result.response.status shouldBeIn listOf(401, 403)
		}

		test("MerchantUserCannotInviteSubAccountsException from the list use case returns 403") {
			every { listMerchantUsersUseCase.execute(any()) } throws
				MerchantUserCannotInviteSubAccountsException("권한이 없습니다.")

			mockMvc
				.perform(get("/merchant/merchant-users").with(authenticatedAs(OWNER)))
				.andExpect(status().isForbidden)
		}

		test("OWNER suspending a sub-account returns 200") {
			every { changeMerchantUserStatusUseCase.execute(any()) } returns
				ChangeMerchantUserStatusResult(
					merchantUserId = MerchantUserId("mu_001"),
					status = AccountStatus.SUSPENDED,
					changedAt = Instant.parse("2026-07-19T00:00:00Z"),
				)

			mockMvc
				.perform(post("/merchant/merchant-users/mu_001/suspend").with(csrf()).with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.status").value("SUSPENDED"))
		}

		test("VIEWER cannot reach the action paths (SecurityConfig wildcard covers sub-paths)") {
			// 와일드카드를 좁히면 여기서 먼저 깨진다 — 정적 1차 관문이 사라지는 것을 막는 회귀 테스트다.
			mockMvc
				.perform(post("/merchant/merchant-users/mu_001/suspend").with(csrf()).with(authenticatedAs(VIEWER)))
				.andExpect(status().isForbidden)
			mockMvc
				.perform(post("/merchant/merchant-users/mu_001/terminate").with(csrf()).with(authenticatedAs(VIEWER)))
				.andExpect(status().isForbidden)
		}

		test("no authentication on an action path returns 401 or 403") {
			val result = mockMvc.perform(post("/merchant/merchant-users/mu_001/suspend").with(csrf())).andReturn()

			result.response.status shouldBeIn listOf(401, 403)
		}

		test("LastActiveOwnerException returns 409") {
			every { changeMerchantUserStatusUseCase.execute(any()) } throws
				LastActiveOwnerException("마지막 활성 OWNER입니다.")

			mockMvc
				.perform(post("/merchant/merchant-users/mu_001/suspend").with(csrf()).with(authenticatedAs(OWNER)))
				.andExpect(status().isConflict)
		}

		test("InvalidMerchantUserTransitionException returns 409") {
			every { changeMerchantUserStatusUseCase.execute(any()) } throws
				InvalidMerchantUserTransitionException("종료된 계정은 재개할 수 없습니다.")

			mockMvc
				.perform(post("/merchant/merchant-users/mu_001/reactivate").with(csrf()).with(authenticatedAs(OWNER)))
				.andExpect(status().isConflict)
		}

		test("changing a role returns 200") {
			every { changeMerchantUserRoleUseCase.execute(any()) } returns
				ChangeMerchantUserRoleResult(
					merchantUserId = MerchantUserId("mu_001"),
					role = MerchantUserRole.VIEWER,
					changedAt = Instant.parse("2026-07-19T00:00:00Z"),
				)

			mockMvc
				.perform(
					post("/merchant/merchant-users/mu_001/role")
						.with(csrf())
						.with(authenticatedAs(OWNER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(ChangeMerchantUserRoleRequest(role = "VIEWER"))),
				).andExpect(status().isOk)
				.andExpect(jsonPath("$.role").value("VIEWER"))
		}

		test("an invalid role string returns 400") {
			mockMvc
				.perform(
					post("/merchant/merchant-users/mu_001/role")
						.with(csrf())
						.with(authenticatedAs(OWNER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(ChangeMerchantUserRoleRequest(role = "NOT_A_ROLE"))),
				).andExpect(status().isBadRequest)
		}

		test("attempting to invite an OWNER returns 400 (domain require failure surfaces as IllegalArgumentException)") {
			every { inviteMerchantSubAccountUseCase.execute(any()) } throws
				IllegalArgumentException("하위 계정 발급으로는 OWNER를 만들 수 없습니다: inviteInitialOwner를 사용하세요.")

			mockMvc
				.perform(
					post("/merchant/merchant-users")
						.with(csrf())
						.with(authenticatedAs(OWNER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest().copy(role = "OWNER"))),
				).andExpect(status().isBadRequest)
		}
	}
}
