package paytech.practice.pay.domain.shared

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class ExchangeRateTest : FunSpec({

	test("wraps a positive rate") {
		ExchangeRate(BigDecimal("1350.500000000000")).value shouldBe BigDecimal("1350.500000000000")
	}

	test("rejects zero") {
		shouldThrow<IllegalArgumentException> { ExchangeRate(BigDecimal.ZERO) }
	}

	test("rejects a negative rate") {
		shouldThrow<IllegalArgumentException> { ExchangeRate(BigDecimal("-1")) }
	}
})
