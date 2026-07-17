package paytech.practice.pay.domain.quote

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PaymentQuoteIdTest : FunSpec({

	test("wraps a non-blank value") {
		PaymentQuoteId("pq_test_001").value shouldBe "pq_test_001"
	}

	test("rejects a blank value") {
		shouldThrow<IllegalArgumentException> { PaymentQuoteId("") }
		shouldThrow<IllegalArgumentException> { PaymentQuoteId("   ") }
	}

	test("rejects a value longer than 40 characters") {
		shouldThrow<IllegalArgumentException> { PaymentQuoteId("p".repeat(41)) }
	}

	test("accepts a value exactly 40 characters") {
		PaymentQuoteId("p".repeat(40)).value.length shouldBe 40
	}
})
