package paytech.practice.pay.domain.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class InternalUserIdTest : FunSpec({

	test("wraps a non-blank value") {
		InternalUserId("iu_test_001").value shouldBe "iu_test_001"
	}

	test("rejects a blank value") {
		shouldThrow<IllegalArgumentException> { InternalUserId("") }
		shouldThrow<IllegalArgumentException> { InternalUserId("   ") }
	}

	test("rejects a value longer than 50 characters") {
		shouldThrow<IllegalArgumentException> { InternalUserId("i".repeat(51)) }
	}

	test("accepts a value exactly 50 characters") {
		InternalUserId("i".repeat(50)).value.length shouldBe 50
	}
})
