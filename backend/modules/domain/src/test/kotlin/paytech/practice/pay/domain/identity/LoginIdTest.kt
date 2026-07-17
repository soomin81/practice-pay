package paytech.practice.pay.domain.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LoginIdTest :
	FunSpec({

		test("wraps a non-blank value") {
			LoginId("admin01").value shouldBe "admin01"
		}

		test("rejects a blank value") {
			shouldThrow<IllegalArgumentException> { LoginId("") }
			shouldThrow<IllegalArgumentException> { LoginId("   ") }
		}

		test("rejects a value longer than 100 characters") {
			shouldThrow<IllegalArgumentException> { LoginId("a".repeat(101)) }
		}

		test("accepts a value exactly 100 characters") {
			LoginId("a".repeat(100)).value.length shouldBe 100
		}
	})
