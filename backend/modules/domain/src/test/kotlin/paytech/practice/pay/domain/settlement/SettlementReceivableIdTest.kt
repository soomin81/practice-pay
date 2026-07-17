package paytech.practice.pay.domain.settlement

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class SettlementReceivableIdTest :
	FunSpec({

		test("wraps a non-blank value") {
			SettlementReceivableId("stl_test_001").value shouldBe "stl_test_001"
		}

		test("rejects a blank value") {
			shouldThrow<IllegalArgumentException> { SettlementReceivableId("") }
			shouldThrow<IllegalArgumentException> { SettlementReceivableId("   ") }
		}

		test("rejects a value longer than 50 characters") {
			shouldThrow<IllegalArgumentException> { SettlementReceivableId("s".repeat(51)) }
		}

		test("accepts a value exactly 50 characters") {
			SettlementReceivableId("s".repeat(50)).value.length shouldBe 50
		}
	})
