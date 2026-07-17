package paytech.practice.pay.domain.blockchain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TransactionHashTest : FunSpec({

	val valid = "0x" + "a".repeat(64)

	test("accepts a well-formed transaction hash") {
		TransactionHash(valid).value shouldBe valid
	}

	test("rejects a value without the 0x prefix") {
		shouldThrow<IllegalArgumentException> { TransactionHash("a".repeat(64)) }
	}

	test("rejects a value with the wrong length") {
		shouldThrow<IllegalArgumentException> { TransactionHash("0x" + "a".repeat(63)) }
		shouldThrow<IllegalArgumentException> { TransactionHash("0x" + "a".repeat(65)) }
	}

	test("rejects non-hex characters") {
		shouldThrow<IllegalArgumentException> { TransactionHash("0x" + "g".repeat(64)) }
	}
})
