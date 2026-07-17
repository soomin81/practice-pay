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
import paytech.practice.pay.application.identity.AccountLockedException
import paytech.practice.pay.application.identity.AuthenticateInternalUserResult
import paytech.practice.pay.application.identity.AuthenticateInternalUserUseCase
import paytech.practice.pay.application.identity.InvalidCredentialsException
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import tools.jackson.databind.ObjectMapper
import java.time.Instant

private fun validRequest(): AdminLoginRequest = AdminLoginRequest(loginId = "admin01", password = "correct-horse-battery-staple")

@WebMvcTest(AdminLoginController::class)
@Import(SecurityConfig::class)
class AdminLoginControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var objectMapper: ObjectMapper

	@MockkBean
	lateinit var authenticateInternalUserUseCase: AuthenticateInternalUserUseCase

	init {
		extensions(SpringExtension)

		test("valid credentials return 200 with the authenticated identity") {
			every { authenticateInternalUserUseCase.execute(any()) } returns
				AuthenticateInternalUserResult(
					internalUserId = InternalUserId("iu_001"),
					loginId = LoginId("admin01"),
					userName = "테스트 관리자",
					role = InternalUserRole.SUPER_ADMIN,
				)

			mockMvc
				.perform(
					post("/admin/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isOk)
				.andExpect(jsonPath("$.loginId").value("admin01"))
				.andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
		}

		test("blank password returns 400") {
			mockMvc
				.perform(
					post("/admin/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest().copy(password = ""))),
				).andExpect(status().isBadRequest)
		}

		test("InvalidCredentialsException from the use case returns 401") {
			every { authenticateInternalUserUseCase.execute(any()) } throws InvalidCredentialsException()

			mockMvc
				.perform(
					post("/admin/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isUnauthorized)
		}

		test("AccountLockedException from the use case returns 401") {
			every { authenticateInternalUserUseCase.execute(any()) } throws AccountLockedException(Instant.parse("2026-07-17T00:15:00Z"))

			mockMvc
				.perform(
					post("/admin/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isUnauthorized)
		}
	}
}
