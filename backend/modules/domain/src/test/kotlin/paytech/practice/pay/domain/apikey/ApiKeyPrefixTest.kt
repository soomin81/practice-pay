package paytech.practice.pay.domain.apikey

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ApiKeyPrefixTest :
	FunSpec({

		test("wraps a non-blank value") {
			ApiKeyPrefix("sk_test_ab12cd34").value shouldBe "sk_test_ab12cd34"
		}

		test("rejects a blank value") {
			shouldThrow<IllegalArgumentException> { ApiKeyPrefix("") }
			shouldThrow<IllegalArgumentException> { ApiKeyPrefix("   ") }
		}

		test("rejects a value longer than 50 characters") {
			shouldThrow<IllegalArgumentException> { ApiKeyPrefix("p".repeat(51)) }
		}

		test("accepts a value exactly 50 characters") {
			ApiKeyPrefix("p".repeat(50)).value.length shouldBe 50
		}
	})
