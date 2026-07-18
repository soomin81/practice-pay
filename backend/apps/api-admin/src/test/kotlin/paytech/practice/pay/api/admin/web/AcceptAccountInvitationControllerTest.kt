package paytech.practice.pay.api.admin.web

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.api.admin.config.SecurityConfig
import paytech.practice.pay.application.identity.AcceptAccountInvitationResult
import paytech.practice.pay.application.identity.AcceptAccountInvitationUseCase
import paytech.practice.pay.application.identity.InvalidInvitationException
import paytech.practice.pay.domain.identity.LoginId
import tools.jackson.databind.ObjectMapper
import java.time.Instant

private fun validRequest(): AcceptAccountInvitationRequest =
	AcceptAccountInvitationRequest(invitationToken = "raw-invitation-token", newPassword = "new-password-123")

/**
 * `SecurityConfig`를 명시적으로 Import해서 `/admin/account-invitations/accept`가
 * 실제로 `permitAll`인지까지 검증한다(`InternalUserIssuanceControllerTest`와
 * 같은 이유).
 */
@WebMvcTest(AcceptAccountInvitationController::class)
@Import(SecurityConfig::class)
class AcceptAccountInvitationControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var objectMapper: ObjectMapper

	@MockkBean
	lateinit var acceptAccountInvitationUseCase: AcceptAccountInvitationUseCase

	init {
		extensions(SpringExtension)

		test("a valid, unauthenticated request returns 200 with the activated identity") {
			every { acceptAccountInvitationUseCase.execute(any()) } returns
				AcceptAccountInvitationResult(
					loginId = LoginId("new-operator"),
					activatedAt = Instant.parse("2026-07-17T00:00:00Z"),
				)

			mockMvc
				.perform(
					post("/admin/account-invitations/accept")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isOk)
				.andExpect(jsonPath("$.loginId").value("new-operator"))
		}

		test("blank invitationToken returns 400") {
			mockMvc
				.perform(
					post("/admin/account-invitations/accept")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest().copy(invitationToken = ""))),
				).andExpect(status().isBadRequest)
		}

		test("InvalidInvitationException from the use case returns 400") {
			every { acceptAccountInvitationUseCase.execute(any()) } throws InvalidInvitationException()

			mockMvc
				.perform(
					post("/admin/account-invitations/accept")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isBadRequest)
		}
	}
}
