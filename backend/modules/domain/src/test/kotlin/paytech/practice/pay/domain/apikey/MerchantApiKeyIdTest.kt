package paytech.practice.pay.domain.apikey

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MerchantApiKeyIdTest : FunSpec({

	test("wraps a non-blank value") {
		MerchantApiKeyId("mak_test_001").value shouldBe "mak_test_001"
	}

	test("rejects a blank value") {
		shouldThrow<IllegalArgumentException> { MerchantApiKeyId("") }
		shouldThrow<IllegalArgumentException> { MerchantApiKeyId("   ") }
	}

	test("rejects a value longer than 50 characters") {
		shouldThrow<IllegalArgumentException> { MerchantApiKeyId("k".repeat(51)) }
	}

	test("accepts a value exactly 50 characters") {
		MerchantApiKeyId("k".repeat(50)).value.length shouldBe 50
	}
})
