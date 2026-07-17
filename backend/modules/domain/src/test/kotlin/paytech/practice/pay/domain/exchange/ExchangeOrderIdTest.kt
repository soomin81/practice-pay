package paytech.practice.pay.domain.exchange

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ExchangeOrderIdTest : FunSpec({

	test("wraps a non-blank value") {
		ExchangeOrderId("exo_test_001").value shouldBe "exo_test_001"
	}

	test("rejects a blank value") {
		shouldThrow<IllegalArgumentException> { ExchangeOrderId("") }
		shouldThrow<IllegalArgumentException> { ExchangeOrderId("   ") }
	}

	test("rejects a value longer than 50 characters") {
		shouldThrow<IllegalArgumentException> { ExchangeOrderId("e".repeat(51)) }
	}

	test("accepts a value exactly 50 characters") {
		ExchangeOrderId("e".repeat(50)).value.length shouldBe 50
	}
})
