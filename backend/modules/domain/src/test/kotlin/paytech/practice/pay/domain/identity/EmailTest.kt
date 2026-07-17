package paytech.practice.pay.domain.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EmailTest :
	FunSpec({

		test("accepts a value containing @") {
			Email("admin@example.com").value shouldBe "admin@example.com"
		}

		test("rejects a value without @") {
			shouldThrow<IllegalArgumentException> { Email("admin-example.com") }
		}

		test("rejects a blank value") {
			shouldThrow<IllegalArgumentException> { Email("") }
		}

		test("rejects a value longer than 320 characters") {
			val tooLong = "a".repeat(310) + "@example.com"
			shouldThrow<IllegalArgumentException> { Email(tooLong) }
		}
	})
