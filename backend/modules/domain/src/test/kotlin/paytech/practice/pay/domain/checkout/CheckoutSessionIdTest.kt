package paytech.practice.pay.domain.checkout

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CheckoutSessionIdTest :
	FunSpec({

		test("wraps a non-blank value") {
			CheckoutSessionId("cs_test_001").value shouldBe "cs_test_001"
		}

		test("rejects a blank value") {
			shouldThrow<IllegalArgumentException> { CheckoutSessionId("") }
			shouldThrow<IllegalArgumentException> { CheckoutSessionId("   ") }
		}

		test("rejects a value longer than 50 characters") {
			shouldThrow<IllegalArgumentException> { CheckoutSessionId("c".repeat(51)) }
		}

		test("accepts a value exactly 50 characters") {
			CheckoutSessionId("c".repeat(50)).value.length shouldBe 50
		}
	})
