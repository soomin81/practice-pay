package paytech.practice.pay.api.admin.web

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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.api.admin.config.SecurityConfig
import paytech.practice.pay.api.admin.security.InternalUserPrincipal
import paytech.practice.pay.application.identity.AcceptAccountInvitationResult
import paytech.practice.pay.application.identity.AcceptAccountInvitationUseCase
import paytech.practice.pay.application.identity.AdminChangeMerchantUserRoleUseCase
import paytech.practice.pay.application.identity.AdminChangeMerchantUserStatusUseCase
import paytech.practice.pay.application.identity.AdminListMerchantUsersUseCase
import paytech.practice.pay.application.identity.AuthenticateInternalUserResult
import paytech.practice.pay.application.identity.AuthenticateInternalUserUseCase
import paytech.practice.pay.application.identity.ChangeInternalUserRoleResult
import paytech.practice.pay.application.identity.ChangeInternalUserRoleUseCase
import paytech.practice.pay.application.identity.ChangeInternalUserStatusResult
import paytech.practice.pay.application.identity.ChangeInternalUserStatusUseCase
import paytech.practice.pay.application.identity.ChangeMerchantUserRoleResult
import paytech.practice.pay.application.identity.ChangeMerchantUserStatusResult
import paytech.practice.pay.application.identity.IssueInternalUserResult
import paytech.practice.pay.application.identity.IssueInternalUserUseCase
import paytech.practice.pay.application.identity.ListInternalLoginAuditResult
import paytech.practice.pay.application.identity.ListInternalLoginAuditUseCase
import paytech.practice.pay.application.identity.ListInternalUsersResult
import paytech.practice.pay.application.identity.ListInternalUsersUseCase
import paytech.practice.pay.application.identity.ListMerchantLoginAuditResult
import paytech.practice.pay.application.identity.ListMerchantLoginAuditUseCase
import paytech.practice.pay.application.identity.ListMerchantUsersResult
import paytech.practice.pay.application.identity.RegisterMerchantResult
import paytech.practice.pay.application.identity.RegisterMerchantUseCase
import paytech.practice.pay.application.merchant.ListMerchantsResult
import paytech.practice.pay.application.merchant.ListMerchantsUseCase
import paytech.practice.pay.application.payment.ListPaymentsResult
import paytech.practice.pay.application.payment.ListPaymentsUseCase
import paytech.practice.pay.application.port.outbound.InternalLoginAuditEntry
import paytech.practice.pay.application.port.outbound.InternalUserSummary
import paytech.practice.pay.application.port.outbound.MerchantLoginAuditEntry
import paytech.practice.pay.application.port.outbound.MerchantSummary
import paytech.practice.pay.application.port.outbound.MerchantUserSummary
import paytech.practice.pay.application.port.outbound.PaymentListEntry
import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalLoginAuditId
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.LoginOutcome
import paytech.practice.pay.domain.identity.MerchantLoginAuditId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.merchant.MerchantStatus
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import tools.jackson.databind.ObjectMapper
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-19T00:00:00Z")
private val SUPER_ADMIN =
	InternalUserPrincipal(InternalUserId("iu_sa01"), LoginId("super-admin"), InternalUserRole.SUPER_ADMIN)
private val VIEWER =
	InternalUserPrincipal(InternalUserId("iu_vw01"), LoginId("viewer01"), InternalUserRole.VIEWER)
private val OPERATOR =
	InternalUserPrincipal(InternalUserId("iu_op01"), LoginId("operator01"), InternalUserRole.OPERATOR)

private fun authenticatedAs(principal: InternalUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

/**
 * 모든 내부 운영자 콘솔 엔드포인트가 공유하는 스니펫 형태다 — `api-merchant`의
 * `merchantResource`와 같은 이유·같은 모양(특히 `resource(builder.build())`로 감싸지 않으면
 * 스키마가 조용히 빈 `type: object`가 된다).
 */
private fun adminResource(
	summary: String,
	description: String,
	responseSchema: String? = null,
	responseFields: List<FieldDescriptor> = emptyList(),
	requestSchema: String? = null,
	requestFields: List<FieldDescriptor> = emptyList(),
): ResourceSnippet {
	val builder =
		ResourceSnippetParameters
			.builder()
			.tag("Admin Console")
			.summary(summary)
			.description(description)

	responseSchema?.let { builder.responseSchema(Schema(it)) }
	if (responseFields.isNotEmpty()) builder.responseFields(responseFields)
	requestSchema?.let { builder.requestSchema(Schema(it)) }
	if (requestFields.isNotEmpty()) builder.requestFields(requestFields)

	return resource(builder.build())
}

/**
 * 통과한 테스트에서 내부 운영자 콘솔 API의 OpenAPI 스펙을 만든다 — 프론트엔드
 * (`frontend/admin`)가 백엔드 소스가 아니라 이 스펙으로 타입을 생성한다.
 *
 * 오류 응답은 문서화하지 않는다(MockMvc가 컨테이너의 ERROR 디스패치를 재현하지 못한다) —
 * 오류 코드는 `docs/architecture/admin-console-api.md`에 산문으로 남긴다. CSRF/세션 계약도
 * 같은 이유로 그 문서의 몫이다.
 */
@WebMvcTest(
	controllers = [
		AdminLoginController::class,
		AdminMeController::class,
		AdminLogoutController::class,
		MerchantController::class,
		InternalUserController::class,
		AdminMerchantUserController::class,
		LoginAuditController::class,
		MerchantLoginAuditController::class,
		AdminPaymentController::class,
		AcceptAccountInvitationController::class,
	],
)
@Import(SecurityConfig::class)
@AutoConfigureRestDocs
class AdminApiDocumentationTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@Autowired
	lateinit var objectMapper: ObjectMapper

	@MockkBean
	lateinit var authenticateInternalUserUseCase: AuthenticateInternalUserUseCase

	@MockkBean
	lateinit var registerMerchantUseCase: RegisterMerchantUseCase

	@MockkBean
	lateinit var listMerchantsUseCase: ListMerchantsUseCase

	@MockkBean
	lateinit var issueInternalUserUseCase: IssueInternalUserUseCase

	@MockkBean
	lateinit var listInternalUsersUseCase: ListInternalUsersUseCase

	@MockkBean
	lateinit var acceptAccountInvitationUseCase: AcceptAccountInvitationUseCase

	@MockkBean
	lateinit var changeInternalUserStatusUseCase: ChangeInternalUserStatusUseCase

	@MockkBean
	lateinit var changeInternalUserRoleUseCase: ChangeInternalUserRoleUseCase

	@MockkBean
	lateinit var adminListMerchantUsersUseCase: AdminListMerchantUsersUseCase

	@MockkBean
	lateinit var adminChangeMerchantUserStatusUseCase: AdminChangeMerchantUserStatusUseCase

	@MockkBean
	lateinit var adminChangeMerchantUserRoleUseCase: AdminChangeMerchantUserRoleUseCase

	@MockkBean
	lateinit var listInternalLoginAuditUseCase: ListInternalLoginAuditUseCase

	@MockkBean
	lateinit var listMerchantLoginAuditUseCase: ListMerchantLoginAuditUseCase

	@MockkBean
	lateinit var listPaymentsUseCase: ListPaymentsUseCase

	init {
		extensions(SpringExtension)

		test("document POST login") {
			every { authenticateInternalUserUseCase.execute(any()) } returns
				AuthenticateInternalUserResult(
					internalUserId = InternalUserId("iu_op01"),
					loginId = LoginId("operator01"),
					userName = "테스트 운영자",
					role = InternalUserRole.OPERATOR,
				)

			val snippet =
				adminResource(
					summary = "내부 운영자 로그인",
					description =
						"로그인에 성공하면 세션 쿠키(Set-Cookie)를 내리고 본문에 신원 정보를 담는다. 가맹점 콘솔과 달리 " +
							"merchantCode가 없다 — 내부 운영자는 특정 가맹점에 속하지 않는다. 상태 변경 요청이라 CSRF " +
							"토큰(X-XSRF-TOKEN)이 필요하다(GET /admin/me로 먼저 XSRF-TOKEN 쿠키를 받는다).",
					requestSchema = "AdminLoginRequest",
					requestFields =
						listOf(
							fieldWithPath("loginId").description("내부 운영자 로그인 아이디"),
							fieldWithPath("password").description("비밀번호"),
						),
					responseSchema = "AdminLoginResponse",
					responseFields =
						listOf(
							fieldWithPath("internalUserId").description("내부 운영자 식별자"),
							fieldWithPath("loginId").description("로그인 아이디"),
							fieldWithPath("userName").description("사용자 이름"),
							fieldWithPath("role").description("SUPER_ADMIN | OPERATOR | VIEWER"),
						),
				)

			mockMvc
				.perform(
					post("/admin/login")
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(
							objectMapper.writeValueAsString(
								AdminLoginRequest(loginId = "operator01", password = "correct-horse-battery-staple"),
							),
						),
				).andExpect(status().isOk)
				.andDo(document("admin-login", snippet))
		}

		test("document GET me") {
			val snippet =
				adminResource(
					summary = "현재 세션 사용자 조회(세션 복원)",
					description =
						"프론트가 새로고침 후 로그인 상태를 복원할 때 쓴다. 미인증이면 401이고 프론트는 그것을 " +
							"'로그아웃 상태'로 해석한다. 이 GET은 CSRF 토큰 발급도 겸한다(응답에 XSRF-TOKEN 쿠키가 실린다).",
					responseSchema = "AdminMeResponse",
					responseFields =
						listOf(
							fieldWithPath("internalUserId").description("내부 운영자 식별자"),
							fieldWithPath("loginId").description("로그인 아이디"),
							fieldWithPath("role").description("SUPER_ADMIN | OPERATOR | VIEWER"),
						),
				)

			mockMvc
				.perform(get("/admin/me").with(authenticatedAs(OPERATOR)))
				.andExpect(status().isOk)
				.andDo(document("admin-me", snippet))
		}

		test("document POST logout") {
			val snippet =
				adminResource(
					summary = "로그아웃",
					description = "세션을 무효화한다. 본문이 없고 204를 돌려준다. 상태 변경 요청이라 CSRF 토큰이 필요하다.",
				)

			mockMvc
				.perform(post("/admin/logout").with(authenticatedAs(OPERATOR)).with(csrf()))
				.andExpect(status().isNoContent)
				.andDo(document("admin-logout", snippet))
		}

		test("document GET merchants") {
			every { listMerchantsUseCase.execute() } returns
				ListMerchantsResult(
					merchants =
						listOf(
							MerchantSummary(
								merchantId = MerchantId("mrc_001"),
								merchantCode = MerchantCode("TEST_MERCHANT"),
								merchantName = "테스트 가맹점",
								status = MerchantStatus.ACTIVE,
								createdAt = NOW,
							),
						),
				)

			val snippet =
				adminResource(
					summary = "가맹점 목록 조회",
					description =
						"인증된 내부 운영자 전원이 조회할 수 있다 — VIEWER도 포함된다(VIEWER는 '조회 전용'으로 정의돼 " +
							"있다). 등록(POST)만 SUPER_ADMIN/OPERATOR로 제한된다.",
					responseSchema = "ListMerchantsResponse",
					responseFields =
						listOf(
							fieldWithPath("merchants").description("가맹점 요약 배열"),
							fieldWithPath("merchants[].merchantId").description("가맹점 식별자"),
							fieldWithPath("merchants[].merchantCode").description("가맹점 코드(로그인 시 사용)"),
							fieldWithPath("merchants[].merchantName").description("가맹점 이름"),
							fieldWithPath("merchants[].status").description("가맹점 상태"),
							fieldWithPath("merchants[].createdAt").description("등록 시각(UTC)"),
						),
				)

			mockMvc
				.perform(get("/admin/merchants").with(authenticatedAs(OPERATOR)))
				.andExpect(status().isOk)
				.andDo(document("admin-list-merchants", snippet))
		}

		test("document POST issue internal user") {
			every { issueInternalUserUseCase.execute(any()) } returns
				IssueInternalUserResult(
					internalUserId = InternalUserId("iu_002"),
					loginId = LoginId("new-operator"),
					email = Email("new-operator@example.com"),
					userName = "새 운영자",
					role = InternalUserRole.OPERATOR,
					invitationToken = "raw-invitation-token",
					invitationExpiresAt = NOW.plusSeconds(604_800),
				)

			val snippet =
				adminResource(
					summary = "내부 운영자 계정 발급",
					description =
						"SUPER_ADMIN만 호출할 수 있다(일반 회원가입은 제공하지 않는다). invitationToken은 이 응답에서만 " +
							"원문으로 보이며, **그 사람이 활성화할 곳은 이 콘솔 자신의 /accept-invitation**이다 " +
							"— 가맹점 등록이 만드는 링크가 가맹점 콘솔을 가리키는 것과 대비된다.",
					requestSchema = "IssueInternalUserRequest",
					requestFields =
						listOf(
							fieldWithPath("loginId").description("전 시스템에서 유일한 로그인 아이디"),
							fieldWithPath("email").description("전 시스템에서 유일한 이메일"),
							fieldWithPath("userName").description("사용자 이름"),
							fieldWithPath("role")
								.description("OPERATOR | VIEWER. **SUPER_ADMIN을 보내면 400이다** — 최초 SUPER_ADMIN은 Bootstrap으로만 생성한다."),
						),
					responseSchema = "IssueInternalUserResponse",
					responseFields =
						listOf(
							fieldWithPath("internalUserId").description("생성된 내부 운영자 식별자"),
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
					post("/admin/internal-users")
						.with(authenticatedAs(SUPER_ADMIN))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(
							objectMapper.writeValueAsString(
								IssueInternalUserRequest(
									loginId = "new-operator",
									email = "new-operator@example.com",
									userName = "새 운영자",
									role = "OPERATOR",
								),
							),
						),
				).andExpect(status().isCreated)
				.andDo(document("admin-issue-internal-user", snippet))
		}

		test("document GET internal users") {
			every { listInternalUsersUseCase.execute() } returns
				ListInternalUsersResult(
					internalUsers =
						listOf(
							InternalUserSummary(
								internalUserId = InternalUserId("iu_002"),
								loginId = LoginId("operator01"),
								email = Email("operator01@example.com"),
								userName = "운영자",
								role = InternalUserRole.OPERATOR,
								status = AccountStatus.ACTIVE,
								// non-null을 넣는다 — null이면 restdocs-api-spec이 타입을 추론 못 해
								// 필드를 스펙에서 통째로 빠뜨린다(다른 앱에서 겪은 함정).
								lastLoginAt = NOW.plusSeconds(3_600),
								createdAt = NOW,
							),
						),
				)

			val snippet =
				adminResource(
					summary = "내부 운영자 목록 조회",
					description =
						"**SUPER_ADMIN만 조회할 수 있다** — 가맹점 목록(GET)이 VIEWER에게도 열려 있는 것과 다르다. " +
							"명부에는 직원 이메일·마지막 로그인·누가 SUPER_ADMIN인지가 담기고, 계정 관리 자체가 " +
							"SUPER_ADMIN의 영역이기 때문이다. 비밀번호 해시는 담기지 않는다.",
					responseSchema = "ListInternalUsersResponse",
					responseFields =
						listOf(
							fieldWithPath("internalUsers").description("내부 운영자 요약 배열(최신 생성순)"),
							fieldWithPath("internalUsers[].internalUserId").description("내부 운영자 식별자"),
							fieldWithPath("internalUsers[].loginId").description("로그인 아이디"),
							fieldWithPath("internalUsers[].email").description("이메일"),
							fieldWithPath("internalUsers[].userName").description("사용자 이름"),
							fieldWithPath("internalUsers[].role").description("SUPER_ADMIN | OPERATOR | VIEWER"),
							fieldWithPath("internalUsers[].status")
								.description("INVITED | ACTIVE | LOCKED | SUSPENDED | TERMINATED"),
							fieldWithPath("internalUsers[].lastLoginAt")
								.description("마지막 로그인 시각(UTC). 로그인한 적이 없으면 null.")
								.optional(),
							fieldWithPath("internalUsers[].createdAt").description("생성(초대) 시각(UTC)"),
						),
				)

			mockMvc
				.perform(get("/admin/internal-users").with(authenticatedAs(SUPER_ADMIN)))
				.andExpect(status().isOk)
				.andDo(document("admin-list-internal-users", snippet))
		}

		test("document POST accept account invitation") {
			every { acceptAccountInvitationUseCase.execute(any()) } returns
				AcceptAccountInvitationResult(loginId = LoginId("new-operator"), activatedAt = NOW)

			val snippet =
				adminResource(
					summary = "초대 수락(내부 운영자 계정 활성화)",
					description =
						"초대받은 내부 직원이 Token과 새 비밀번호로 계정을 INVITED → ACTIVE로 활성화한다. 인증이 필요 " +
							"없고 **CSRF 토큰도 요구하지 않는다**(자격증명이 세션 쿠키가 아니라 본문의 Token 자체다). " +
							"가맹점 사용자 초대는 이 엔드포인트가 아니라 api-merchant의 같은 경로로 간다.",
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
					post("/admin/account-invitations/accept")
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
				.andDo(document("admin-accept-invitation", snippet))
		}

		test("document POST register merchant") {
			every { registerMerchantUseCase.execute(any()) } returns
				RegisterMerchantResult(
					merchantId = MerchantId("mrc_002"),
					merchantCode = MerchantCode("NEW_MERCHANT"),
					merchantName = "새 가맹점",
					ownerMerchantUserId = MerchantUserId("mu_owner01"),
					ownerLoginId = LoginId("new-owner"),
					ownerEmail = Email("new-owner@example.com"),
					invitationToken = "raw-invitation-token",
					invitationExpiresAt = NOW.plusSeconds(604_800),
				)

			val snippet =
				adminResource(
					summary = "가맹점 등록(+ 최초 OWNER 초대)",
					description =
						"SUPER_ADMIN/OPERATOR만 호출할 수 있다. 가맹점과 최초 OWNER 계정을 한 트랜잭션에서 만든다 — " +
							"가맹점은 스스로 가입할 수 없다. invitationToken은 이 응답에서만 원문으로 보이며, **그 OWNER가 " +
							"활성화할 곳은 이 콘솔이 아니라 가맹점 콘솔의 /accept-invitation**이다.",
					requestSchema = "RegisterMerchantRequest",
					requestFields =
						listOf(
							fieldWithPath("merchantCode").description("가맹점 코드(전 시스템에서 유일, 가맹점 사용자 로그인에 쓰인다)"),
							fieldWithPath("merchantName").description("가맹점 이름"),
							fieldWithPath("webhookUrl").description("결제 결과를 받을 Webhook URL. 선택값이다.").optional(),
							fieldWithPath("ownerLoginId").description("최초 OWNER의 로그인 아이디"),
							fieldWithPath("ownerEmail").description("최초 OWNER의 이메일"),
							fieldWithPath("ownerUserName").description("최초 OWNER의 이름"),
						),
					responseSchema = "RegisterMerchantResponse",
					responseFields =
						listOf(
							fieldWithPath("merchantId").description("생성된 가맹점 식별자"),
							fieldWithPath("merchantCode").description("가맹점 코드"),
							fieldWithPath("merchantName").description("가맹점 이름"),
							fieldWithPath("ownerMerchantUserId").description("생성된 OWNER 계정 식별자"),
							fieldWithPath("ownerLoginId").description("OWNER 로그인 아이디"),
							fieldWithPath("ownerEmail").description("OWNER 이메일"),
							fieldWithPath("invitationToken").description("OWNER 초대 Token 원문. 최초 1회만 노출된다."),
							fieldWithPath("invitationExpiresAt").description("초대 만료 시각(UTC)"),
						),
				)

			mockMvc
				.perform(
					post("/admin/merchants")
						.with(authenticatedAs(OPERATOR))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(
							objectMapper.writeValueAsString(
								RegisterMerchantRequest(
									merchantCode = "NEW_MERCHANT",
									merchantName = "새 가맹점",
									webhookUrl = "https://merchant.example.com/webhooks/payments",
									ownerLoginId = "new-owner",
									ownerEmail = "new-owner@example.com",
									ownerUserName = "새 오너",
								),
							),
						),
				).andExpect(status().isCreated)
				.andDo(document("admin-register-merchant", snippet))
		}
		test("document POST change internal user status") {
			every { changeInternalUserStatusUseCase.execute(any()) } returns
				ChangeInternalUserStatusResult(
					internalUserId = InternalUserId("iu_002"),
					status = AccountStatus.SUSPENDED,
					changedAt = NOW,
				)

			val snippet =
				adminResource(
					summary = "내부 운영자 계정 상태 변경(정지·재개·종료)",
					description =
						"SUPER_ADMIN만 호출할 수 있다. 마지막 경로 세그먼트로 동작을 고른다: " +
							"suspend(ACTIVE→SUSPENDED) · reactivate(SUSPENDED→ACTIVE) · terminate(→TERMINATED, 되돌릴 수 없음). " +
							"자기 자신은 대상으로 삼을 수 없고(403), 마지막 활성 SUPER_ADMIN을 정지·종료하면 409다. 상태 변경 " +
							"요청이라 CSRF 토큰이 필요하다.",
					responseSchema = "ChangeInternalUserStatusResponse",
					responseFields =
						listOf(
							fieldWithPath("internalUserId").description("대상 내부 운영자 식별자"),
							fieldWithPath("status").description("변경된 상태(ACTIVE | SUSPENDED | TERMINATED)"),
							fieldWithPath("changedAt").description("변경 시각(UTC)"),
						),
				)

			mockMvc
				.perform(post("/admin/internal-users/iu_002/suspend").with(authenticatedAs(SUPER_ADMIN)).with(csrf()))
				.andExpect(status().isOk)
				.andDo(document("admin-change-internal-user-status", snippet))
		}

		test("document POST change internal user role") {
			every { changeInternalUserRoleUseCase.execute(any()) } returns
				ChangeInternalUserRoleResult(
					internalUserId = InternalUserId("iu_002"),
					role = InternalUserRole.VIEWER,
					changedAt = NOW,
				)

			val snippet =
				adminResource(
					summary = "내부 운영자 역할 변경",
					description =
						"SUPER_ADMIN만 호출할 수 있다. role은 OPERATOR | VIEWER여야 한다 — **SUPER_ADMIN으로 승격하면 " +
							"400이다**(초대와 같은 제약, 최초 SUPER_ADMIN은 Bootstrap으로만 만든다). 마지막 활성 SUPER_ADMIN을 " +
							"강등하면 409다. 상태 변경 요청이라 CSRF 토큰이 필요하다.",
					requestSchema = "ChangeInternalUserRoleRequest",
					requestFields =
						listOf(
							fieldWithPath("role").description("변경할 역할(OPERATOR | VIEWER)"),
						),
					responseSchema = "ChangeInternalUserRoleResponse",
					responseFields =
						listOf(
							fieldWithPath("internalUserId").description("대상 내부 운영자 식별자"),
							fieldWithPath("role").description("변경된 역할"),
							fieldWithPath("changedAt").description("변경 시각(UTC)"),
						),
				)

			mockMvc
				.perform(
					post("/admin/internal-users/iu_002/role")
						.with(authenticatedAs(SUPER_ADMIN))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(ChangeInternalUserRoleRequest(role = "VIEWER"))),
				).andExpect(status().isOk)
				.andDo(document("admin-change-internal-user-role", snippet))
		}
		test("document GET merchant users") {
			every { adminListMerchantUsersUseCase.execute(any()) } returns
				ListMerchantUsersResult(
					merchantUsers =
						listOf(
							MerchantUserSummary(
								merchantUserId = MerchantUserId("mu_001"),
								loginId = LoginId("owner01"),
								email = Email("owner01@example.com"),
								userName = "오너",
								role = MerchantUserRole.OWNER,
								status = AccountStatus.ACTIVE,
								// non-null을 넣는다 — null이면 restdocs-api-spec이 타입을 추론 못 해 필드를 뺀다.
								lastLoginAt = NOW.plusSeconds(3_600),
								createdAt = NOW,
								pendingInvitationExpiresAt = NOW.plusSeconds(604_800),
							),
						),
				)

			val snippet =
				adminResource(
					summary = "가맹점 사용자 명부 조회",
					description =
						"인증된 내부 운영자 전원이 조회할 수 있다 — VIEWER도 포함된다(GET /admin/merchants와 같은 스코핑). " +
							"관리 액션(POST)만 SUPER_ADMIN/OPERATOR로 제한된다. 비밀번호 해시는 담기지 않는다.",
					responseSchema = "AdminListMerchantUsersResponse",
					responseFields =
						listOf(
							fieldWithPath("merchantUsers").description("가맹점 사용자 요약 배열(최신 생성순)"),
							fieldWithPath("merchantUsers[].merchantUserId").description("가맹점 사용자 식별자"),
							fieldWithPath("merchantUsers[].loginId").description("로그인 아이디"),
							fieldWithPath("merchantUsers[].email").description("이메일"),
							fieldWithPath("merchantUsers[].userName").description("사용자 이름"),
							fieldWithPath("merchantUsers[].role").description("OWNER | ADMIN | VIEWER"),
							fieldWithPath("merchantUsers[].status").description("INVITED | ACTIVE | LOCKED | SUSPENDED | TERMINATED"),
							fieldWithPath("merchantUsers[].lastLoginAt").description("마지막 로그인 시각(UTC). 없으면 null.").optional(),
							fieldWithPath("merchantUsers[].createdAt").description("생성(초대) 시각(UTC)"),
							fieldWithPath("merchantUsers[].pendingInvitationExpiresAt")
								.description("유효한 초대의 만료 시각(UTC). 없으면 null.")
								.optional(),
						),
				)

			mockMvc
				.perform(get("/admin/merchants/mrc_001/users").with(authenticatedAs(OPERATOR)))
				.andExpect(status().isOk)
				.andDo(document("admin-list-merchant-users", snippet))
		}

		test("document POST change merchant user status") {
			every { adminChangeMerchantUserStatusUseCase.execute(any()) } returns
				ChangeMerchantUserStatusResult(
					merchantUserId = MerchantUserId("mu_001"),
					status = AccountStatus.SUSPENDED,
					changedAt = NOW,
				)

			val snippet =
				adminResource(
					summary = "가맹점 사용자 상태 변경(정지·재개·종료)",
					description =
						"SUPER_ADMIN/OPERATOR만 호출할 수 있다. 마지막 경로 세그먼트로 동작을 고른다: suspend · reactivate · " +
							"terminate(되돌릴 수 없음). 대상이 경로의 가맹점 소속이 아니면 404. 마지막 활성 OWNER를 정지·종료하면 " +
							"409다 — 이 API가 그 불변식이 실제로 트리거되는 첫 경로다. 상태 변경이라 CSRF 토큰이 필요하다.",
					responseSchema = "ChangeMerchantUserStatusResponse",
					responseFields =
						listOf(
							fieldWithPath("merchantUserId").description("대상 가맹점 사용자 식별자"),
							fieldWithPath("status").description("변경된 상태(ACTIVE | SUSPENDED | TERMINATED)"),
							fieldWithPath("changedAt").description("변경 시각(UTC)"),
						),
				)

			mockMvc
				.perform(post("/admin/merchants/mrc_001/users/mu_001/suspend").with(authenticatedAs(OPERATOR)).with(csrf()))
				.andExpect(status().isOk)
				.andDo(document("admin-change-merchant-user-status", snippet))
		}

		test("document POST change merchant user role") {
			every { adminChangeMerchantUserRoleUseCase.execute(any()) } returns
				ChangeMerchantUserRoleResult(
					merchantUserId = MerchantUserId("mu_001"),
					role = MerchantUserRole.VIEWER,
					changedAt = NOW,
				)

			val snippet =
				adminResource(
					summary = "가맹점 사용자 역할 변경",
					description =
						"SUPER_ADMIN/OPERATOR만 호출할 수 있다. role은 ADMIN | VIEWER여야 한다 — OWNER로 승격하면 400이다" +
							"(도메인이 막는다). 마지막 활성 OWNER를 강등하면 409다. 상태 변경이라 CSRF 토큰이 필요하다.",
					requestSchema = "ChangeMerchantUserRoleRequest",
					requestFields =
						listOf(
							fieldWithPath("role").description("변경할 역할(ADMIN | VIEWER)"),
						),
					responseSchema = "ChangeMerchantUserRoleResponse",
					responseFields =
						listOf(
							fieldWithPath("merchantUserId").description("대상 가맹점 사용자 식별자"),
							fieldWithPath("role").description("변경된 역할"),
							fieldWithPath("changedAt").description("변경 시각(UTC)"),
						),
				)

			mockMvc
				.perform(
					post("/admin/merchants/mrc_001/users/mu_001/role")
						.with(authenticatedAs(OPERATOR))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(ChangeMerchantUserRoleRequest(role = "VIEWER"))),
				).andExpect(status().isOk)
				.andDo(document("admin-change-merchant-user-role", snippet))
		}
		test("document GET login audit") {
			every { listInternalLoginAuditUseCase.execute(any()) } returns
				ListInternalLoginAuditResult(
					entries =
						listOf(
							InternalLoginAuditEntry(
								auditId = InternalLoginAuditId("ila_001"),
								internalUserId = InternalUserId("iu_op01"),
								attemptedLoginId = "operator01",
								userName = "테스트 운영자",
								outcome = LoginOutcome.SUCCESS,
								clientIp = "203.0.113.7",
								occurredAt = NOW,
							),
						),
				)

			val snippet =
				adminResource(
					summary = "로그인 감사 로그 조회",
					description =
						"**SUPER_ADMIN만** 조회할 수 있다 — 실패 시도·클라이언트 IP·누가 로그인했는지가 담겨 내부 직원 " +
							"명부와 같은 민감도다. 최근 시도를 최신순으로 돌려준다(성공·실패·잠금). 없는 로그인 아이디로의 " +
							"시도는 internalUserId·userName이 null이고 attemptedLoginId만 남는다.",
					responseSchema = "ListLoginAuditResponse",
					responseFields =
						listOf(
							fieldWithPath("entries").description("로그인 감사 기록 배열(최신순)"),
							fieldWithPath("entries[].auditId").description("감사 기록 식별자"),
							fieldWithPath("entries[].internalUserId").description("시도가 가리킨 계정 식별자. 없는 계정이면 null.").optional(),
							fieldWithPath("entries[].attemptedLoginId").description("시도에 쓰인 로그인 아이디"),
							fieldWithPath("entries[].userName").description("계정 이름. 없는 계정이면 null.").optional(),
							fieldWithPath("entries[].outcome").description("SUCCESS | INVALID_CREDENTIALS | LOCKED"),
							fieldWithPath("entries[].clientIp").description("요청 원격 주소. 없으면 null.").optional(),
							fieldWithPath("entries[].occurredAt").description("시도 시각(UTC)"),
						),
				)

			mockMvc
				.perform(get("/admin/login-audit").with(authenticatedAs(SUPER_ADMIN)))
				.andExpect(status().isOk)
				.andDo(document("admin-login-audit", snippet))
		}
		test("document GET merchant login audit") {
			every { listMerchantLoginAuditUseCase.execute(any()) } returns
				ListMerchantLoginAuditResult(
					entries =
						listOf(
							MerchantLoginAuditEntry(
								auditId = MerchantLoginAuditId("mla_001"),
								merchantId = MerchantId("mrc_001"),
								merchantName = "테스트 가맹점",
								attemptedMerchantCode = "test-merchant",
								attemptedLoginId = "owner01",
								userName = "오너",
								outcome = LoginOutcome.SUCCESS,
								clientIp = "203.0.113.7",
								occurredAt = NOW,
							),
						),
				)

			val snippet =
				adminResource(
					summary = "가맹점 로그인 감사 로그 조회",
					description =
						"**SUPER_ADMIN/OPERATOR**가 조회할 수 있다 — 내부 직원 로그인 감사(SUPER_ADMIN 전용)와 달리 " +
							"OPERATOR도 가맹점 업무를 맡아 포함한다. 전 가맹점의 로그인 시도(성공·실패·잠김)를 최신순으로 " +
							"돌려준다. 기록은 api-merchant가 하고 조회는 이 콘솔이 한다. 없는 merchantCode 시도는 " +
							"merchantId·merchantName이 null이고, 없는 loginId 시도는 userName이 null이다.",
					responseSchema = "ListMerchantLoginAuditResponse",
					responseFields =
						listOf(
							fieldWithPath("entries").description("가맹점 로그인 감사 기록 배열(최신순)"),
							fieldWithPath("entries[].auditId").description("감사 기록 식별자"),
							fieldWithPath("entries[].merchantId").description("시도가 가리킨 가맹점 식별자. 없는 가맹점이면 null.").optional(),
							fieldWithPath("entries[].merchantName").description("가맹점 이름. 없는 가맹점이면 null.").optional(),
							fieldWithPath("entries[].attemptedMerchantCode").description("시도에 쓰인 가맹점 코드"),
							fieldWithPath("entries[].attemptedLoginId").description("시도에 쓰인 로그인 아이디"),
							fieldWithPath("entries[].userName").description("계정 이름. 없는 계정이면 null.").optional(),
							fieldWithPath("entries[].outcome").description("SUCCESS | INVALID_CREDENTIALS | LOCKED"),
							fieldWithPath("entries[].clientIp").description("요청 원격 주소. 없으면 null.").optional(),
							fieldWithPath("entries[].occurredAt").description("시도 시각(UTC)"),
						),
				)

			mockMvc
				.perform(get("/admin/merchant-login-audit").with(authenticatedAs(OPERATOR)))
				.andExpect(status().isOk)
				.andDo(document("admin-merchant-login-audit", snippet))
		}

		test("document GET payments") {
			every { listPaymentsUseCase.execute(any()) } returns
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
				adminResource(
					summary = "결제 내역 조회(전 가맹점)",
					description =
						"**인증된 내부 사용자 전원**(VIEWER 포함)이 조회할 수 있다. 쿼리 파라미터로 좁힌다: " +
							"merchantId, status(PaymentStatus), from/to(생성 시각 ISO-8601 UTC), page(0부터), size. " +
							"size는 서버가 최대 200으로 자르고, 실제로 적용된 값을 응답의 size로 돌려준다. " +
							"정렬은 생성 시각 최신순이다. paymentAmount는 Minor Unit 정수를 **문자열로** 준다 — " +
							"토큰 금액이 JavaScript Number의 안전 정수 범위를 넘을 수 있어서다.",
					responseSchema = "ListPaymentsResponse",
					responseFields =
						listOf(
							fieldWithPath("payments").description("결제 배열(생성 시각 최신순)"),
							fieldWithPath("payments[].paymentId").description("결제 식별자"),
							fieldWithPath("payments[].merchantId").description("가맹점 식별자"),
							fieldWithPath("payments[].merchantName").description("가맹점 이름"),
							fieldWithPath("payments[].merchantOrderId").description("가맹점이 부여한 주문 식별자"),
							fieldWithPath("payments[].orderName").description("주문명"),
							fieldWithPath("payments[].orderAmount").description("KRW 주문 금액(원 단위 정수)"),
							fieldWithPath("payments[].paymentAsset").description("결제 자산 코드(USDC)"),
							fieldWithPath("payments[].paymentAmount").description("결제 토큰 금액. Minor Unit 정수를 문자열로 준다."),
							fieldWithPath("payments[].tokenDecimals").description("토큰 소수 자릿수(USDC는 6)"),
							fieldWithPath("payments[].network").description("블록체인 네트워크 코드"),
							fieldWithPath("payments[].status").description("PaymentStatus 값"),
							fieldWithPath("payments[].failureReason").description("실패 사유. FAILED가 아니면 null.").optional(),
							fieldWithPath("payments[].transactionHash").description("온체인 거래 Hash. 고객이 제출하기 전이면 null.").optional(),
							fieldWithPath("payments[].paidAt").description("결제 완료 시각(UTC). SUCCEEDED가 아니면 null.").optional(),
							fieldWithPath("payments[].createdAt").description("결제 생성 시각(UTC)"),
							fieldWithPath("totalCount").description("필터 전체에 걸린 건수(현재 페이지 건수가 아니다)"),
							fieldWithPath("page").description("조회한 페이지 번호(0부터)"),
							fieldWithPath("size").description("실제로 적용된 페이지 크기. 상한에 걸리면 요청값과 다르다."),
						),
				)

			mockMvc
				.perform(get("/admin/payments").with(authenticatedAs(VIEWER)))
				.andExpect(status().isOk)
				.andDo(document("admin-payments", snippet))
		}
	}
}
