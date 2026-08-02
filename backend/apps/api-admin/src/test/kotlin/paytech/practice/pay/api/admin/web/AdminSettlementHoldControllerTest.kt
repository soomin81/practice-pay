package paytech.practice.pay.api.admin.web

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.hamcrest.Matchers.containsString
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
import paytech.practice.pay.application.port.outbound.SettlementHoldAuditEntry
import paytech.practice.pay.application.settlement.CancelSettlementReceivableResult
import paytech.practice.pay.application.settlement.CancelSettlementReceivableUseCase
import paytech.practice.pay.application.settlement.ExportSettlementReceivablesUseCase
import paytech.practice.pay.application.settlement.ListSettlementHoldHistoryResult
import paytech.practice.pay.application.settlement.ListSettlementHoldHistoryUseCase
import paytech.practice.pay.application.settlement.ListSettlementReceivablesUseCase
import paytech.practice.pay.application.settlement.ReleaseSettlementHoldCommand
import paytech.practice.pay.application.settlement.ReleaseSettlementHoldResult
import paytech.practice.pay.application.settlement.ReleaseSettlementHoldUseCase
import paytech.practice.pay.application.settlement.SettlementReceivableNotFoundException
import paytech.practice.pay.application.settlement.SettlementReceivableNotReleasableException
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.settlement.SettlementHoldAction
import paytech.practice.pay.domain.settlement.SettlementHoldAuditId
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import java.time.Instant

private val RECEIVABLE_ID = SettlementReceivableId("stl_001")
private const val RELEASE_PATH = "/admin/settlement-receivables/stl_001/release"
private const val CANCEL_PATH = "/admin/settlement-receivables/stl_001/cancel"
private const val HISTORY_PATH = "/admin/settlement-receivables/stl_001/hold-history"
private const val NOTE_BODY = """{"note":"탐지 오류로 확인되어 해제합니다."}"""

private val SUPER_ADMIN = InternalUserPrincipal(InternalUserId("iu_sa01"), LoginId("super-admin"), InternalUserRole.SUPER_ADMIN)
private val OPERATOR = InternalUserPrincipal(InternalUserId("iu_op01"), LoginId("operator01"), InternalUserRole.OPERATOR)
private val VIEWER = InternalUserPrincipal(InternalUserId("iu_vw01"), LoginId("viewer01"), InternalUserRole.VIEWER)

private fun authenticatedAs(principal: InternalUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

private fun jsonPost(
	path: String,
	principal: InternalUserPrincipal,
	body: String = NOTE_BODY,
) = post(path)
	.contentType(MediaType.APPLICATION_JSON)
	.content(body)
	.with(authenticatedAs(principal))
	.with(csrf())

@WebMvcTest(AdminSettlementReceivableController::class)
@Import(SecurityConfig::class, FixedClockConfiguration::class)
class AdminSettlementHoldControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@MockkBean
	lateinit var listSettlementReceivablesUseCase: ListSettlementReceivablesUseCase

	@MockkBean
	lateinit var exportSettlementReceivablesUseCase: ExportSettlementReceivablesUseCase

	@MockkBean
	lateinit var releaseSettlementHoldUseCase: ReleaseSettlementHoldUseCase

	@MockkBean
	lateinit var cancelSettlementReceivableUseCase: CancelSettlementReceivableUseCase

	@MockkBean
	lateinit var listSettlementHoldHistoryUseCase: ListSettlementHoldHistoryUseCase

	init {
		extensions(SpringExtension)

		/**
		 * **돌아간 상태를 응답이 알려줘야 한다** — 요청이 목표 상태를 정하지 않으므로(서버가
		 * `exchangeOrderId` 유무로 고른다) 화면은 이 값 말고는 어디로 갔는지 알 길이 없다.
		 */
		test("releasing a hold reports the status it actually returned to") {
			every { releaseSettlementHoldUseCase.execute(any()) } returns
				ReleaseSettlementHoldResult(RECEIVABLE_ID, SettlementReceivableStatus.READY)

			mockMvc
				.perform(jsonPost(RELEASE_PATH, SUPER_ADMIN))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.settlementReceivableId").value("stl_001"))
				.andExpect(jsonPath("$.status").value("READY"))
		}

		test("releasing a receivable that was never exchanged returns it to PENDING") {
			every { releaseSettlementHoldUseCase.execute(any()) } returns
				ReleaseSettlementHoldResult(RECEIVABLE_ID, SettlementReceivableStatus.PENDING)

			mockMvc
				.perform(jsonPost(RELEASE_PATH, SUPER_ADMIN))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.status").value("PENDING"))
		}

		/** 실행자를 본문으로 받으면 누구든 남의 이름으로 이력을 남길 수 있다. */
		test("the actor comes from the session and the note from the body") {
			val command = slot<ReleaseSettlementHoldCommand>()
			every { releaseSettlementHoldUseCase.execute(capture(command)) } returns
				ReleaseSettlementHoldResult(RECEIVABLE_ID, SettlementReceivableStatus.READY)

			mockMvc.perform(jsonPost(RELEASE_PATH, SUPER_ADMIN)).andExpect(status().isOk)

			command.captured.actorInternalUserId shouldBe InternalUserId("iu_sa01")
			command.captured.settlementReceivableId shouldBe RECEIVABLE_ID
			command.captured.note shouldBe "탐지 오류로 확인되어 해제합니다."
		}

		test("cancelling a receivable reports CANCELLED") {
			every { cancelSettlementReceivableUseCase.execute(any()) } returns
				CancelSettlementReceivableResult(RECEIVABLE_ID, SettlementReceivableStatus.CANCELLED)

			mockMvc
				.perform(jsonPost(CANCEL_PATH, SUPER_ADMIN))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.status").value("CANCELLED"))
		}

		/** 자동 경로가 없는 전이라 사유가 없으면 "왜 풀었나"에 아무도 답할 수 없다. */
		test("a blank note is rejected with 400") {
			every { releaseSettlementHoldUseCase.execute(any()) } throws IllegalArgumentException("해제 사유(note)는 공백일 수 없습니다.")

			mockMvc
				.perform(jsonPost(RELEASE_PATH, SUPER_ADMIN, body = """{"note":"   "}"""))
				.andExpect(status().isBadRequest)
		}

		/**
		 * **막는 쪽(`mark-reorged`)과 같은 등급이어야 한다** — 푸는 쪽만 넓히면 좁게 잡은
		 * 의미가 없어진다. `OPERATOR`가 Webhook 재전송·가맹점 계정 관리를 할 수 있는 것과 대비된다.
		 */
		test("an OPERATOR cannot release a hold") {
			mockMvc.perform(jsonPost(RELEASE_PATH, OPERATOR)).andExpect(status().isForbidden)

			verify(exactly = 0) { releaseSettlementHoldUseCase.execute(any()) }
		}

		test("an OPERATOR cannot cancel a receivable") {
			mockMvc.perform(jsonPost(CANCEL_PATH, OPERATOR)).andExpect(status().isForbidden)

			verify(exactly = 0) { cancelSettlementReceivableUseCase.execute(any()) }
		}

		test("an unauthenticated release is rejected with 401") {
			mockMvc
				.perform(post(RELEASE_PATH).contentType(MediaType.APPLICATION_JSON).content(NOTE_BODY).with(csrf()))
				.andExpect(status().isUnauthorized)
		}

		test("a release without a CSRF token is rejected") {
			mockMvc
				.perform(
					post(RELEASE_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content(NOTE_BODY)
						.with(authenticatedAs(SUPER_ADMIN)),
				).andExpect(status().isForbidden)
		}

		test("an unknown receivable is 404") {
			every { releaseSettlementHoldUseCase.execute(any()) } throws SettlementReceivableNotFoundException(RECEIVABLE_ID)

			mockMvc.perform(jsonPost(RELEASE_PATH, SUPER_ADMIN)).andExpect(status().isNotFound)
		}

		/** 이미 풀렸는지 취소됐는지에 따라 다음 행동이 달라진다 — 현재 상태를 문구에 담는다. */
		test("releasing something that is not held is 409 and says its current status") {
			every { releaseSettlementHoldUseCase.execute(any()) } throws
				SettlementReceivableNotReleasableException(RECEIVABLE_ID, SettlementReceivableStatus.CANCELLED)

			mockMvc
				.perform(jsonPost(RELEASE_PATH, SUPER_ADMIN))
				.andExpect(status().isConflict)
				.andExpect(jsonPath("$.message").value(containsString("CANCELLED")))
		}

		/**
		 * **이력을 읽는 것과 상태를 바꾸는 것은 다른 권한이다** — `VIEWER`의 조회 업무에
		 * 속하므로 `SecurityConfig`가 `POST`만 좁혔다. 이 테스트가 그 스코핑의 회귀다.
		 */
		test("a VIEWER can read the hold history even though it cannot change anything") {
			every { listSettlementHoldHistoryUseCase.execute(RECEIVABLE_ID) } returns
				ListSettlementHoldHistoryResult(
					entries =
						listOf(
							SettlementHoldAuditEntry(
								auditId = SettlementHoldAuditId("sha_001"),
								internalUserId = InternalUserId("iu_sa01"),
								internalUserName = "관리자",
								action = SettlementHoldAction.RELEASED,
								reasonCode = null,
								note = "탐지 오류로 확인되어 해제합니다.",
								occurredAt = Instant.parse("2026-08-02T00:00:00Z"),
							),
						),
				)

			mockMvc
				.perform(get(HISTORY_PATH).with(authenticatedAs(VIEWER)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.history[0].action").value("RELEASED"))
				.andExpect(jsonPath("$.history[0].internalUserName").value("관리자"))
				.andExpect(jsonPath("$.history[0].note").value("탐지 오류로 확인되어 해제합니다."))
		}

		test("a VIEWER still cannot release a hold") {
			mockMvc.perform(jsonPost(RELEASE_PATH, VIEWER)).andExpect(status().isForbidden)

			verify(exactly = 0) { releaseSettlementHoldUseCase.execute(any()) }
		}
	}
}
