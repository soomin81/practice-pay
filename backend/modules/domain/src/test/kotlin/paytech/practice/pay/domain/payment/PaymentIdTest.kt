package paytech.practice.pay.domain.payment

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PaymentIdTest :
	FunSpec({

		test("wraps a non-blank value") {
			PaymentId("pay_123").value shouldBe "pay_123"
		}

		test("rejects a blank value") {
			shouldThrow<IllegalArgumentException> { PaymentId("") }
			shouldThrow<IllegalArgumentException> { PaymentId("   ") }
		}

		test("rejects a value longer than 40 characters") {
			shouldThrow<IllegalArgumentException> { PaymentId("p".repeat(41)) }
		}

		test("accepts a value exactly 40 characters") {
			PaymentId("p".repeat(40)).value.length shouldBe 40
		}
	})
