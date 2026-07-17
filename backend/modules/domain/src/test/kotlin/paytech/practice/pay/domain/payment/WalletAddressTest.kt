package paytech.practice.pay.domain.payment

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WalletAddressTest : FunSpec({

	val valid = "0x" + "a".repeat(40)

	test("accepts a well-formed EVM address") {
		WalletAddress(valid).value shouldBe valid
	}

	test("accepts mixed-case hex digits") {
		WalletAddress("0x" + "aB3f".repeat(10)).value shouldBe ("0x" + "aB3f".repeat(10))
	}

	test("rejects a value without the 0x prefix") {
		shouldThrow<IllegalArgumentException> { WalletAddress("a".repeat(40)) }
	}

	test("rejects a value with the wrong length") {
		shouldThrow<IllegalArgumentException> { WalletAddress("0x" + "a".repeat(39)) }
		shouldThrow<IllegalArgumentException> { WalletAddress("0x" + "a".repeat(41)) }
	}

	test("rejects non-hex characters") {
		shouldThrow<IllegalArgumentException> { WalletAddress("0x" + "g".repeat(40)) }
	}
})
