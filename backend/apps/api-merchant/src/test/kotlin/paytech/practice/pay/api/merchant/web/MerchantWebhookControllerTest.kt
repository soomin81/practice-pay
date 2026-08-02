package paytech.practice.pay.api.merchant.web

import com.ninjasquad.springmockk.MockkBean
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.api.merchant.config.SecurityConfig
import paytech.practice.pay.api.merchant.security.MerchantUserPrincipal
import paytech.practice.pay.application.merchant.GetMerchantWebhookSettingsUseCase
import paytech.practice.pay.application.merchant.MerchantWebhookSettings
import paytech.practice.pay.application.merchant.RotateMerchantWebhookSecretUseCase
import paytech.practice.pay.application.merchant.UpdateMerchantWebhookUrlUseCase
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId

private val MERCHANT_ID = MerchantId("mrc_001")
private val OWNER = MerchantUserPrincipal(MerchantUserId("mu_owner"), MERCHANT_ID, LoginId("owner"), MerchantUserRole.OWNER)
private val ADMIN = MerchantUserPrincipal(MerchantUserId("mu_admin"), MERCHANT_ID, LoginId("admin"), MerchantUserRole.ADMIN)
private val VIEWER = MerchantUserPrincipal(MerchantUserId("mu_viewer"), MERCHANT_ID, LoginId("viewer"), MerchantUserRole.VIEWER)

private fun authenticatedAs(principal: MerchantUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

private fun settings(
	url: String? = "https://merchant.example.com/webhooks",
	version: Int = 1,
) = MerchantWebhookSettings(webhookUrl = url, signingSecret = "whsec_dGVzdA", secretVersion = version)

@WebMvcTest(MerchantWebhookController::class)
@Import(SecurityConfig::class)
class MerchantWebhookControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@MockkBean
	lateinit var getMerchantWebhookSettingsUseCase: GetMerchantWebhookSettingsUseCase

	@MockkBean
	lateinit var updateMerchantWebhookUrlUseCase: UpdateMerchantWebhookUrlUseCase

	@MockkBean
	lateinit var rotateMerchantWebhookSecretUseCase: RotateMerchantWebhookSecretUseCase

	init {
		extensions(SpringExtension)

		test("an OWNER sees the webhook url, signing secret, and secret version") {
			every { getMerchantWebhookSettingsUseCase.execute(MERCHANT_ID) } returns settings()

			mockMvc
				.perform(get("/merchant/webhook").with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.webhookUrl").value("https://merchant.example.com/webhooks"))
				.andExpect(jsonPath("$.signingSecret").value("whsec_dGVzdA"))
				.andExpect(jsonPath("$.secretVersion").value(1))
		}

		test("an ADMIN may also read the settings") {
			every { getMerchantWebhookSettingsUseCase.execute(MERCHANT_ID) } returns settings()

			mockMvc
				.perform(get("/merchant/webhook").with(authenticatedAs(ADMIN)))
				.andExpect(status().isOk)
		}

		/**
		 * **읽기 자체가 자격증명 노출이라 GET도 막는다.** 응답에 서명 비밀이 들어 있어서,
		 * 볼 수 있는 사람은 곧 Webhook을 위조할 수 있다 — 다른 경로에서 조회를 넓게
		 * 열어 둔 것과 다른 판단이고, 이 테스트가 그 판단을 고정한다.
		 */
		test("a VIEWER cannot read the settings because the response carries the signing secret") {
			mockMvc
				.perform(get("/merchant/webhook").with(authenticatedAs(VIEWER)))
				.andExpect(status().isForbidden)

			verify(exactly = 0) { getMerchantWebhookSettingsUseCase.execute(any()) }
		}

		test("a VIEWER cannot rotate the secret") {
			mockMvc
				.perform(post("/merchant/webhook/rotate-secret").with(authenticatedAs(VIEWER)).with(csrf()))
				.andExpect(status().isForbidden)

			verify(exactly = 0) { rotateMerchantWebhookSecretUseCase.execute(any()) }
		}

		test("an unauthenticated request is rejected with 401") {
			mockMvc.perform(get("/merchant/webhook")).andExpect(status().isUnauthorized)
		}

		/**
		 * 경로나 본문으로 `merchantId`를 받지 않는다 — **인증 주체의 가맹점에만**
		 * 적용되므로 남의 설정을 건드릴 방법이 애초에 없다.
		 */
		test("every action is scoped to the authenticated merchant") {
			val readIds = mutableListOf<MerchantId>()
			every { getMerchantWebhookSettingsUseCase.execute(capture(readIds)) } returns settings()

			mockMvc.perform(get("/merchant/webhook").with(authenticatedAs(OWNER))).andExpect(status().isOk)

			readIds.single() shouldBe MERCHANT_ID
		}

		test("updating the url passes the authenticated merchant and the new url") {
			val merchantId = slot<MerchantId>()
			val url = slot<String?>()
			every { updateMerchantWebhookUrlUseCase.execute(capture(merchantId), captureNullable(url)) } returns Unit
			every { getMerchantWebhookSettingsUseCase.execute(MERCHANT_ID) } returns settings()

			mockMvc
				.perform(
					put("/merchant/webhook")
						.with(authenticatedAs(OWNER))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""{"webhookUrl":"https://merchant.example.com/hooks"}"""),
				).andExpect(status().isOk)

			merchantId.captured shouldBe MERCHANT_ID
			url.captured shouldBe "https://merchant.example.com/hooks"
		}

		/**
		 * 빈 문자열은 "해제"로 다룬다 — 화면에서 입력란을 비우는 것이 곧 해제이고,
		 * 그것이 `HttpUrl` 검증에 걸려 400이 나면 해제할 방법이 사라진다.
		 */
		test("an empty url clears the setting instead of failing validation") {
			val url = slot<String?>()
			every { updateMerchantWebhookUrlUseCase.execute(MERCHANT_ID, captureNullable(url)) } returns Unit
			every { getMerchantWebhookSettingsUseCase.execute(MERCHANT_ID) } returns settings(url = null)

			mockMvc
				.perform(
					put("/merchant/webhook")
						.with(authenticatedAs(OWNER))
						.with(csrf())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""{"webhookUrl":""}"""),
				).andExpect(status().isOk)
				.andExpect(jsonPath("$.webhookUrl").doesNotExist())

			url.captured.shouldBeNull()
		}

		test("rotating the secret returns the new secret version straight away") {
			every { rotateMerchantWebhookSecretUseCase.execute(MERCHANT_ID) } returns settings(version = 2)

			mockMvc
				.perform(post("/merchant/webhook/rotate-secret").with(authenticatedAs(OWNER)).with(csrf()))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.secretVersion").value(2))
		}

		test("a request without a CSRF token cannot rotate the secret") {
			mockMvc
				.perform(post("/merchant/webhook/rotate-secret").with(authenticatedAs(OWNER)))
				.andExpect(status().isForbidden)
		}
	}
}
