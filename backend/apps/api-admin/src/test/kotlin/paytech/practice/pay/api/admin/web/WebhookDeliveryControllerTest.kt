package paytech.practice.pay.api.admin.web

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.mockk.every
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
import paytech.practice.pay.application.webhook.RedeliverWebhookResult
import paytech.practice.pay.application.webhook.RedeliverWebhookUseCase
import paytech.practice.pay.application.webhook.WebhookDeliveryNotFoundException
import paytech.practice.pay.application.webhook.WebhookDeliveryNotRedeliverableException
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.webhook.WebhookDeliveryId
import paytech.practice.pay.domain.webhook.WebhookDeliveryStatus

private val DELIVERY_ID = WebhookDeliveryId("wh_test_001")
private const val REDELIVER_PATH = "/admin/webhook-deliveries/wh_test_001/redeliver"

private val SUPER_ADMIN = InternalUserPrincipal(InternalUserId("iu_sa01"), LoginId("super-admin"), InternalUserRole.SUPER_ADMIN)
private val OPERATOR = InternalUserPrincipal(InternalUserId("iu_op01"), LoginId("operator01"), InternalUserRole.OPERATOR)
private val VIEWER = InternalUserPrincipal(InternalUserId("iu_vw01"), LoginId("viewer01"), InternalUserRole.VIEWER)

private fun authenticatedAs(principal: InternalUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

private fun reopened() =
	RedeliverWebhookResult(
		webhookDeliveryId = DELIVERY_ID,
		status = WebhookDeliveryStatus.PENDING,
		attemptCount = 5,
	)

@WebMvcTest(WebhookDeliveryController::class)
@Import(SecurityConfig::class)
class WebhookDeliveryControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@MockkBean
	lateinit var redeliverWebhookUseCase: RedeliverWebhookUseCase

	init {
		extensions(SpringExtension)

		/**
		 * **응답이 `SUCCEEDED`가 아니라 `PENDING`이라는 것이 요점이다** — 이 요청은 보내지
		 * 않고 되돌려 놓기만 하고, 실제 발송은 발행 Worker가 한다. 화면이 이 값을 "성공"으로
		 * 읽으면 사용자에게 거짓말을 하게 된다.
		 */
		test("redelivering reports the reopened state, not a delivery success") {
			every { redeliverWebhookUseCase.execute(DELIVERY_ID) } returns reopened()

			mockMvc
				.perform(post(REDELIVER_PATH).with(authenticatedAs(SUPER_ADMIN)).with(csrf()))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.webhookDeliveryId").value("wh_test_001"))
		}

		/** 누적 시도 횟수를 그대로 돌려준다 — 재전송이 재시도 예산을 새로 주지 않는다는 사실이 드러난다. */
		test("the response carries the accumulated attempt count") {
			every { redeliverWebhookUseCase.execute(DELIVERY_ID) } returns reopened()

			mockMvc
				.perform(post(REDELIVER_PATH).with(authenticatedAs(SUPER_ADMIN)).with(csrf()))
				.andExpect(jsonPath("$.attemptCount").value(5))
		}

		test("an OPERATOR may also redeliver") {
			every { redeliverWebhookUseCase.execute(DELIVERY_ID) } returns reopened()

			mockMvc
				.perform(post(REDELIVER_PATH).with(authenticatedAs(OPERATOR)).with(csrf()))
				.andExpect(status().isOk)
		}

		/**
		 * **재전송은 상태를 바꾸는 운영 행위다** — 조회 전용 역할이 누를 일이 아니다.
		 * Use Case까지 닿지 않는 것도 함께 확인한다(정적 관문이 실제로 막는지).
		 */
		test("a VIEWER cannot redeliver") {
			mockMvc
				.perform(post(REDELIVER_PATH).with(authenticatedAs(VIEWER)).with(csrf()))
				.andExpect(status().isForbidden)

			verify(exactly = 0) { redeliverWebhookUseCase.execute(any()) }
		}

		test("an unauthenticated request is rejected with 401") {
			mockMvc.perform(post(REDELIVER_PATH).with(csrf())).andExpect(status().isUnauthorized)
		}

		test("a request without a CSRF token is rejected") {
			mockMvc
				.perform(post(REDELIVER_PATH).with(authenticatedAs(SUPER_ADMIN)))
				.andExpect(status().isForbidden)
		}

		test("an unknown delivery is 404") {
			every { redeliverWebhookUseCase.execute(DELIVERY_ID) } throws WebhookDeliveryNotFoundException(DELIVERY_ID)

			mockMvc
				.perform(post(REDELIVER_PATH).with(authenticatedAs(SUPER_ADMIN)).with(csrf()))
				.andExpect(status().isNotFound)
		}

		/**
		 * `409`인 이유: 요청 자체는 올바른데 **대상의 현재 상태가 그 동작을 허용하지 않는다**.
		 * 현재 상태를 문구에 담아 "왜 안 되는지"를 알려준다 — 모르면 같은 버튼을 계속 누른다.
		 */
		test("redelivering something that has not failed is 409 and says why") {
			every { redeliverWebhookUseCase.execute(DELIVERY_ID) } throws
				WebhookDeliveryNotRedeliverableException(DELIVERY_ID, WebhookDeliveryStatus.SUCCEEDED)

			mockMvc
				.perform(post(REDELIVER_PATH).with(authenticatedAs(SUPER_ADMIN)).with(csrf()))
				.andExpect(status().isConflict)
				.andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("SUCCEEDED")))
		}
	}
}
