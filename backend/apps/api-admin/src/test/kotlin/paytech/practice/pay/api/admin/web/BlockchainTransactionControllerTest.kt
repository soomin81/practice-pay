package paytech.practice.pay.api.admin.web

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.api.admin.config.SecurityConfig
import paytech.practice.pay.api.admin.security.InternalUserPrincipal
import paytech.practice.pay.application.payment.BlockchainTransactionNotFoundException
import paytech.practice.pay.application.payment.MarkTransactionReorgedCommand
import paytech.practice.pay.application.payment.MarkTransactionReorgedResult
import paytech.practice.pay.application.payment.MarkTransactionReorgedUseCase
import paytech.practice.pay.application.payment.TransactionNotReorgeableException
import paytech.practice.pay.domain.blockchain.BlockchainTransactionId
import paytech.practice.pay.domain.blockchain.BlockchainTransactionStatus
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.payment.PaymentId

private val TX_ID = BlockchainTransactionId("btx_001")
private const val MARK_PATH = "/admin/blockchain-transactions/btx_001/mark-reorged"

private val SUPER_ADMIN = InternalUserPrincipal(InternalUserId("iu_sa01"), LoginId("super-admin"), InternalUserRole.SUPER_ADMIN)
private val OPERATOR = InternalUserPrincipal(InternalUserId("iu_op01"), LoginId("operator01"), InternalUserRole.OPERATOR)
private val VIEWER = InternalUserPrincipal(InternalUserId("iu_vw01"), LoginId("viewer01"), InternalUserRole.VIEWER)

private fun authenticatedAs(principal: InternalUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

private fun marked(settlementHeld: Boolean = true) =
	MarkTransactionReorgedResult(
		blockchainTransactionId = TX_ID,
		paymentId = PaymentId("pay_001"),
		settlementHeld = settlementHeld,
	)

@WebMvcTest(BlockchainTransactionController::class)
@Import(SecurityConfig::class)
class BlockchainTransactionControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@MockkBean
	lateinit var markTransactionReorgedUseCase: MarkTransactionReorgedUseCase

	init {
		extensions(SpringExtension)

		test("marking a transaction reorged reports that the settlement was held") {
			every { markTransactionReorgedUseCase.execute(any()) } returns marked()

			mockMvc
				.perform(post(MARK_PATH).with(authenticatedAs(SUPER_ADMIN)).with(csrf()))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.blockchainTransactionId").value("btx_001"))
				.andExpect(jsonPath("$.settlementHeld").value(true))
		}

		/**
		 * **실행자는 요청이 아니라 인증 주체에서 온다** — 본문으로 받으면 누구든 남의 이름으로
		 * 이력을 남길 수 있고, 그러면 감사 기록이 증거로서 쓸모가 없어진다.
		 */
		test("the actor recorded in the audit trail comes from the session, not the request") {
			val command = slot<MarkTransactionReorgedCommand>()
			every { markTransactionReorgedUseCase.execute(capture(command)) } returns marked()

			mockMvc
				.perform(post(MARK_PATH).with(authenticatedAs(SUPER_ADMIN)).with(csrf()))
				.andExpect(status().isOk)

			command.captured.actorInternalUserId shouldBe InternalUserId("iu_sa01")
			command.captured.blockchainTransactionId shouldBe TX_ID
		}

		/**
		 * **막지 못한 쪽이 오히려 위험하다** — 채권이 아직 없다는 뜻이고, 매도 Worker가 이
		 * 결제를 집어 채권을 만들 수 있다. 응답이 그 사실을 담아야 화면이 경고한다.
		 */
		test("reports when nothing was held because the receivable does not exist yet") {
			every { markTransactionReorgedUseCase.execute(any()) } returns marked(settlementHeld = false)

			mockMvc
				.perform(post(MARK_PATH).with(authenticatedAs(SUPER_ADMIN)).with(csrf()))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.settlementHeld").value(false))
		}

		/**
		 * **이 경로만 `SUPER_ADMIN` 전용이다.** 같은 콘솔의 Webhook 재전송은
		 * `SUPER_ADMIN`/`OPERATOR`인데, 이쪽은 되돌릴 수 없고 **가맹점에게 지급될 돈을 직접
		 * 막는다** — 잘못 누르면 가맹점이 받을 정산이 멈춘다.
		 */
		test("an OPERATOR cannot mark a transaction reorged even though it may redeliver webhooks") {
			mockMvc
				.perform(post(MARK_PATH).with(authenticatedAs(OPERATOR)).with(csrf()))
				.andExpect(status().isForbidden)

			verify(exactly = 0) { markTransactionReorgedUseCase.execute(any()) }
		}

		test("a VIEWER cannot mark a transaction reorged") {
			mockMvc
				.perform(post(MARK_PATH).with(authenticatedAs(VIEWER)).with(csrf()))
				.andExpect(status().isForbidden)

			verify(exactly = 0) { markTransactionReorgedUseCase.execute(any()) }
		}

		test("an unauthenticated request is rejected with 401") {
			mockMvc.perform(post(MARK_PATH).with(csrf())).andExpect(status().isUnauthorized)
		}

		test("a request without a CSRF token is rejected") {
			mockMvc.perform(post(MARK_PATH).with(authenticatedAs(SUPER_ADMIN))).andExpect(status().isForbidden)
		}

		test("an unknown transaction is 404") {
			every { markTransactionReorgedUseCase.execute(any()) } throws BlockchainTransactionNotFoundException(TX_ID)

			mockMvc
				.perform(post(MARK_PATH).with(authenticatedAs(SUPER_ADMIN)).with(csrf()))
				.andExpect(status().isNotFound)
		}

		/**
		 * 확정 전 거래는 `409`이고 **현재 상태를 문구에 담는다** — 자동 경로가 아직 판단
		 * 중이라는 뜻이라, 운영자가 기다려야 할지 알아야 한다.
		 */
		test("a transaction that is not confirmed is 409 and says its current status") {
			every { markTransactionReorgedUseCase.execute(any()) } throws
				TransactionNotReorgeableException(TX_ID, BlockchainTransactionStatus.CONFIRMING)

			mockMvc
				.perform(post(MARK_PATH).with(authenticatedAs(SUPER_ADMIN)).with(csrf()))
				.andExpect(status().isConflict)
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("CONFIRMING")))
		}
	}
}
