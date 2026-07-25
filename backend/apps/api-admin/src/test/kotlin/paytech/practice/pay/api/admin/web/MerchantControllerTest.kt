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
import paytech.practice.pay.application.identity.DuplicateMerchantException
import paytech.practice.pay.application.identity.RegisterMerchantResult
import paytech.practice.pay.application.identity.RegisterMerchantUseCase
import paytech.practice.pay.application.merchant.ListMerchantsResult
import paytech.practice.pay.application.merchant.ListMerchantsUseCase
import paytech.practice.pay.application.port.outbound.MerchantSummary
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.merchant.MerchantStatus
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
 * Import한다 — 그래야 `POST /admin/merchants`가 `SUPER_ADMIN`/`OPERATOR` 역할을
 * 요구하고 `GET /admin/merchants`는 인증된 누구나(`VIEWER` 포함) 호출할 수 있는
 * 실제 인가 규칙까지 검증할 수 있다(`InternalUserIssuanceControllerTest`와
 * 같은 이유, `SecurityConfig`의 메서드 스코핑 KDoc 참고).
 */
@WebMvcTest(MerchantController::class)
@Import(SecurityConfig::class)
class MerchantControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var objectMapper: ObjectMapper

	@MockkBean
	lateinit var registerMerchantUseCase: RegisterMerchantUseCase

	@MockkBean
	lateinit var listMerchantsUseCase: ListMerchantsUseCase

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
						.with(csrf())
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
						.with(csrf())
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
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(validRequest())),
					).andReturn()

			result.response.status shouldBeIn listOf(401, 403)
		}

		test("authenticated as VIEWER returns 403") {
			mockMvc
				.perform(
					post("/admin/merchants")
						.with(csrf())
						.with(authenticatedAs(VIEWER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isForbidden)
		}

		test("blank merchantCode returns 400") {
			mockMvc
				.perform(
					post("/admin/merchants")
						.with(csrf())
						.with(authenticatedAs(SUPER_ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest().copy(merchantCode = ""))),
				).andExpect(status().isBadRequest)
		}

		test("an invalid webhookUrl returns 400") {
			mockMvc
				.perform(
					post("/admin/merchants")
						.with(csrf())
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
						.with(csrf())
						.with(authenticatedAs(SUPER_ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validRequest())),
				).andExpect(status().isConflict)
		}

		test("SUPER_ADMIN listing merchants returns 200 with the summaries") {
			every { listMerchantsUseCase.execute() } returns
				ListMerchantsResult(
					merchants =
						listOf(
							MerchantSummary(
								merchantId = MerchantId("mrc_001"),
								merchantCode = MerchantCode("MERCHANT_ONE"),
								merchantName = "First Merchant",
								status = MerchantStatus.ACTIVE,
								createdAt = Instant.parse("2026-07-19T00:00:00Z"),
							),
						),
				)

			mockMvc
				.perform(get("/admin/merchants").with(authenticatedAs(SUPER_ADMIN)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.merchants[0].merchantCode").value("MERCHANT_ONE"))
				.andExpect(jsonPath("$.merchants[0].status").value("ACTIVE"))
		}

		// docs/architecture/identity-access-api-key.md의 "3.2 MVP 역할"이 VIEWER를
		// "조회 전용"으로 정의한다 — POST는 403이어야 하지만 GET은 통과해야 한다.
		// SecurityConfig가 /admin/merchants 규칙을 HttpMethod.POST로 좁힌 이유를
		// 이 테스트가 실증한다.
		test("VIEWER listing merchants returns 200 even though VIEWER cannot register") {
			every { listMerchantsUseCase.execute() } returns ListMerchantsResult(merchants = emptyList())

			mockMvc
				.perform(get("/admin/merchants").with(authenticatedAs(VIEWER)))
				.andExpect(status().isOk)
		}

		test("OPERATOR listing merchants also returns 200") {
			every { listMerchantsUseCase.execute() } returns ListMerchantsResult(merchants = emptyList())

			mockMvc
				.perform(get("/admin/merchants").with(authenticatedAs(OPERATOR)))
				.andExpect(status().isOk)
		}

		test("no authentication for listing returns 401 or 403") {
			val result = mockMvc.perform(get("/admin/merchants")).andReturn()

			result.response.status shouldBeIn listOf(401, 403)
		}

		test("an empty merchant list returns 200 with an empty array") {
			every { listMerchantsUseCase.execute() } returns ListMerchantsResult(merchants = emptyList())

			mockMvc
				.perform(get("/admin/merchants").with(authenticatedAs(SUPER_ADMIN)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.merchants").isArray)
				.andExpect(jsonPath("$.merchants.length()").value(0))
		}
	}
}
