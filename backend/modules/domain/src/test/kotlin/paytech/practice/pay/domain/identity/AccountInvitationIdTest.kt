package paytech.practice.pay.domain.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AccountInvitationIdTest : FunSpec({

	test("wraps a non-blank value") {
		AccountInvitationId("inv_test_001").value shouldBe "inv_test_001"
	}

	test("rejects a blank value") {
		shouldThrow<IllegalArgumentException> { AccountInvitationId("") }
		shouldThrow<IllegalArgumentException> { AccountInvitationId("   ") }
	}

	test("rejects a value longer than 50 characters") {
		shouldThrow<IllegalArgumentException> { AccountInvitationId("i".repeat(51)) }
	}

	test("accepts a value exactly 50 characters") {
		AccountInvitationId("i".repeat(50)).value.length shouldBe 50
	}
})
