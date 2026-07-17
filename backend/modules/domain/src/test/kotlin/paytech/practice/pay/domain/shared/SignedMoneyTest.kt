package paytech.practice.pay.domain.shared

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SignedMoneyTest :
	FunSpec({

		test("wraps a positive amount") {
			SignedMoney(1_000).amount shouldBe 1_000
		}

		test("wraps a negative amount") {
			SignedMoney(-1_000).amount shouldBe -1_000
		}

		test("wraps zero") {
			SignedMoney(0) shouldBe SignedMoney.ZERO
		}

		test("adds two amounts") {
			(SignedMoney(1_000) + SignedMoney(-300)) shouldBe SignedMoney(700)
		}

		test("subtracts two amounts, allowing a negative result") {
			(SignedMoney(100) - SignedMoney(200)) shouldBe SignedMoney(-100)
		}

		test("compares amounts") {
			(SignedMoney(-100) < SignedMoney(0)) shouldBe true
		}
	})
