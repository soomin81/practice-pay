package paytech.practice.pay.domain.payment

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MerchantOrderIdTest :
	FunSpec({

		test("wraps a non-blank value") {
			MerchantOrderId("order-001").value shouldBe "order-001"
		}

		test("rejects a blank value") {
			shouldThrow<IllegalArgumentException> { MerchantOrderId("") }
			shouldThrow<IllegalArgumentException> { MerchantOrderId("   ") }
		}

		test("rejects a value longer than 100 characters") {
			shouldThrow<IllegalArgumentException> { MerchantOrderId("o".repeat(101)) }
		}

		test("accepts a value exactly 100 characters") {
			MerchantOrderId("o".repeat(100)).value.length shouldBe 100
		}
	})
