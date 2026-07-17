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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.api.admin.config.SecurityConfig
import paytech.practice.pay.api.admin.security.InternalUserPrincipal
import paytech.practice.pay.application.identity.DuplicateInternalUserException
import paytech.practice.pay.application.identity.IssueInternalUserResult
import paytech.practice.pay.application.identity.IssueInternalUserUseCase
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import tools.jackson.databind.ObjectMapper
import java.time.Instant

private val ISSUER = InternalUserPrincipal(InternalUserId("iu_super_admin"), LoginId("super-admin"), InternalUserRole.SUPER_ADMIN)

private fun validRequest(): IssueInternalUserRequest =
	IssueInternalUserRequest(
		loginId = "new-operator",
		email = "new-operator@example.com",
		userName = "새 운영자",
		role = "OPERATOR",
	)

private fun authenticatedAs(principal: InternalUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

/**
 * `@WebMvcTest`는 `SecurityConfig`를 자동으로 스캔하지 않으므로 명시적으로
 * Import한다 — 그래야 `/admin/internal-users`가 `SUPER_ADMIN` 역할을 요구하는
 * 실제 인가 규칙까지 검증할 수 있다(`apps:api-payment`의 `PaymentControllerTest`와
 * 같은 이유).
 */
@WebMvcTest(InternalUserIssuanceController::class)
@Import(SecurityConfig::class)
class InternalUserIssuanceControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var objectMapper: ObjectMapper

	@MockkBean
	lateinit var issueInternalUserUseCase: IssueInternalUserUseCase

	init {
		extensions(SpringExtension)

		test("SUPER_ADMIN with a valid request returns 201 with the issued identity and invitation token") {
			every { issueInternalUserUseCase.execute(any()) } returns
				IssueInternalUserResult(
					internalUserId = InternalUserId("iu_001"),
					loginId = LoginId("new-operator"),
					email = Email("new-operator@example.com"),
					userName = "새 운영자",
					role = InternalUserRole.OPERATOR,
					invitationToken = "raw-invitation-token",
					invitationExpiresAt = Instant.parse("2026-07-24T00:00:00Z"),
				)

			mockMvc
				.perform(
					post("/admin/internal-users")
						.with(authenticatedAs(ISSUER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isCreated)
				.andExpect(jsonPath("$.loginId").value("new-operator"))
				.andExpect(jsonPath("$.role").value("OPERATOR"))
				.andExpect(jsonPath("$.invitationToken").value("raw-invitation-token"))
		}

		test("no authentication returns 401 or 403") {
			val result =
				mockMvc
					.perform(
						post("/admin/internal-users")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(validRequest())),
					).andReturn()

			result.response.status shouldBeIn listOf(401, 403)
		}

		test("authenticated as OPERATOR (not SUPER_ADMIN) returns 403") {
			val operator = InternalUserPrincipal(InternalUserId("iu_operator"), LoginId("operator"), InternalUserRole.OPERATOR)

			mockMvc
				.perform(
					post("/admin/internal-users")
						.with(authenticatedAs(operator))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isForbidden)
		}

		test("blank loginId returns 400") {
			mockMvc
				.perform(
					post("/admin/internal-users")
						.with(authenticatedAs(ISSUER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest().copy(loginId = ""))),
				).andExpect(status().isBadRequest)
		}

		test("an invalid role returns 400") {
			mockMvc
				.perform(
					post("/admin/internal-users")
						.with(authenticatedAs(ISSUER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest().copy(role = "NOT_A_ROLE"))),
				).andExpect(status().isBadRequest)
		}

		test("DuplicateInternalUserException from the use case returns 409") {
			every { issueInternalUserUseCase.execute(any()) } throws DuplicateInternalUserException("로그인 아이디가 이미 사용 중입니다.")

			mockMvc
				.perform(
					post("/admin/internal-users")
						.with(authenticatedAs(ISSUER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isConflict)
		}
	}
}
