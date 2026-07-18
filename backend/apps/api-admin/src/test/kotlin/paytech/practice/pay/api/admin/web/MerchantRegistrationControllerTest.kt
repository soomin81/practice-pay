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
import paytech.practice.pay.application.identity.DuplicateMerchantException
import paytech.practice.pay.application.identity.RegisterMerchantResult
import paytech.practice.pay.application.identity.RegisterMerchantUseCase
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.merchant.MerchantId
import tools.jackson.databind.ObjectMapper
import java.time.Instant

private val SUPER_ADMIN = InternalUserPrincipal(InternalUserId("iu_super_admin"), LoginId("super-admin"), InternalUserRole.SUPER_ADMIN)
private val OPERATOR = InternalUserPrincipal(InternalUserId("iu_operator"), LoginId("operator"), InternalUserRole.OPERATOR)
private val VIEWER = InternalUserPrincipal(InternalUserId("iu_viewer"), LoginId("viewer"), InternalUserRole.VIEWER)

private fun validRequest(): RegisterMerchantRequest =
	RegisterMerchantRequest(
		merchantCode = "NEW_MERCHANT",
		merchantName = "새 가맹점",
		webhookUrl = null,
		ownerLoginId = "owner-login",
		ownerEmail = "owner@example.com",
		ownerUserName = "가맹점 대표",
	)

private fun authenticatedAs(principal: InternalUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

/**
 * `@WebMvcTest`는 `SecurityConfig`를 자동으로 스캔하지 않으므로 명시적으로
 * Import한다 — 그래야 `/admin/merchants`가 `SUPER_ADMIN`/`OPERATOR` 역할을
 * 요구하는 실제 인가 규칙까지 검증할 수 있다(`InternalUserIssuanceControllerTest`와
 * 같은 이유).
 */
@WebMvcTest(MerchantRegistrationController::class)
@Import(SecurityConfig::class)
class MerchantRegistrationControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var objectMapper: ObjectMapper

	@MockkBean
	lateinit var registerMerchantUseCase: RegisterMerchantUseCase

	init {
		extensions(SpringExtension)

		test("SUPER_ADMIN with a valid request returns 201 with the merchant and invitation token") {
			every { registerMerchantUseCase.execute(any()) } returns
				RegisterMerchantResult(
					merchantId = MerchantId("mrc_001"),
					merchantCode = MerchantCode("NEW_MERCHANT"),
					merchantName = "새 가맹점",
					ownerMerchantUserId = MerchantUserId("mu_001"),
					ownerLoginId = LoginId("owner-login"),
					ownerEmail = Email("owner@example.com"),
					invitationToken = "raw-invitation-token",
					invitationExpiresAt = Instant.parse("2026-07-26T00:00:00Z"),
				)

			mockMvc
				.perform(
					post("/admin/merchants")
						.with(authenticatedAs(SUPER_ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isCreated)
				.andExpect(jsonPath("$.merchantCode").value("NEW_MERCHANT"))
				.andExpect(jsonPath("$.ownerLoginId").value("owner-login"))
				.andExpect(jsonPath("$.invitationToken").value("raw-invitation-token"))
		}

		test("OPERATOR with a valid request also returns 201") {
			every { registerMerchantUseCase.execute(any()) } returns
				RegisterMerchantResult(
					merchantId = MerchantId("mrc_001"),
					merchantCode = MerchantCode("NEW_MERCHANT"),
					merchantName = "새 가맹점",
					ownerMerchantUserId = MerchantUserId("mu_001"),
					ownerLoginId = LoginId("owner-login"),
					ownerEmail = Email("owner@example.com"),
					invitationToken = "raw-invitation-token",
					invitationExpiresAt = Instant.parse("2026-07-26T00:00:00Z"),
				)

			mockMvc
				.perform(
					post("/admin/merchants")
						.with(authenticatedAs(OPERATOR))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isCreated)
		}

		test("no authentication returns 401 or 403") {
			val result =
				mockMvc
					.perform(
						post("/admin/merchants")
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(validRequest())),
					).andReturn()

			result.response.status shouldBeIn listOf(401, 403)
		}

		test("authenticated as VIEWER returns 403") {
			mockMvc
				.perform(
					post("/admin/merchants")
						.with(authenticatedAs(VIEWER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isForbidden)
		}

		test("blank merchantCode returns 400") {
			mockMvc
				.perform(
					post("/admin/merchants")
						.with(authenticatedAs(SUPER_ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest().copy(merchantCode = ""))),
				).andExpect(status().isBadRequest)
		}

		test("an invalid webhookUrl returns 400") {
			mockMvc
				.perform(
					post("/admin/merchants")
						.with(authenticatedAs(SUPER_ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest().copy(webhookUrl = "not-a-url"))),
				).andExpect(status().isBadRequest)
		}

		test("DuplicateMerchantException from the use case returns 409") {
			every { registerMerchantUseCase.execute(any()) } throws DuplicateMerchantException("가맹점 코드가 이미 사용 중입니다.")

			mockMvc
				.perform(
					post("/admin/merchants")
						.with(authenticatedAs(SUPER_ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isConflict)
		}
	}
}
