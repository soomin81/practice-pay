package paytech.practice.pay.domain.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MerchantUserIdTest : FunSpec({

	test("wraps a non-blank value") {
		MerchantUserId("mu_test_001").value shouldBe "mu_test_001"
	}

	test("rejects a blank value") {
		shouldThrow<IllegalArgumentException> { MerchantUserId("") }
		shouldThrow<IllegalArgumentException> { MerchantUserId("   ") }
	}

	test("rejects a value longer than 50 characters") {
		shouldThrow<IllegalArgumentException> { MerchantUserId("m".repeat(51)) }
	}

	test("accepts a value exactly 50 characters") {
		MerchantUserId("m".repeat(50)).value.length shouldBe 50
	}
})
