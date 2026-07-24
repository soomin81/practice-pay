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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.api.merchant.config.SecurityConfig
import paytech.practice.pay.api.merchant.security.MerchantUserPrincipal
import paytech.practice.pay.application.apikey.IssueMerchantApiKeyResult
import paytech.practice.pay.application.apikey.IssueMerchantApiKeyUseCase
import paytech.practice.pay.application.apikey.ListMerchantApiKeysResult
import paytech.practice.pay.application.apikey.ListMerchantApiKeysUseCase
import paytech.practice.pay.application.apikey.MerchantApiKeyNotActiveException
import paytech.practice.pay.application.apikey.MerchantApiKeyNotFoundException
import paytech.practice.pay.application.apikey.MerchantUserCannotManageApiKeysException
import paytech.practice.pay.application.apikey.RevokeMerchantApiKeyResult
import paytech.practice.pay.application.apikey.RevokeMerchantApiKeyUseCase
import paytech.practice.pay.application.port.outbound.MerchantApiKeySummary
import paytech.practice.pay.domain.apikey.ApiEnvironment
import paytech.practice.pay.domain.apikey.ApiKeyPrefix
import paytech.practice.pay.domain.apikey.ApiKeyScope
import paytech.practice.pay.domain.apikey.ApiKeyStatus
import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId
import tools.jackson.databind.ObjectMapper
import java.time.Instant

private val OWNER = MerchantUserPrincipal(MerchantUserId("mu_owner"), MerchantId("mrc_001"), LoginId("owner"), MerchantUserRole.OWNER)
private val ADMIN = MerchantUserPrincipal(MerchantUserId("mu_admin"), MerchantId("mrc_001"), LoginId("admin"), MerchantUserRole.ADMIN)
private val VIEWER = MerchantUserPrincipal(MerchantUserId("mu_viewer"), MerchantId("mrc_001"), LoginId("viewer"), MerchantUserRole.VIEWER)

private fun validIssueRequest(): IssueMerchantApiKeyRequest =
	IssueMerchantApiKeyRequest(keyName = "운영 서버용 Key", scopes = listOf("PAYMENT_CREATE", "PAYMENT_READ"))

private fun authenticatedAs(principal: MerchantUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

/**
 * `@WebMvcTest`는 `SecurityConfig`를 자동으로 스캔하지 않으므로 명시적으로
 * Import한다 — 그래야 `/merchant/api-keys` 아래의 와일드카드가 `OWNER`/`ADMIN`
 * 역할을 요구하는 실제 인가 규칙까지 검증할 수 있다(`MerchantSubAccountControllerTest`와
 * 같은 이유). 발급(POST, 경로 변수 없음)과 폐기(DELETE, 경로 변수 있음) 양쪽 모두
 * 그 와일드카드로 걸리는지를 이 테스트가 실증한다.
 */
@WebMvcTest(MerchantApiKeyController::class)
@Import(SecurityConfig::class)
class MerchantApiKeyControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var objectMapper: ObjectMapper

	@MockkBean
	lateinit var issueMerchantApiKeyUseCase: IssueMerchantApiKeyUseCase

	@MockkBean
	lateinit var revokeMerchantApiKeyUseCase: RevokeMerchantApiKeyUseCase

	@MockkBean
	lateinit var listMerchantApiKeysUseCase: ListMerchantApiKeysUseCase

	init {
		extensions(SpringExtension)

		test("OWNER issuing a key returns 201 with the raw key shown once") {
			every { issueMerchantApiKeyUseCase.execute(any()) } returns
				IssueMerchantApiKeyResult(
					merchantApiKeyId = MerchantApiKeyId("mak_001"),
					keyName = "운영 서버용 Key",
					environment = ApiEnvironment.TEST,
					keyPrefix = ApiKeyPrefix("sk_test_ab12cd34"),
					scopes = setOf(ApiKeyScope.PAYMENT_CREATE, ApiKeyScope.PAYMENT_READ),
					rawApiKey = "sk_test_ab12cd34_rawsecretvalue",
					createdAt = Instant.parse("2026-07-19T00:00:00Z"),
				)

			mockMvc
				.perform(
					post("/merchant/api-keys")
						.with(csrf())
						.with(authenticatedAs(OWNER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validIssueRequest())),
				).andExpect(status().isCreated)
				.andExpect(jsonPath("$.environment").value("TEST"))
				.andExpect(jsonPath("$.rawApiKey").value("sk_test_ab12cd34_rawsecretvalue"))
		}

		test("ADMIN issuing a key also returns 201") {
			every { issueMerchantApiKeyUseCase.execute(any()) } returns
				IssueMerchantApiKeyResult(
					merchantApiKeyId = MerchantApiKeyId("mak_001"),
					keyName = "운영 서버용 Key",
					environment = ApiEnvironment.TEST,
					keyPrefix = ApiKeyPrefix("sk_test_ab12cd34"),
					scopes = setOf(ApiKeyScope.PAYMENT_CREATE),
					rawApiKey = "sk_test_ab12cd34_rawsecretvalue",
					createdAt = Instant.parse("2026-07-19T00:00:00Z"),
				)

			mockMvc
				.perform(
					post("/merchant/api-keys")
						.with(csrf())
						.with(authenticatedAs(ADMIN))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validIssueRequest().copy(scopes = listOf("PAYMENT_CREATE")))),
				).andExpect(status().isCreated)
		}

		test("VIEWER issuing a key returns 403 (blocked by SecurityConfig before the use case runs)") {
			mockMvc
				.perform(
					post("/merchant/api-keys")
						.with(csrf())
						.with(authenticatedAs(VIEWER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validIssueRequest())),
				).andExpect(status().isForbidden)
		}

		test("no authentication for issuance returns 401 or 403") {
			val result =
				mockMvc
					.perform(
						post("/merchant/api-keys")
							.with(csrf())
							.contentType(MediaType.APPLICATION_JSON)
							.content(objectMapper.writeValueAsString(validIssueRequest())),
					).andReturn()

			result.response.status shouldBeIn listOf(401, 403)
		}

		test("issuing without a CSRF token returns 403 even when authenticated (CSRF is enforced)") {
			// .with(csrf())를 일부러 뺐다 — 세션 쿠키 인증에서 CSRF가 실제로 강제되는지
			// 지키는 회귀 테스트다(SecurityConfig가 CSRF를 다시 끄면 여기서 먼저 깨진다).
			mockMvc
				.perform(
					post("/merchant/api-keys")
						.with(authenticatedAs(OWNER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validIssueRequest())),
				).andExpect(status().isForbidden)
		}

		test("blank keyName returns 400") {
			mockMvc
				.perform(
					post("/merchant/api-keys")
						.with(csrf())
						.with(authenticatedAs(OWNER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validIssueRequest().copy(keyName = ""))),
				).andExpect(status().isBadRequest)
		}

		test("empty scopes returns 400") {
			mockMvc
				.perform(
					post("/merchant/api-keys")
						.with(csrf())
						.with(authenticatedAs(OWNER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validIssueRequest().copy(scopes = emptyList()))),
				).andExpect(status().isBadRequest)
		}

		test("an invalid scope string returns 400") {
			mockMvc
				.perform(
					post("/merchant/api-keys")
						.with(csrf())
						.with(authenticatedAs(OWNER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validIssueRequest().copy(scopes = listOf("NOT_A_SCOPE")))),
				).andExpect(status().isBadRequest)
		}

		test("MerchantUserCannotManageApiKeysException from the use case returns 403") {
			every { issueMerchantApiKeyUseCase.execute(any()) } throws
				MerchantUserCannotManageApiKeysException("권한이 없습니다.")

			mockMvc
				.perform(
					post("/merchant/api-keys")
						.with(csrf())
						.with(authenticatedAs(OWNER))
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(validIssueRequest())),
				).andExpect(status().isForbidden)
		}

		test("OWNER revoking a key returns 200") {
			every { revokeMerchantApiKeyUseCase.execute(any()) } returns
				RevokeMerchantApiKeyResult(
					merchantApiKeyId = MerchantApiKeyId("mak_001"),
					revokedAt = Instant.parse("2026-07-19T00:00:00Z"),
				)

			mockMvc
				.perform(delete("/merchant/api-keys/mak_001").with(csrf()).with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.merchantApiKeyId").value("mak_001"))
		}

		test("VIEWER revoking a key returns 403 (the wildcard rule covers the path-variable route too)") {
			mockMvc
				.perform(delete("/merchant/api-keys/mak_001").with(csrf()).with(authenticatedAs(VIEWER)))
				.andExpect(status().isForbidden)
		}

		test("no authentication for revocation returns 401 or 403") {
			val result = mockMvc.perform(delete("/merchant/api-keys/mak_001").with(csrf())).andReturn()

			result.response.status shouldBeIn listOf(401, 403)
		}

		test("MerchantApiKeyNotFoundException from the use case returns 404") {
			every { revokeMerchantApiKeyUseCase.execute(any()) } throws
				MerchantApiKeyNotFoundException("찾을 수 없습니다.")

			mockMvc
				.perform(delete("/merchant/api-keys/mak_no_such_id").with(csrf()).with(authenticatedAs(OWNER)))
				.andExpect(status().isNotFound)
		}

		test("MerchantApiKeyNotActiveException from the use case returns 409") {
			every { revokeMerchantApiKeyUseCase.execute(any()) } throws
				MerchantApiKeyNotActiveException("이미 REVOKED 상태입니다.")

			mockMvc
				.perform(delete("/merchant/api-keys/mak_001").with(csrf()).with(authenticatedAs(OWNER)))
				.andExpect(status().isConflict)
		}

		test("OWNER listing keys returns 200 with summaries, no secret material") {
			every { listMerchantApiKeysUseCase.execute(any()) } returns
				ListMerchantApiKeysResult(
					apiKeys =
						listOf(
							MerchantApiKeySummary(
								merchantApiKeyId = MerchantApiKeyId("mak_001"),
								keyName = "운영 서버용 Key",
								environment = ApiEnvironment.TEST,
								keyPrefix = ApiKeyPrefix("sk_test_ab12cd34"),
								scopes = setOf(ApiKeyScope.PAYMENT_CREATE, ApiKeyScope.PAYMENT_READ),
								status = ApiKeyStatus.ACTIVE,
								createdAt = Instant.parse("2026-07-19T00:00:00Z"),
								lastUsedAt = null,
								revokedAt = null,
							),
						),
				)

			mockMvc
				.perform(get("/merchant/api-keys").with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.apiKeys[0].merchantApiKeyId").value("mak_001"))
				.andExpect(jsonPath("$.apiKeys[0].keyPrefix").value("sk_test_ab12cd34"))
				.andExpect(jsonPath("$.apiKeys[0].status").value("ACTIVE"))
				.andExpect(jsonPath("$.apiKeys[0].rawApiKey").doesNotExist())
				.andExpect(jsonPath("$.apiKeys[0].secretHash").doesNotExist())
		}

		test("ADMIN listing keys also returns 200") {
			every { listMerchantApiKeysUseCase.execute(any()) } returns ListMerchantApiKeysResult(apiKeys = emptyList())

			mockMvc
				.perform(get("/merchant/api-keys").with(authenticatedAs(ADMIN)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.apiKeys").isEmpty)
		}

		test("VIEWER listing keys returns 403 (blocked by SecurityConfig before the use case runs)") {
			mockMvc
				.perform(get("/merchant/api-keys").with(authenticatedAs(VIEWER)))
				.andExpect(status().isForbidden)
		}

		test("no authentication for listing returns 401 or 403") {
			val result = mockMvc.perform(get("/merchant/api-keys")).andReturn()

			result.response.status shouldBeIn listOf(401, 403)
		}

		test("MerchantUserCannotManageApiKeysException from the use case on list returns 403") {
			every { listMerchantApiKeysUseCase.execute(any()) } throws
				MerchantUserCannotManageApiKeysException("권한이 없습니다.")

			mockMvc
				.perform(get("/merchant/api-keys").with(authenticatedAs(OWNER)))
				.andExpect(status().isForbidden)
		}
	}
}
