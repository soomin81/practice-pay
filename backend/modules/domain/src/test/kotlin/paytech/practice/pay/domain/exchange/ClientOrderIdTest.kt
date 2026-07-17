package paytech.practice.pay.domain.exchange

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ClientOrderIdTest :
	FunSpec({

		test("wraps a non-blank value") {
			ClientOrderId("client-order-001").value shouldBe "client-order-001"
		}

		test("rejects a blank value") {
			shouldThrow<IllegalArgumentException> { ClientOrderId("") }
			shouldThrow<IllegalArgumentException> { ClientOrderId("   ") }
		}

		test("rejects a value longer than 100 characters") {
			shouldThrow<IllegalArgumentException> { ClientOrderId("c".repeat(101)) }
		}

		test("accepts a value exactly 100 characters") {
			ClientOrderId("c".repeat(100)).value.length shouldBe 100
		}
	})
