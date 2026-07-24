package paytech.practice.pay.api.merchant.web

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.api.merchant.config.SecurityConfig
import paytech.practice.pay.api.merchant.security.MerchantUserPrincipal
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId

private val OWNER = MerchantUserPrincipal(MerchantUserId("mu_owner"), MerchantId("mrc_001"), LoginId("owner"), MerchantUserRole.OWNER)

private fun authenticatedAs(principal: MerchantUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

/**
 * `POST /merchant/logout`의 슬라이스 테스트다. 상태를 바꾸는 POST라 CSRF 보호 대상이므로,
 * **토큰이 있으면 204 / 없으면 403**을 확인한다(CSRF 강제 회귀 방어).
 */
@WebMvcTest(MerchantLogoutController::class)
@Import(SecurityConfig::class)
class MerchantLogoutControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	init {
		extensions(SpringExtension)

		test("authenticated logout with a CSRF token returns 204") {
			mockMvc
				.perform(post("/merchant/logout").with(authenticatedAs(OWNER)).with(csrf()))
				.andExpect(status().isNoContent)
		}

		test("logout without a CSRF token returns 403 (CSRF is enforced)") {
			mockMvc
				.perform(post("/merchant/logout").with(authenticatedAs(OWNER)))
				.andExpect(status().isForbidden)
		}
	}
}
