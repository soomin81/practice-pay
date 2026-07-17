package paytech.practice.pay.domain.blockchain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ContractAddressTest : FunSpec({

	val valid = "0x" + "b".repeat(40)

	test("accepts a well-formed contract address") {
		ContractAddress(valid).value shouldBe valid
	}

	test("rejects a value without the 0x prefix") {
		shouldThrow<IllegalArgumentException> { ContractAddress("b".repeat(40)) }
	}

	test("rejects a value with the wrong length") {
		shouldThrow<IllegalArgumentException> { ContractAddress("0x" + "b".repeat(39)) }
		shouldThrow<IllegalArgumentException> { ContractAddress("0x" + "b".repeat(41)) }
	}

	test("rejects non-hex characters") {
		shouldThrow<IllegalArgumentException> { ContractAddress("0x" + "z".repeat(40)) }
	}
})
