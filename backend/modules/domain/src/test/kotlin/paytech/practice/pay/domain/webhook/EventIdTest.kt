package paytech.practice.pay.domain.webhook

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EventIdTest :
	FunSpec({

		test("wraps a non-blank value") {
			EventId("evt_test_001").value shouldBe "evt_test_001"
		}

		test("rejects a blank value") {
			shouldThrow<IllegalArgumentException> { EventId("") }
			shouldThrow<IllegalArgumentException> { EventId("   ") }
		}

		test("rejects a value longer than 50 characters") {
			shouldThrow<IllegalArgumentException> { EventId("e".repeat(51)) }
		}

		test("accepts a value exactly 50 characters") {
			EventId("e".repeat(50)).value.length shouldBe 50
		}
	})
