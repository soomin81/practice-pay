package paytech.practice.pay.domain.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class TokenAmountTest : FunSpec({

	test("wraps a non-negative minor-unit amount") {
		TokenAmount(72_992_701).amountMinor shouldBe 72_992_701
	}

	test("rejects a negative amount") {
		shouldThrow<IllegalArgumentException> { TokenAmount(-1) }
	}

	test("adds two amounts") {
		(TokenAmount(1_000) + TokenAmount(500)) shouldBe TokenAmount(1_500)
	}

	test("subtracts two amounts") {
		(TokenAmount(1_000) - TokenAmount(300)) shouldBe TokenAmount(700)
	}

	test("rejects a subtraction that would go negative") {
		shouldThrow<IllegalArgumentException> { TokenAmount(100) - TokenAmount(200) }
	}

	test("compares amounts") {
		(TokenAmount(100) < TokenAmount(200)) shouldBe true
	}
})
