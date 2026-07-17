package paytech.practice.pay.domain.blockchain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ChainIdTest :
	FunSpec({

		test("wraps a positive chain id") {
			ChainId(84_532).value shouldBe 84_532L
		}

		test("rejects zero") {
			shouldThrow<IllegalArgumentException> { ChainId(0) }
		}

		test("rejects a negative value") {
			shouldThrow<IllegalArgumentException> { ChainId(-1) }
		}
	})
