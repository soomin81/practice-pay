package paytech.practice.pay.domain.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class BlockchainNetworkTest :
	FunSpec({

		test("wraps a non-blank code") {
			BlockchainNetwork("BASE_SEPOLIA").code shouldBe "BASE_SEPOLIA"
		}

		test("rejects a blank code") {
			shouldThrow<IllegalArgumentException> { BlockchainNetwork("") }
			shouldThrow<IllegalArgumentException> { BlockchainNetwork("   ") }
		}

		test("exposes a BASE_SEPOLIA constant") {
			BlockchainNetwork.BASE_SEPOLIA shouldBe BlockchainNetwork("BASE_SEPOLIA")
		}
	})
