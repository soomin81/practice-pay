package paytech.practice.pay.application.customer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.PaymentCustomerSearchProjection
import paytech.practice.pay.application.port.outbound.PiiBlindIndexer
import paytech.practice.pay.application.port.outbound.PiiEncryptor
import paytech.practice.pay.domain.customer.CustomerEmail
import paytech.practice.pay.domain.customer.CustomerPhone

/** 검색은 복호화를 하지 않는다 — 부르면 테스트가 깨지도록 일부러 터지는 구현을 넣는다. */
private class NeverDecryptingEncryptor : PiiEncryptor {
	override fun encrypt(plaintext: String): String = "enc($plaintext)"

	override fun decrypt(ciphertext: String): String = error("검색 경로에서 복호화를 부르면 안 된다.")
}

private fun newUseCase(projection: PaymentCustomerSearchProjection): SearchPaymentCustomersUseCase =
	SearchPaymentCustomersUseCase(
		paymentCustomerSearchProjection = projection,
		paymentCustomerCrypto = PaymentCustomerCrypto(NeverDecryptingEncryptor(), PiiBlindIndexer { "idx($it)" }),
	)

class SearchPaymentCustomersUseCaseTest :
	FunSpec({

		/**
		 * **정규화된 값으로 인덱스를 만들어야 한다** — 저장할 때와 같은 계산이 아니면
		 * 대문자로 검색한 사람이 아무것도 찾지 못한다.
		 */
		test("builds the email index from the normalized value") {
			val projection = mockk<PaymentCustomerSearchProjection>(relaxed = true)

			newUseCase(projection).execute(SearchPaymentCustomersCommand(CustomerEmail("GilDong@Example.com"), null))

			verify(exactly = 1) { projection.findByEmailIndex("idx(gildong@example.com)") }
		}

		test("builds the phone index from the normalized value") {
			val projection = mockk<PaymentCustomerSearchProjection>(relaxed = true)

			newUseCase(projection).execute(SearchPaymentCustomersCommand(null, CustomerPhone("010-1234-5678")))

			verify(exactly = 1) { projection.findByPhoneIndex("idx(01012345678)") }
		}

		/**
		 * 둘을 AND로 걸 수 있게 하면 "이 이메일과 이 번호가 같은 사람인가"를 확인할 수 있게
		 * 된다 — 그건 찾는 것이 아니라 대조하는 것이다.
		 */
		test("refuses both criteria at once") {
			val projection = mockk<PaymentCustomerSearchProjection>(relaxed = true)

			shouldThrow<IllegalArgumentException> {
				newUseCase(projection).execute(
					SearchPaymentCustomersCommand(CustomerEmail("gildong@example.com"), CustomerPhone("010-1234-5678")),
				)
			}
			verify(exactly = 0) { projection.findByEmailIndex(any()) }
			verify(exactly = 0) { projection.findByPhoneIndex(any()) }
		}

		test("refuses an empty query") {
			val projection = mockk<PaymentCustomerSearchProjection>(relaxed = true)

			shouldThrow<IllegalArgumentException> {
				newUseCase(projection).execute(SearchPaymentCustomersCommand(null, null))
			}
		}

		test("no match is an empty list, not an exception") {
			val projection = mockk<PaymentCustomerSearchProjection>()
			every { projection.findByEmailIndex(any()) } returns emptyList()

			val result = newUseCase(projection).execute(SearchPaymentCustomersCommand(CustomerEmail("nobody@example.com"), null))

			result.matches shouldBe emptyList()
		}
	})
