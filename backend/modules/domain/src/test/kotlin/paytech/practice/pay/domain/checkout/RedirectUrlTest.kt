package paytech.practice.pay.domain.checkout

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RedirectUrlTest : FunSpec({

	test("accepts an https URL") {
		RedirectUrl("https://merchant.example.com/success").value shouldBe "https://merchant.example.com/success"
	}

	test("accepts an http URL") {
		RedirectUrl("http://localhost:8081/success").value shouldBe "http://localhost:8081/success"
	}

	test("rejects a URL without a scheme") {
		shouldThrow<IllegalArgumentException> { RedirectUrl("merchant.example.com/success") }
	}

	test("rejects a value longer than 1000 characters") {
		val tooLong = "https://example.com/" + "a".repeat(1000)
		shouldThrow<IllegalArgumentException> { RedirectUrl(tooLong) }
	}
})
