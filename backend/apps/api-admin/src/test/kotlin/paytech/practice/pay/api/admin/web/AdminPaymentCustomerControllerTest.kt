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
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import paytech.practice.pay.api.admin.config.SecurityConfig
import paytech.practice.pay.api.admin.security.InternalUserPrincipal
import paytech.practice.pay.application.customer.PaymentCustomerNotFoundException
import paytech.practice.pay.application.customer.RevealPaymentCustomerCommand
import paytech.practice.pay.application.customer.RevealPaymentCustomerResult
import paytech.practice.pay.application.customer.RevealPaymentCustomerUseCase
import paytech.practice.pay.application.customer.SearchPaymentCustomersCommand
import paytech.practice.pay.application.customer.SearchPaymentCustomersResult
import paytech.practice.pay.application.customer.SearchPaymentCustomersUseCase
import paytech.practice.pay.application.port.outbound.PaymentCustomerSearchEntry
import paytech.practice.pay.domain.customer.CustomerEmail
import paytech.practice.pay.domain.customer.CustomerName
import paytech.practice.pay.domain.customer.CustomerPhone
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Money
import java.time.Instant

private const val SEARCH_PATH = "/admin/payment-customers"
private const val REVEAL_PATH = "/admin/payment-customers/pay_001/reveal"
private const val REASON_BODY = """{"reason":"결제 실패 문의 대응"}"""
private val NOW: Instant = Instant.parse("2026-08-20T00:00:00Z")

private val SUPER_ADMIN = InternalUserPrincipal(InternalUserId("iu_sa01"), LoginId("super-admin"), InternalUserRole.SUPER_ADMIN)
private val OPERATOR = InternalUserPrincipal(InternalUserId("iu_op01"), LoginId("operator01"), InternalUserRole.OPERATOR)
private val VIEWER = InternalUserPrincipal(InternalUserId("iu_vw01"), LoginId("viewer01"), InternalUserRole.VIEWER)

private fun authenticatedAs(principal: InternalUserPrincipal) =
	authentication(
		UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_${principal.role.name}"))),
	)

private fun searchEntry() =
	PaymentCustomerSearchEntry(
		paymentId = PaymentId("pay_001"),
		merchantId = MerchantId("mrc_001"),
		merchantName = "테스트 가맹점",
		merchantOrderId = MerchantOrderId("order-1001"),
		orderName = "테스트 상품",
		orderAmount = Money(20_000),
		status = PaymentStatus.SUCCEEDED,
		nameMasked = "홍*동",
		emailMasked = "gi***@example.com",
		phoneMasked = "010-****-5678",
		paidAt = NOW,
		createdAt = NOW,
	)

/**
 * 구매자 정보 검색·원본 열람의 **인가 경계와 응답 형태**를 고정한다.
 *
 * 이 컨트롤러에서 가장 위험한 회귀는 기능이 안 되는 것이 아니라 **권한이 넓어지는 것**이다
 * (ADR-008의 6). 그래서 역할별 케이스를 셋 다 박아 두고, 검색 응답에 원문이 섞이지 않는지도
 * 함께 확인한다.
 */
@WebMvcTest(AdminPaymentCustomerController::class)
@Import(SecurityConfig::class)
class AdminPaymentCustomerControllerTest : FunSpec() {
	@Autowired
	lateinit var mockMvc: MockMvc

	@MockkBean
	lateinit var searchPaymentCustomersUseCase: SearchPaymentCustomersUseCase

	@MockkBean
	lateinit var revealPaymentCustomerUseCase: RevealPaymentCustomerUseCase

	init {
		extensions(SpringExtension)

		test("search returns masked values for an OPERATOR") {
			every { searchPaymentCustomersUseCase.execute(any()) } returns
				SearchPaymentCustomersResult(matches = listOf(searchEntry()))

			mockMvc
				.perform(get(SEARCH_PATH).param("email", "gildong@example.com").with(authenticatedAs(OPERATOR)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.matches[0].paymentId").value("pay_001"))
				.andExpect(jsonPath("$.matches[0].emailMasked").value("gi***@example.com"))
				.andExpect(jsonPath("$.matches[0].orderAmount").value(20000))
		}

		/** 검색이 복호화 경로를 타지 않는다는 약속이 응답에서도 보여야 한다. */
		test("search response never carries the plaintext that was searched for") {
			every { searchPaymentCustomersUseCase.execute(any()) } returns
				SearchPaymentCustomersResult(matches = listOf(searchEntry()))

			mockMvc
				.perform(get(SEARCH_PATH).param("email", "gildong@example.com").with(authenticatedAs(SUPER_ADMIN)))
				.andExpect(status().isOk)
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("gildong@example.com"))))
		}

		test("search passes the normalized-capable value object through to the use case") {
			val command = slot<SearchPaymentCustomersCommand>()
			every { searchPaymentCustomersUseCase.execute(capture(command)) } returns SearchPaymentCustomersResult(emptyList())

			mockMvc
				.perform(get(SEARCH_PATH).param("phone", "010-1234-5678").with(authenticatedAs(OPERATOR)))
				.andExpect(status().isOk)

			command.captured.phone shouldBe CustomerPhone("010-1234-5678")
			command.captured.email shouldBe null
		}

		test("an empty result is 200 with an empty array, not 404") {
			every { searchPaymentCustomersUseCase.execute(any()) } returns SearchPaymentCustomersResult(emptyList())

			mockMvc
				.perform(get(SEARCH_PATH).param("email", "nobody@example.com").with(authenticatedAs(OPERATOR)))
				.andExpect(status().isOk)
				.andExpect(jsonPath("$.matches").isEmpty)
		}

		/** 형식 검증은 도메인 VO가 한다 — 컨트롤러는 그 예외를 400으로 흘려보낸다. */
		test("a malformed email returns 400") {
			mockMvc
				.perform(get(SEARCH_PATH).param("email", "not-an-email").with(authenticatedAs(OPERATOR)))
				.andExpect(status().isBadRequest)
		}

		test("VIEWER cannot search") {
			mockMvc
				.perform(get(SEARCH_PATH).param("email", "gildong@example.com").with(authenticatedAs(VIEWER)))
				.andExpect(status().isForbidden)
		}

		test("search requires authentication") {
			mockMvc
				.perform(get(SEARCH_PATH).param("email", "gildong@example.com"))
				.andExpect(status().isUnauthorized)
		}

		test("reveal returns the plaintext for a SUPER_ADMIN") {
			every { revealPaymentCustomerUseCase.execute(any()) } returns
				RevealPaymentCustomerResult(
					paymentId = PaymentId("pay_001"),
					name = CustomerName("홍길동"),
					email = CustomerEmail("gildong@example.com"),
					phone = CustomerPhone("010-1234-5678"),
					revealedAt = NOW,
				)

			mockMvc
				.perform(
					post(REVEAL_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content(REASON_BODY)
						.with(authenticatedAs(SUPER_ADMIN))
						.with(csrf()),
				).andExpect(status().isOk)
				.andExpect(jsonPath("$.email").value("gildong@example.com"))
				.andExpect(jsonPath("$.revealedAt").exists())
		}

		/** 감사 기록이 자기 신고가 되지 않도록, 실행 주체는 본문이 아니라 인증에서 온다. */
		test("the actor and reason come from the principal and body, not from a client-supplied id") {
			val command = slot<RevealPaymentCustomerCommand>()
			every { revealPaymentCustomerUseCase.execute(capture(command)) } returns
				RevealPaymentCustomerResult(
					paymentId = PaymentId("pay_001"),
					name = CustomerName("홍길동"),
					email = CustomerEmail("gildong@example.com"),
					phone = CustomerPhone("010-1234-5678"),
					revealedAt = NOW,
				)

			mockMvc
				.perform(
					post(REVEAL_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""{"reason":"결제 실패 문의 대응","actorInternalUserId":"iu_someone_else"}""")
						.with(authenticatedAs(SUPER_ADMIN))
						.with(csrf()),
				).andExpect(status().isOk)

			command.captured.actorInternalUserId shouldBe InternalUserId("iu_sa01")
			command.captured.reason shouldBe "결제 실패 문의 대응"
			command.captured.clientIp shouldBe "127.0.0.1"
		}

		test("a blank reason returns 400") {
			mockMvc
				.perform(
					post(REVEAL_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""{"reason":"  "}""")
						.with(authenticatedAs(SUPER_ADMIN))
						.with(csrf()),
				).andExpect(status().isBadRequest)

			verify(exactly = 0) { revealPaymentCustomerUseCase.execute(any()) }
		}

		test("a missing customer is 404") {
			every { revealPaymentCustomerUseCase.execute(any()) } throws PaymentCustomerNotFoundException(PaymentId("pay_001"))

			mockMvc
				.perform(
					post(REVEAL_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content(REASON_BODY)
						.with(authenticatedAs(SUPER_ADMIN))
						.with(csrf()),
				).andExpect(status().isNotFound)
		}

		/**
		 * **가장 중요한 회귀다.** OPERATOR는 검색까지만 할 수 있다 — 원본 열람까지 넓어지면
		 * "SUPER_ADMIN만 원문을 본다"는 ADR-008의 결정이 무너진다.
		 */
		test("OPERATOR can search but cannot reveal") {
			mockMvc
				.perform(
					post(REVEAL_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content(REASON_BODY)
						.with(authenticatedAs(OPERATOR))
						.with(csrf()),
				).andExpect(status().isForbidden)

			verify(exactly = 0) { revealPaymentCustomerUseCase.execute(any()) }
		}

		test("reveal requires CSRF") {
			mockMvc
				.perform(
					post(REVEAL_PATH)
						.contentType(MediaType.APPLICATION_JSON)
						.content(REASON_BODY)
						.with(authenticatedAs(SUPER_ADMIN)),
				).andExpect(status().isForbidden)
		}
	}
}
