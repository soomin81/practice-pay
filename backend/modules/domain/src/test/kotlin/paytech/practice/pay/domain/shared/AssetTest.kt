package paytech.practice.pay.domain.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AssetTest : FunSpec({

	test("wraps a non-blank code") {
		Asset("USDC").code shouldBe "USDC"
	}

	test("rejects a blank code") {
		shouldThrow<IllegalArgumentException> { Asset("") }
		shouldThrow<IllegalArgumentException> { Asset("   ") }
	}

	test("exposes a USDC constant") {
		Asset.USDC shouldBe Asset("USDC")
	}
})
