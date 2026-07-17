package paytech.practice.pay.domain.merchant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MerchantIdTest :
	FunSpec({

		test("wraps a non-blank value") {
			MerchantId("mrc_test_001").value shouldBe "mrc_test_001"
		}

		test("rejects a blank value") {
			shouldThrow<IllegalArgumentException> { MerchantId("") }
			shouldThrow<IllegalArgumentException> { MerchantId("   ") }
		}

		test("rejects a value longer than 40 characters") {
			shouldThrow<IllegalArgumentException> { MerchantId("m".repeat(41)) }
		}

		test("accepts a value exactly 40 characters") {
			MerchantId("m".repeat(40)).value.length shouldBe 40
		}
	})
