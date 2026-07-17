package paytech.practice.pay.domain.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class MoneyTest :
	FunSpec({

		test("wraps a non-negative KRW amount") {
			Money(1_000).amount shouldBe 1_000
		}

		test("rejects a negative amount") {
			shouldThrow<IllegalArgumentException> { Money(-1) }
		}

		test("adds two amounts") {
			(Money(1_000) + Money(500)) shouldBe Money(1_500)
		}

		test("subtracts two amounts") {
			(Money(1_000) - Money(300)) shouldBe Money(700)
		}

		test("rejects a subtraction that would go negative") {
			shouldThrow<IllegalArgumentException> { Money(100) - Money(200) }
		}

		test("compares amounts") {
			(Money(100) < Money(200)) shouldBe true
		}
	})
