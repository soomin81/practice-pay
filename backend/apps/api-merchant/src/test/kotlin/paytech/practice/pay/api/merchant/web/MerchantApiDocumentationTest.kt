package paytech.practice.pay.api.merchant.web

import com.epages.restdocs.apispec.ParameterDescriptorWithType
import com.epages.restdocs.apispec.ResourceDocumentation.parameterWithName
import com.epages.restdocs.apispec.ResourceDocumentation.resource
import com.epages.restdocs.apispec.ResourceSnippet
import com.epages.restdocs.apispec.ResourceSnippetParameters
import com.epages.restdocs.apispec.Schema
import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restdocs.test.autoconfigure.AutoConfigureRestDocs
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.api.merchant.config.SecurityConfig
import paytech.practice.pay.api.merchant.security.MerchantUserPrincipal
import paytech.practice.pay.application.apikey.IssueMerchantApiKeyResult
import paytech.practice.pay.application.apikey.IssueMerchantApiKeyUseCase
import paytech.practice.pay.application.apikey.ListMerchantApiKeysResult
import paytech.practice.pay.application.apikey.ListMerchantApiKeysUseCase
import paytech.practice.pay.application.apikey.RevokeMerchantApiKeyResult
import paytech.practice.pay.application.apikey.RevokeMerchantApiKeyUseCase
import paytech.practice.pay.application.identity.AuthenticateMerchantUserResult
import paytech.practice.pay.application.identity.AuthenticateMerchantUserUseCase
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

private val NOW: Instant = Instant.parse("2026-07-19T00:00:00Z")
private val OWNER =
	MerchantUserPrincipal(MerchantUserId("mu_owner01"), MerchantId("mrc_test_001"), LoginId("owner01"), MerchantUserRole.OWNER)

private fun authenticatedAs(principal: MerchantUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

/**
 * 모든 가맹점 콘솔 엔드포인트가 공유하는 스니펫 형태를 한곳에 모은다.
 *
 * **`resource(builder.build())`로 감싸는 것이 핵심이다** — 빌더를 그대로 `document(...)`에
 * 넘기면 `responseFields`/`pathParameters`가 조용히 사라져 스키마가 빈 `type: object`로
 * 나온다(`api-payment`의 `CheckoutApiDocumentationTest`가 같은 함정을 겪고 남긴 근거).
 * 스펙을 눈으로 열어보지 않으면 그대로 넘어가는 종류의 오류라, 생성 후 반드시
 * `openapi3.yaml`의 경로 수·필드를 확인한다.
 */
private fun merchantResource(
	summary: String,
	description: String,
	responseSchema: String? = null,
	responseFields: List<FieldDescriptor> = emptyList(),
	requestSchema: String? = null,
	requestFields: List<FieldDescriptor> = emptyList(),
	pathParameters: List<ParameterDescriptorWithType> = emptyList(),
): ResourceSnippet {
	val builder =
		ResourceSnippetParameters
			.builder()
			.tag("Merchant Console")
			.summary(summary)
			.description(description)

	responseSchema?.let { builder.responseSchema(Schema(it)) }
	if (responseFields.isNotEmpty()) builder.responseFields(responseFields)
	requestSchema?.let { builder.requestSchema(Schema(it)) }
	if (requestFields.isNotEmpty()) builder.requestFields(requestFields)
	if (pathParameters.isNotEmpty()) builder.pathParameters(*pathParameters.toTypedArray())

	return resource(builder.build())
}

/**
 * 통과한 테스트에서 가맹점 콘솔 API의 OpenAPI 스펙(`build/api-spec/openapi3.yaml`)을
 * 만든다 — 프론트엔드(`frontend/merchant`)가 백엔드 소스가 아니라 이 스펙으로 타입을
 * 생성한다(`api-payment`의 체크아웃 스펙과 같은 규율).
 *
 * **동작·인가 검증은 각 컨트롤러의 슬라이스 테스트가 하고, 이 클래스는 문서화만 한다**
 * (`CheckoutApiDocumentationTest`와 같은 분리). REST Docs 기반이라 실제로 요청을 보내고
 * 응답을 받아야 스니펫이 나오므로 스펙이 실제 응답과 어긋날 수 없다.
 *
 * **CSRF/세션은 스펙에 드러나지 않는다.** 상태 변경 요청에 `.with(csrf())`를 실어야
 * 통과하지만 restdocs는 명시적으로 서술한 것만 문서화하므로, `X-XSRF-TOKEN`/세션 쿠키
 * 계약은 스펙이 아니라 `docs/architecture/merchant-console-api.md`의 산문으로 남긴다
 * (오류 응답을 스펙에서 빼는 이유와 같은 층 — MockMvc가 그 층을 재현하지 못한다).
 */
@WebMvcTest(
	controllers = [
		MerchantLoginController::class,
		MerchantMeController::class,
		MerchantLogoutController::class,
		MerchantApiKeyController::class,
	],
)
@Import(SecurityConfig::class)
@AutoConfigureRestDocs
@TestPropertySource(properties = ["app.merchant-console.allowed-origins=http://localhost:5174"])
class MerchantApiDocumentationTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var objectMapper: ObjectMapper

	@MockkBean
	lateinit var authenticateMerchantUserUseCase: AuthenticateMerchantUserUseCase

	@MockkBean
	lateinit var issueMerchantApiKeyUseCase: IssueMerchantApiKeyUseCase

	@MockkBean
	lateinit var listMerchantApiKeysUseCase: ListMerchantApiKeysUseCase

	@MockkBean
	lateinit var revokeMerchantApiKeyUseCase: RevokeMerchantApiKeyUseCase

	init {
		extensions(SpringExtension)

		test("document POST login") {
			every { authenticateMerchantUserUseCase.execute(any()) } returns
				AuthenticateMerchantUserResult(
					merchantUserId = MerchantUserId("mu_owner01"),
					merchantId = MerchantId("mrc_test_001"),
					loginId = LoginId("owner01"),
					userName = "테스트 오너",
					role = MerchantUserRole.OWNER,
				)

			val snippet =
				merchantResource(
					summary = "가맹점 콘솔 로그인",
					description =
						"로그인에 성공하면 세션 쿠키(Set-Cookie)를 내리고 본문에 신원 정보를 담는다. login_id는 " +
							"가맹점 안에서만 유일해서 merchantCode로 어느 가맹점인지 먼저 밝힌다. 상태 변경 요청이라 " +
							"CSRF 토큰(X-XSRF-TOKEN)이 필요하다 — GET /merchant/me로 먼저 XSRF-TOKEN 쿠키를 받는다.",
					requestSchema = "MerchantLoginRequest",
					requestFields =
						listOf(
							fieldWithPath("merchantCode").description("가맹점 식별 코드"),
							fieldWithPath("loginId").description("가맹점 내 로그인 아이디"),
							fieldWithPath("password").description("비밀번호"),
						),
					responseSchema = "MerchantLoginResponse",
					responseFields =
						listOf(
							fieldWithPath("merchantUserId").description("가맹점 사용자 식별자"),
							fieldWithPath("merchantId").description("가맹점 식별자"),
							fieldWithPath("loginId").description("로그인 아이디"),
							fieldWithPath("userName").description("사용자 이름"),
							fieldWithPath("role").description("OWNER | ADMIN | VIEWER"),
						),
				)

			mockMvc
				.perform(
					post("/merchant/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(
							objectMapper.writeValueAsString(
								MerchantLoginRequest(merchantCode = "test-merchant", loginId = "owner01", password = "correct-horse-battery-staple"),
							),
						),
				).andExpect(status().isOk)
				.andDo(document("merchant-login", snippet))
		}

		test("document GET me") {
			val snippet =
				merchantResource(
					summary = "현재 세션 사용자 조회(세션 복원)",
					description =
						"프론트가 새로고침 후 로그인 상태를 복원할 때 쓴다. 미인증이면 401을 돌려주고, 프론트는 그것을 " +
							"'로그아웃 상태'로 해석한다. 이 GET은 CSRF 토큰 발급도 겸한다 — 응답에 XSRF-TOKEN 쿠키가 실린다.",
					responseSchema = "MerchantMeResponse",
					responseFields =
						listOf(
							fieldWithPath("merchantUserId").description("가맹점 사용자 식별자"),
							fieldWithPath("merchantId").description("가맹점 식별자"),
							fieldWithPath("loginId").description("로그인 아이디"),
							fieldWithPath("role").description("OWNER | ADMIN | VIEWER"),
						),
				)

			mockMvc
				.perform(get("/merchant/me").with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)
				.andDo(document("merchant-me", snippet))
		}

		test("document POST logout") {
			val snippet =
				merchantResource(
					summary = "로그아웃",
					description = "세션을 무효화한다. 본문이 없고 204를 돌려준다. 상태 변경 요청이라 CSRF 토큰이 필요하다.",
				)

			mockMvc
				.perform(post("/merchant/logout").with(authenticatedAs(OWNER)).with(csrf()))
				.andExpect(status().isNoContent)
				.andDo(document("merchant-logout", snippet))
		}

		test("document POST issue api key") {
			every { issueMerchantApiKeyUseCase.execute(any()) } returns
				IssueMerchantApiKeyResult(
					merchantApiKeyId = MerchantApiKeyId("mak_001"),
					keyName = "운영 서버용 Key",
					environment = ApiEnvironment.TEST,
					keyPrefix = ApiKeyPrefix("sk_test_ab12cd34"),
					scopes = setOf(ApiKeyScope.PAYMENT_CREATE, ApiKeyScope.PAYMENT_READ),
					rawApiKey = "sk_test_ab12cd34_rawsecretvalue",
					createdAt = NOW,
				)

			val snippet =
				merchantResource(
					summary = "API Key 발급",
					description =
						"OWNER/ADMIN만 발급할 수 있다. rawApiKey는 이 응답에서만 원문으로 보이고 DB에는 Hash만 남는다 — " +
							"다시 조회할 수 없으니 즉시 가맹점 서버에 전달해야 한다. environment는 받지 않는다(MVP는 항상 TEST).",
					requestSchema = "IssueMerchantApiKeyRequest",
					requestFields =
						listOf(
							fieldWithPath("keyName").description("Key 이름(용도 식별용)"),
							fieldWithPath("scopes").description("Scope 이름 배열. MVP는 PAYMENT_CREATE | PAYMENT_READ."),
						),
					responseSchema = "IssueMerchantApiKeyResponse",
					responseFields =
						listOf(
							fieldWithPath("merchantApiKeyId").description("API Key 식별자"),
							fieldWithPath("keyName").description("Key 이름"),
							fieldWithPath("environment").description("발급 환경. MVP는 TEST."),
							fieldWithPath("keyPrefix").description("식별·화면 표시용 Prefix"),
							fieldWithPath("scopes").description("허용 Scope 배열"),
							fieldWithPath("rawApiKey").description("전체 API Key 원문. 최초 1회만 노출된다."),
							fieldWithPath("createdAt").description("발급 시각(UTC)"),
						),
				)

			mockMvc
				.perform(
					post("/merchant/api-keys")
						.with(authenticatedAs(OWNER))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(
							objectMapper.writeValueAsString(
								IssueMerchantApiKeyRequest(keyName = "운영 서버용 Key", scopes = listOf("PAYMENT_CREATE", "PAYMENT_READ")),
							),
						),
				).andExpect(status().isCreated)
				.andDo(document("merchant-issue-api-key", snippet))
		}

		test("document GET list api keys") {
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
								createdAt = NOW,
								// lastUsedAt/revokedAt는 예시에 non-null을 넣는다 — null이면 restdocs-api-spec이
								// 타입을 추론 못 해 필드를 스펙에서 통째로 빠뜨린다(payment에서 겪은 함정).
								lastUsedAt = NOW.plusSeconds(3_600),
								revokedAt = NOW.plusSeconds(7_200),
							),
						),
				)

			val snippet =
				merchantResource(
					summary = "API Key 목록 조회",
					description = "OWNER/ADMIN만 조회할 수 있다. Secret 관련 필드(rawApiKey/secretHash)는 애초에 담기지 않는다.",
					responseSchema = "ListMerchantApiKeysResponse",
					responseFields =
						listOf(
							fieldWithPath("apiKeys").description("발급된 API Key 요약 배열"),
							fieldWithPath("apiKeys[].merchantApiKeyId").description("API Key 식별자"),
							fieldWithPath("apiKeys[].keyName").description("Key 이름"),
							fieldWithPath("apiKeys[].environment").description("발급 환경"),
							fieldWithPath("apiKeys[].keyPrefix").description("식별·화면 표시용 Prefix"),
							fieldWithPath("apiKeys[].scopes").description("허용 Scope 배열"),
							fieldWithPath("apiKeys[].status").description("ACTIVE | REVOKED | EXPIRED"),
							fieldWithPath("apiKeys[].createdAt").description("발급 시각(UTC)"),
							fieldWithPath("apiKeys[].lastUsedAt").description("마지막 사용 시각(UTC). 사용 전에는 null.").optional(),
							fieldWithPath("apiKeys[].revokedAt").description("폐기 시각(UTC). ACTIVE면 null.").optional(),
						),
				)

			mockMvc
				.perform(get("/merchant/api-keys").with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)
				.andDo(document("merchant-list-api-keys", snippet))
		}

		test("document DELETE revoke api key") {
			every { revokeMerchantApiKeyUseCase.execute(any()) } returns
				RevokeMerchantApiKeyResult(merchantApiKeyId = MerchantApiKeyId("mak_001"), revokedAt = NOW)

			val snippet =
				merchantResource(
					summary = "API Key 폐기",
					description = "OWNER/ADMIN만 폐기할 수 있다. 폐기된 Key는 다시 활성화하지 않는다 — 새 Key를 발급한다.",
					responseSchema = "RevokeMerchantApiKeyResponse",
					responseFields =
						listOf(
							fieldWithPath("merchantApiKeyId").description("폐기된 API Key 식별자"),
							fieldWithPath("revokedAt").description("폐기 시각(UTC)"),
						),
					pathParameters = listOf(parameterWithName("merchantApiKeyId").description("폐기할 API Key 식별자")),
				)

			mockMvc
				.perform(delete("/merchant/api-keys/{merchantApiKeyId}", "mak_001").with(authenticatedAs(OWNER)).with(csrf()))
				.andExpect(status().isOk)
				.andDo(document("merchant-revoke-api-key", snippet))
		}
	}
}
