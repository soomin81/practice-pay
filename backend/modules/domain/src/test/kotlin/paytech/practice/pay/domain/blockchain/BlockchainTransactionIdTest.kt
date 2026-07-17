package paytech.practice.pay.domain.blockchain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BlockchainTransactionIdTest : FunSpec({

	test("wraps a non-blank value") {
		BlockchainTransactionId("btx_test_001").value shouldBe "btx_test_001"
	}

	test("rejects a blank value") {
		shouldThrow<IllegalArgumentException> { BlockchainTransactionId("") }
		shouldThrow<IllegalArgumentException> { BlockchainTransactionId("   ") }
	}

	test("rejects a value longer than 50 characters") {
		shouldThrow<IllegalArgumentException> { BlockchainTransactionId("b".repeat(51)) }
	}

	test("accepts a value exactly 50 characters") {
		BlockchainTransactionId("b".repeat(50)).value.length shouldBe 50
	}
})
