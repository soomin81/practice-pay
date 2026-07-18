package paytech.practice.pay.api.merchant.web

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
import paytech.practice.pay.api.merchant.config.SecurityConfig
import paytech.practice.pay.application.identity.AcceptAccountInvitationResult
import paytech.practice.pay.application.identity.AcceptAccountInvitationUseCase
import paytech.practice.pay.application.identity.InvalidInvitationException
import paytech.practice.pay.domain.identity.LoginId
import tools.jackson.databind.ObjectMapper
import java.time.Instant

private fun validRequest(): AcceptAccountInvitationRequest =
	AcceptAccountInvitationRequest(invitationToken = "raw-invitation-token", newPassword = "new-password-123")

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
					loginId = LoginId("new-owner"),
					activatedAt = Instant.parse("2026-07-17T00:00:00Z"),
				)

			mockMvc
				.perform(
					post("/merchant/account-invitations/accept")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isOk)
				.andExpect(jsonPath("$.loginId").value("new-owner"))
		}

		test("blank newPassword returns 400") {
			mockMvc
				.perform(
					post("/merchant/account-invitations/accept")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest().copy(newPassword = ""))),
				).andExpect(status().isBadRequest)
		}

		test("InvalidInvitationException from the use case returns 400") {
			every { acceptAccountInvitationUseCase.execute(any()) } throws InvalidInvitationException()

			mockMvc
				.perform(
					post("/merchant/account-invitations/accept")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isBadRequest)
		}
	}
}
