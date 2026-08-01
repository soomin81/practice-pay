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
import org.springframework.restdocs.payload.JsonFieldType
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
import paytech.practice.pay.application.identity.AcceptAccountInvitationResult
import paytech.practice.pay.application.identity.AcceptAccountInvitationUseCase
import paytech.practice.pay.application.identity.AuthenticateMerchantUserResult
import paytech.practice.pay.application.identity.AuthenticateMerchantUserUseCase
import paytech.practice.pay.application.identity.ChangeMerchantUserRoleResult
import paytech.practice.pay.application.identity.ChangeMerchantUserRoleUseCase
import paytech.practice.pay.application.identity.ChangeMerchantUserStatusResult
import paytech.practice.pay.application.identity.ChangeMerchantUserStatusUseCase
import paytech.practice.pay.application.identity.InviteMerchantSubAccountResult
import paytech.practice.pay.application.identity.InviteMerchantSubAccountUseCase
import paytech.practice.pay.application.identity.ListMerchantUsersResult
import paytech.practice.pay.application.identity.ListMerchantUsersUseCase
import paytech.practice.pay.application.identity.ResendMerchantUserInvitationResult
import paytech.practice.pay.application.identity.ResendMerchantUserInvitationUseCase
import paytech.practice.pay.application.identity.RevokeMerchantUserInvitationResult
import paytech.practice.pay.application.identity.RevokeMerchantUserInvitationUseCase
import paytech.practice.pay.application.payment.ExportMerchantPaymentsUseCase
import paytech.practice.pay.application.payment.ListMerchantPaymentsUseCase
import paytech.practice.pay.application.payment.ListPaymentsResult
import paytech.practice.pay.application.port.outbound.MerchantApiKeySummary
import paytech.practice.pay.application.port.outbound.MerchantUserSummary
import paytech.practice.pay.application.port.outbound.PaymentListEntry
import paytech.practice.pay.application.port.outbound.SettlementReceivableListEntry
import paytech.practice.pay.application.settlement.ListMerchantSettlementReceivablesUseCase
import paytech.practice.pay.application.settlement.ListSettlementReceivablesResult
import paytech.practice.pay.domain.apikey.ApiEnvironment
import paytech.practice.pay.domain.apikey.ApiKeyPrefix
import paytech.practice.pay.domain.apikey.ApiKeyScope
import paytech.practice.pay.domain.apikey.ApiKeyStatus
import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

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
		MerchantSubAccountController::class,
		MerchantPaymentController::class,
		MerchantSettlementReceivableController::class,
		AcceptAccountInvitationController::class,
	],
)
@Import(SecurityConfig::class, FixedClockConfiguration::class)
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

	@MockkBean
	lateinit var inviteMerchantSubAccountUseCase: InviteMerchantSubAccountUseCase

	@MockkBean
	lateinit var listMerchantUsersUseCase: ListMerchantUsersUseCase

	@MockkBean
	lateinit var listMerchantPaymentsUseCase: ListMerchantPaymentsUseCase

	@MockkBean
	lateinit var exportMerchantPaymentsUseCase: ExportMerchantPaymentsUseCase

	@MockkBean
	lateinit var listSettlementReceivablesUseCase: ListMerchantSettlementReceivablesUseCase

	@MockkBean
	lateinit var acceptAccountInvitationUseCase: AcceptAccountInvitationUseCase

	@MockkBean
	lateinit var changeMerchantUserStatusUseCase: ChangeMerchantUserStatusUseCase

	@MockkBean
	lateinit var changeMerchantUserRoleUseCase: ChangeMerchantUserRoleUseCase

	@MockkBean
	lateinit var resendMerchantUserInvitationUseCase: ResendMerchantUserInvitationUseCase

	@MockkBean
	lateinit var revokeMerchantUserInvitationUseCase: RevokeMerchantUserInvitationUseCase

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

		test("document POST invite sub-account") {
			every { inviteMerchantSubAccountUseCase.execute(any()) } returns
				InviteMerchantSubAccountResult(
					merchantUserId = MerchantUserId("mu_002"),
					loginId = LoginId("new-admin"),
					email = Email("new-admin@example.com"),
					userName = "새 하위 계정",
					role = MerchantUserRole.ADMIN,
					invitationToken = "raw-invitation-token",
					invitationExpiresAt = NOW.plusSeconds(604_800),
				)

			val snippet =
				merchantResource(
					summary = "하위 계정 발급(초대)",
					description =
						"OWNER/ADMIN이 같은 가맹점의 ADMIN/VIEWER 하위 계정을 INVITED 상태로 만든다. merchantId는 받지 " +
							"않는다 — 항상 호출자 자신의 가맹점에 만들어진다(멀티테넌시 방어). OWNER는 이 경로로 만들 수 없다. " +
							"invitationToken은 이 응답에서만 원문으로 보이므로 대상자에게 즉시 전달해야 한다.",
					requestSchema = "InviteMerchantSubAccountRequest",
					requestFields =
						listOf(
							fieldWithPath("loginId").description("가맹점 내에서 유일한 로그인 아이디"),
							fieldWithPath("email").description("가맹점 내에서 유일한 이메일"),
							fieldWithPath("userName").description("사용자 이름"),
							fieldWithPath("role").description("ADMIN | VIEWER (OWNER 불가)"),
						),
					responseSchema = "InviteMerchantSubAccountResponse",
					responseFields =
						listOf(
							fieldWithPath("merchantUserId").description("생성된 가맹점 사용자 식별자"),
							fieldWithPath("loginId").description("로그인 아이디"),
							fieldWithPath("email").description("이메일"),
							fieldWithPath("userName").description("사용자 이름"),
							fieldWithPath("role").description("부여된 역할"),
							fieldWithPath("invitationToken").description("초대 Token 원문. 최초 1회만 노출된다."),
							fieldWithPath("invitationExpiresAt").description("초대 만료 시각(UTC)"),
						),
				)

			mockMvc
				.perform(
					post("/merchant/merchant-users")
						.with(authenticatedAs(OWNER))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(
							objectMapper.writeValueAsString(
								InviteMerchantSubAccountRequest(
									loginId = "new-admin",
									email = "new-admin@example.com",
									userName = "새 하위 계정",
									role = "ADMIN",
								),
							),
						),
				).andExpect(status().isCreated)
				.andDo(document("merchant-invite-sub-account", snippet))
		}

		test("document GET list merchant users") {
			every { listMerchantUsersUseCase.execute(any()) } returns
				ListMerchantUsersResult(
					merchantUsers =
						listOf(
							MerchantUserSummary(
								merchantUserId = MerchantUserId("mu_002"),
								loginId = LoginId("member01"),
								email = Email("member01@example.com"),
								userName = "팀원",
								role = MerchantUserRole.ADMIN,
								status = AccountStatus.ACTIVE,
								// lastLoginAt에 non-null을 넣는다 — null이면 restdocs-api-spec이 타입을
								// 추론 못 해 필드를 스펙에서 통째로 빠뜨린다(1번째 슬라이스에서 겪은 함정).
								lastLoginAt = NOW.plusSeconds(3_600),
								createdAt = NOW,
								// 같은 이유로 non-null을 넣는다(null이면 스펙에서 필드가 빠진다).
								pendingInvitationExpiresAt = NOW.plusSeconds(604_800),
							),
						),
				)

			val snippet =
				merchantResource(
					summary = "가맹점 사용자 목록 조회",
					description =
						"OWNER/ADMIN만 조회할 수 있다. 자신의 가맹점 명부만 나온다 — 누가 소속돼 있고 누가 아직 " +
							"INVITED로 남아 있는지 확인한다. 비밀번호 해시는 담기지 않는다.",
					responseSchema = "ListMerchantUsersResponse",
					responseFields =
						listOf(
							fieldWithPath("merchantUsers").description("가맹점 사용자 요약 배열(최신 생성순)"),
							fieldWithPath("merchantUsers[].merchantUserId").description("가맹점 사용자 식별자"),
							fieldWithPath("merchantUsers[].loginId").description("로그인 아이디"),
							fieldWithPath("merchantUsers[].email").description("이메일"),
							fieldWithPath("merchantUsers[].userName").description("사용자 이름"),
							fieldWithPath("merchantUsers[].role").description("OWNER | ADMIN | VIEWER"),
							fieldWithPath("merchantUsers[].status")
								.description("INVITED | ACTIVE | LOCKED | SUSPENDED | TERMINATED"),
							fieldWithPath("merchantUsers[].lastLoginAt")
								.description("마지막 로그인 시각(UTC). 로그인한 적이 없으면 null.")
								.optional(),
							fieldWithPath("merchantUsers[].createdAt").description("생성(초대) 시각(UTC)"),
							fieldWithPath("merchantUsers[].pendingInvitationExpiresAt")
								.description(
									"유효한(PENDING) 초대의 만료 시각(UTC). null이면 초대가 없거나 취소된 상태이고, " +
										"과거면 만료된 것이다 — INVITED 사용자가 왜 활성화되지 않았는지를 여기서 판단한다.",
								).optional(),
						),
				)

			mockMvc
				.perform(get("/merchant/merchant-users").with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)
				.andDo(document("merchant-list-merchant-users", snippet))
		}

		test("document account status actions (suspend/reactivate/terminate)") {
			// 셋은 경로만 다르고 요청·응답 형태가 같아서 한 테스트에서 함께 문서화한다.
			val actions =
				listOf(
					Triple("suspend", AccountStatus.SUSPENDED, "계정 정지"),
					Triple("reactivate", AccountStatus.ACTIVE, "계정 재개"),
					Triple("terminate", AccountStatus.TERMINATED, "계정 종료"),
				)

			actions.forEach { (path, resultingStatus, summary) ->
				every { changeMerchantUserStatusUseCase.execute(any()) } returns
					ChangeMerchantUserStatusResult(
						merchantUserId = MerchantUserId("mu_002"),
						status = resultingStatus,
						changedAt = NOW,
					)

				val snippet =
					merchantResource(
						summary = summary,
						description =
							"OWNER/ADMIN만 호출할 수 있다. 자기 자신은 대상이 될 수 없고, ADMIN은 OWNER를 변경할 수 없다(403). " +
								"가맹점의 마지막 활성 OWNER를 정지·종료하려 하면 409다(최소 하나의 활성 OWNER를 유지한다). " +
								"허용되지 않는 상태 전이(예: 종료된 계정 재개)도 409다.",
						responseSchema = "ChangeMerchantUserStatusResponse",
						responseFields =
							listOf(
								fieldWithPath("merchantUserId").description("대상 가맹점 사용자 식별자"),
								fieldWithPath("status").description("변경 후 계정 상태"),
								fieldWithPath("changedAt").description("변경 시각(UTC)"),
							),
						pathParameters = listOf(parameterWithName("merchantUserId").description("대상 가맹점 사용자 식별자")),
					)

				mockMvc
					.perform(
						post("/merchant/merchant-users/{merchantUserId}/$path", "mu_002")
							.with(authenticatedAs(OWNER))
							.with(csrf()),
					).andExpect(status().isOk)
					.andDo(document("merchant-$path-user", snippet))
			}
		}

		test("document POST change merchant user role") {
			every { changeMerchantUserRoleUseCase.execute(any()) } returns
				ChangeMerchantUserRoleResult(
					merchantUserId = MerchantUserId("mu_002"),
					role = MerchantUserRole.VIEWER,
					changedAt = NOW,
				)

			val snippet =
				merchantResource(
					summary = "역할 변경",
					description =
						"OWNER/ADMIN만 호출할 수 있다. **OWNER로 승격할 수 없다**(400) — 최초 OWNER는 가맹점 등록에서만 " +
							"생성된다. ADMIN은 OWNER의 역할을 변경할 수 없고(403), 마지막 활성 OWNER는 강등할 수 없다(409).",
					requestSchema = "ChangeMerchantUserRoleRequest",
					requestFields = listOf(fieldWithPath("role").description("변경할 역할. ADMIN | VIEWER (OWNER 불가)")),
					responseSchema = "ChangeMerchantUserRoleResponse",
					responseFields =
						listOf(
							fieldWithPath("merchantUserId").description("대상 가맹점 사용자 식별자"),
							fieldWithPath("role").description("변경 후 역할"),
							fieldWithPath("changedAt").description("변경 시각(UTC)"),
						),
					pathParameters = listOf(parameterWithName("merchantUserId").description("대상 가맹점 사용자 식별자")),
				)

			mockMvc
				.perform(
					post("/merchant/merchant-users/{merchantUserId}/role", "mu_002")
						.with(authenticatedAs(OWNER))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(ChangeMerchantUserRoleRequest(role = "VIEWER"))),
				).andExpect(status().isOk)
				.andDo(document("merchant-change-user-role", snippet))
		}

		test("document POST resend invitation") {
			every { resendMerchantUserInvitationUseCase.execute(any()) } returns
				ResendMerchantUserInvitationResult(
					merchantUserId = MerchantUserId("mu_002"),
					invitationToken = "new-raw-invitation-token",
					invitationExpiresAt = NOW.plusSeconds(604_800),
				)

			val snippet =
				merchantResource(
					summary = "초대 재발송",
					description =
						"초대 Token은 Hash만 저장돼 원문을 다시 볼 수 없으므로, 재발송은 기존 링크를 다시 보여주는 게 " +
							"아니라 **새 Token을 발급하는 것**이다 — 이전 초대 링크는 이 시점에 무효가 된다. 대상이 " +
							"INVITED가 아니면 409다(이미 활성화된 계정에 다시 보낼 이유가 없다).",
					responseSchema = "ResendInvitationResponse",
					responseFields =
						listOf(
							fieldWithPath("merchantUserId").description("대상 가맹점 사용자 식별자"),
							fieldWithPath("invitationToken").description("새 초대 Token 원문. 최초 1회만 노출된다."),
							fieldWithPath("invitationExpiresAt").description("새 초대의 만료 시각(UTC)"),
						),
					pathParameters = listOf(parameterWithName("merchantUserId").description("대상 가맹점 사용자 식별자")),
				)

			mockMvc
				.perform(
					post("/merchant/merchant-users/{merchantUserId}/invitation/resend", "mu_002")
						.with(authenticatedAs(OWNER))
						.with(csrf()),
				).andExpect(status().isOk)
				.andDo(document("merchant-resend-invitation", snippet))
		}

		test("document POST revoke invitation") {
			every { revokeMerchantUserInvitationUseCase.execute(any()) } returns
				RevokeMerchantUserInvitationResult(merchantUserId = MerchantUserId("mu_002"), revokedAt = NOW)

			val snippet =
				merchantResource(
					summary = "초대 취소",
					description =
						"초대 Token을 무효화해 그 링크로는 더 이상 활성화할 수 없게 한다. **계정 자체는 INVITED로 남는다** " +
							"— 계정을 없애려면 종료를 쓴다(되돌릴 수 없는 동작이 가벼운 이름 뒤에 숨지 않게 분리했다). " +
							"취소할 PENDING 초대가 없으면 409다.",
					responseSchema = "RevokeInvitationResponse",
					responseFields =
						listOf(
							fieldWithPath("merchantUserId").description("대상 가맹점 사용자 식별자"),
							fieldWithPath("revokedAt").description("취소 시각(UTC)"),
						),
					pathParameters = listOf(parameterWithName("merchantUserId").description("대상 가맹점 사용자 식별자")),
				)

			mockMvc
				.perform(
					post("/merchant/merchant-users/{merchantUserId}/invitation/revoke", "mu_002")
						.with(authenticatedAs(OWNER))
						.with(csrf()),
				).andExpect(status().isOk)
				.andDo(document("merchant-revoke-invitation", snippet))
		}

		test("document POST accept account invitation") {
			every { acceptAccountInvitationUseCase.execute(any()) } returns
				AcceptAccountInvitationResult(loginId = LoginId("new-admin"), activatedAt = NOW)

			val snippet =
				merchantResource(
					summary = "초대 수락(계정 활성화)",
					description =
						"초대받은 사람이 Token과 새 비밀번호로 계정을 INVITED → ACTIVE로 활성화한다. 인증이 필요 없고, " +
							"**CSRF 토큰도 요구하지 않는다** — 자격증명이 세션 쿠키가 아니라 본문의 초대 Token 자체라 " +
							"CSRF가 막으려는 상황이 성립하지 않는다(merchant-console-api.md 2절).",
					requestSchema = "AcceptAccountInvitationRequest",
					requestFields =
						listOf(
							fieldWithPath("invitationToken").description("발급 응답에서 받은 초대 Token 원문"),
							fieldWithPath("newPassword").description("설정할 새 비밀번호"),
						),
					responseSchema = "AcceptAccountInvitationResponse",
					responseFields =
						listOf(
							fieldWithPath("loginId").description("활성화된 계정의 로그인 아이디"),
							fieldWithPath("activatedAt").description("활성화 시각(UTC)"),
						),
				)

			mockMvc
				.perform(
					post("/merchant/account-invitations/accept")
						.contentType(MediaType.APPLICATION_JSON)
						.content(
							objectMapper.writeValueAsString(
								AcceptAccountInvitationRequest(
									invitationToken = "raw-invitation-token",
									newPassword = "new-password-123",
								),
							),
						),
				).andExpect(status().isOk)
				.andDo(document("merchant-accept-invitation", snippet))
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

		test("document GET payments") {
			every { listMerchantPaymentsUseCase.execute(any(), any()) } returns
				ListPaymentsResult(
					entries =
						listOf(
							PaymentListEntry(
								paymentId = PaymentId("pay_3b81"),
								merchantId = MerchantId("mrc_001"),
								merchantName = "테스트 가맹점",
								merchantOrderId = MerchantOrderId("order-1001"),
								orderName = "테스트 상품",
								orderAmount = Money(50_000),
								paymentAsset = Asset.USDC,
								paymentAmount = TokenAmount(72_992_701),
								tokenDecimals = 6,
								network = BlockchainNetwork.BASE_SEPOLIA,
								status = PaymentStatus.SUCCEEDED,
								failureReason = null,
								transactionHash = TransactionHash("0x" + "7f3a".repeat(16)),
								paidAt = NOW,
								createdAt = NOW,
							),
						),
					totalCount = 1L,
					page = 0,
					size = 50,
				)

			val snippet =
				merchantResource(
					summary = "결제 내역 조회(자기 가맹점)",
					description =
						"**인증된 가맹점 사용자 전원**(VIEWER 포함)이 조회할 수 있다. 조회 범위는 세션의 가맹점으로 " +
							"서버가 고정하므로 merchantId를 보낼 수 없다. 쿼리 파라미터로 좁힌다: status(PaymentStatus), " +
							"from/to(생성 시각 ISO-8601 UTC), page(0부터), size. size는 서버가 최대 200으로 자르고 " +
							"실제로 적용된 값을 응답의 size로 돌려준다. 정렬은 생성 시각 최신순이다. paymentAmount는 " +
							"Minor Unit 정수를 **문자열로** 준다 — 토큰 금액이 JavaScript Number의 안전 정수 범위를 넘을 수 있다.",
					responseSchema = "ListPaymentsResponse",
					responseFields =
						listOf(
							fieldWithPath("payments").description("결제 배열(생성 시각 최신순)"),
							fieldWithPath("payments[].paymentId").description("결제 식별자"),
							fieldWithPath("payments[].merchantOrderId").description("가맹점이 부여한 주문 식별자"),
							fieldWithPath("payments[].orderName").description("주문명"),
							fieldWithPath("payments[].orderAmount").description("KRW 주문 금액(원 단위 정수)"),
							fieldWithPath("payments[].paymentAsset").description("결제 자산 코드(USDC)"),
							fieldWithPath("payments[].paymentAmount").description("결제 토큰 금액. Minor Unit 정수를 문자열로 준다."),
							fieldWithPath("payments[].tokenDecimals").description("토큰 소수 자릿수(USDC는 6)"),
							fieldWithPath("payments[].network").description("블록체인 네트워크 코드"),
							fieldWithPath("payments[].status").description("PaymentStatus 값"),
							fieldWithPath("payments[].failureReason").type(JsonFieldType.STRING).description("실패 사유. FAILED가 아니면 null.").optional(),
							fieldWithPath("payments[].transactionHash").type(JsonFieldType.STRING).description("온체인 거래 Hash. 고객이 제출하기 전이면 null.").optional(),
							fieldWithPath("payments[].paidAt").type(JsonFieldType.STRING).description("결제 완료 시각(UTC). SUCCEEDED가 아니면 null.").optional(),
							fieldWithPath("payments[].createdAt").description("결제 생성 시각(UTC)"),
							fieldWithPath("totalCount").description("필터 전체에 걸린 건수(현재 페이지 건수가 아니다)"),
							fieldWithPath("page").description("조회한 페이지 번호(0부터)"),
							fieldWithPath("size").description("실제로 적용된 페이지 크기. 상한에 걸리면 요청값과 다르다."),
						),
				)

			mockMvc
				.perform(get("/merchant/payments").with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)
				.andDo(document("merchant-payments", snippet))
		}

		test("document GET settlement receivables") {
			every { listSettlementReceivablesUseCase.execute(any(), any()) } returns
				ListSettlementReceivablesResult(
					entries =
						listOf(
							SettlementReceivableListEntry(
								settlementReceivableId = SettlementReceivableId("str_9a1c"),
								merchantId = MerchantId("mrc_test_001"),
								merchantName = "테스트 가맹점",
								paymentId = PaymentId("pay_3b81"),
								merchantOrderId = MerchantOrderId("order-1001"),
								status = SettlementReceivableStatus.READY,
								settlementCurrency = "KRW",
								grossAmount = 20_000,
								feeRate = BigDecimal("0.015"),
								feeAmount = 300,
								adjustmentAmount = 0,
								netAmount = 19_700,
								exchangeReceivedAmount = 20_101,
								exchangeProfitLossAmount = 101,
								eligibleDate = LocalDate.parse("2026-08-01"),
								createdAt = NOW,
							),
						),
					totalCount = 1L,
					totalNetAmount = 19_700L,
					page = 0,
					size = 50,
				)

			val snippet =
				merchantResource(
					summary = "정산 채권 조회(자기 가맹점)",
					description =
						"**인증된 가맹점 사용자 전원**(VIEWER 포함)이 조회할 수 있다. 조회 범위는 세션의 가맹점으로 " +
							"서버가 고정하므로 merchantId를 보낼 수 없다. 쿼리 파라미터: status, " +
							"eligibleFrom/eligibleTo(**정산 예정일 기준 날짜** YYYY-MM-DD), page, size(최대 200). " +
							"**totalNetAmount는 현재 페이지가 아니라 필터 전체의 정산 예정 금액 합계**다 — " +
							"\"그래서 얼마를 받나\"가 이 화면의 질문이다. 금액은 KRW 원 단위 정수라 모두 숫자로 준다. " +
							"응답에 가맹점 열이 없다(언제나 자기 가맹점 하나다).",
					responseSchema = "ListSettlementReceivablesResponse",
					responseFields =
						listOf(
							fieldWithPath("settlementReceivables").description("정산 채권 배열(정산 예정일 최신순)"),
							fieldWithPath("settlementReceivables[].settlementReceivableId").description("정산 채권 식별자"),
							fieldWithPath("settlementReceivables[].paymentId").description("이 채권을 만든 결제"),
							fieldWithPath("settlementReceivables[].merchantOrderId").description("가맹점이 부여한 주문 식별자"),
							fieldWithPath("settlementReceivables[].status").description("SettlementReceivableStatus 값(MVP 종착은 READY)"),
							fieldWithPath("settlementReceivables[].settlementCurrency").description("정산 통화(KRW)"),
							fieldWithPath("settlementReceivables[].grossAmount").description("정산 기준 금액"),
							fieldWithPath("settlementReceivables[].feeRate").description("적용 수수료율"),
							fieldWithPath("settlementReceivables[].feeAmount").description("수수료"),
							fieldWithPath("settlementReceivables[].adjustmentAmount").description("조정 금액(음수 가능)"),
							fieldWithPath("settlementReceivables[].netAmount").description("정산 예정 금액 = gross - fee + adjustment"),
							fieldWithPath("settlementReceivables[].exchangeReceivedAmount")
								.type(JsonFieldType.NUMBER)
								.description("환전으로 확보한 KRW. READY 전에는 null.")
								.optional(),
							fieldWithPath("settlementReceivables[].exchangeProfitLossAmount")
								.type(JsonFieldType.NUMBER)
								.description("확보액과 정산 기준 금액의 차이. 음수 가능, READY 전에는 null.")
								.optional(),
							fieldWithPath("settlementReceivables[].eligibleDate").description("정산 예정일(YYYY-MM-DD)"),
							fieldWithPath("settlementReceivables[].createdAt").description("생성 시각(UTC)"),
							fieldWithPath("totalCount").description("필터 전체에 걸린 건수"),
							fieldWithPath("totalNetAmount").description("필터 전체의 정산 예정 금액 합계"),
							fieldWithPath("page").description("조회한 페이지 번호(0부터)"),
							fieldWithPath("size").description("실제로 적용된 페이지 크기"),
						),
				)

			mockMvc
				.perform(get("/merchant/settlement-receivables").with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)
				.andDo(document("merchant-settlement-receivables", snippet))
		}
	}
}
