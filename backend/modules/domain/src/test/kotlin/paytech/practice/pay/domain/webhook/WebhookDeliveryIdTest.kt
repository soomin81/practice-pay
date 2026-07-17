package paytech.practice.pay.domain.webhook

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WebhookDeliveryIdTest : FunSpec({

	test("wraps a non-blank value") {
		WebhookDeliveryId("wh_test_001").value shouldBe "wh_test_001"
	}

	test("rejects a blank value") {
		shouldThrow<IllegalArgumentException> { WebhookDeliveryId("") }
		shouldThrow<IllegalArgumentException> { WebhookDeliveryId("   ") }
	}

	test("rejects a value longer than 50 characters") {
		shouldThrow<IllegalArgumentException> { WebhookDeliveryId("w".repeat(51)) }
	}

	test("accepts a value exactly 50 characters") {
		WebhookDeliveryId("w".repeat(50)).value.length shouldBe 50
	}
})
