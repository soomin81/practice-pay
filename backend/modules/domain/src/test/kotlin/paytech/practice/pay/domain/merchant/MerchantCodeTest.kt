package paytech.practice.pay.domain.merchant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MerchantCodeTest : FunSpec({

	test("wraps a non-blank value") {
		MerchantCode("TEST_MERCHANT").value shouldBe "TEST_MERCHANT"
	}

	test("rejects a blank value") {
		shouldThrow<IllegalArgumentException> { MerchantCode("") }
		shouldThrow<IllegalArgumentException> { MerchantCode("   ") }
	}

	test("rejects a value longer than 50 characters") {
		shouldThrow<IllegalArgumentException> { MerchantCode("m".repeat(51)) }
	}

	test("accepts a value exactly 50 characters") {
		MerchantCode("m".repeat(50)).value.length shouldBe 50
	}
})
