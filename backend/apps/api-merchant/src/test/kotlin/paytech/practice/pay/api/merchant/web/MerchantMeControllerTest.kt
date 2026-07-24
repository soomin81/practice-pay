package paytech.practice.pay.api.merchant.web

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
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
 * `GET /merchant/me`(세션 복원)의 슬라이스 테스트다. `SecurityConfig`를 Import해서
 * **미인증 시 401**(프론트가 "로그아웃 상태"로 해석하는 계약)까지 검증한다 —
 * 기본 엔트리포인트의 403이 아니라 401이 나와야 한다.
 */
@WebMvcTest(MerchantMeController::class)
@Import(SecurityConfig::class)
class MerchantMeControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	init {
		extensions(SpringExtension)

		test("authenticated GET returns 200 with the current identity") {
			mockMvc
				.perform(get("/merchant/me").with(authenticatedAs(OWNER)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.merchantUserId").value("mu_owner"))
				.andExpect(jsonPath("$.merchantId").value("mrc_001"))
				.andExpect(jsonPath("$.loginId").value("owner"))
				.andExpect(jsonPath("$.role").value("OWNER"))
		}

		test("unauthenticated GET returns 401 (frontend treats it as logged out)") {
			mockMvc
				.perform(get("/merchant/me"))
				.andExpect(status().isUnauthorized)
		}
	}
}
