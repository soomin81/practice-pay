package paytech.practice.pay.api.admin.web

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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.api.admin.config.SecurityConfig
import paytech.practice.pay.api.admin.security.InternalUserPrincipal
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId

private val OPERATOR =
	InternalUserPrincipal(InternalUserId("iu_001"), LoginId("operator01"), InternalUserRole.OPERATOR)

private fun authenticatedAs(principal: InternalUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

/**
 * 세션 복원(`GET /admin/me`)과 로그아웃(`POST /admin/logout`)의 슬라이스 테스트다
 * (`apps:api-merchant`의 같은 테스트들과 같은 구성).
 *
 * **미인증 시 401**(프론트가 "로그아웃 상태"로 해석하는 계약)과 **CSRF 토큰 없는 POST가
 * 403**인 것을 회귀로 지킨다 — 둘 다 SecurityConfig를 되돌리면 여기서 먼저 깨진다.
 */
@WebMvcTest(controllers = [AdminMeController::class, AdminLogoutController::class])
@Import(SecurityConfig::class)
class AdminSessionControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	init {
		extensions(SpringExtension)

		test("authenticated GET /admin/me returns the current identity") {
			mockMvc
				.perform(get("/admin/me").with(authenticatedAs(OPERATOR)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.internalUserId").value("iu_001"))
				.andExpect(jsonPath("$.loginId").value("operator01"))
				.andExpect(jsonPath("$.role").value("OPERATOR"))
		}

		test("unauthenticated GET /admin/me returns 401 (frontend treats it as logged out)") {
			mockMvc
				.perform(get("/admin/me"))
				.andExpect(status().isUnauthorized)
		}

		test("logout with a CSRF token returns 204") {
			mockMvc
				.perform(post("/admin/logout").with(authenticatedAs(OPERATOR)).with(csrf()))
				.andExpect(status().isNoContent)
		}

		test("logout without a CSRF token returns 403 (CSRF is enforced)") {
			mockMvc
				.perform(post("/admin/logout").with(authenticatedAs(OPERATOR)))
				.andExpect(status().isForbidden)
		}
	}
}
