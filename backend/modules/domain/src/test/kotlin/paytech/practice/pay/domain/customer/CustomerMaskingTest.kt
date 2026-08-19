package paytech.practice.pay.domain.customer

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

/**
 * **마스킹은 조용히 틀리는 종류의 규칙이다** — 한 글자 덜 가려도 화면은 멀쩡해 보이고,
 * 그 화면을 본 사람은 가려졌다고 믿는다. 그래서 경계값을 전부 고정한다.
 */
class CustomerMaskingTest :
	FunSpec({

		test("masks the middle of a name") {
			CustomerName("홍길동").masked shouldBe "홍*동"
			CustomerName("남궁길동").masked shouldBe "남**동"
			CustomerName("Alexander").masked shouldBe "A*******r"
		}

		/** 두 글자는 가운데가 없다 — 뒷글자를 가린다. */
		test("masks the second character of a two-character name") {
			CustomerName("홍길").masked shouldBe "홍*"
		}

		/**
		 * 한 글자를 통째로 `*`로 만들면 **이름이 있었다는 사실까지 사라져** 값이 비어 있는
		 * 것과 구분되지 않는다.
		 */
		test("leaves a single-character name as it is") {
			CustomerName("김").masked shouldBe "김"
		}

		test("keeps two characters of the local part and the whole domain") {
			CustomerEmail("abcdef@example.com").masked shouldBe "ab***@example.com"
		}

		/** 두 글자를 다 남기면 가린 것이 없다. */
		test("keeps only one character when the local part is short") {
			CustomerEmail("ab@x.com").masked shouldBe "a***@x.com"
			CustomerEmail("a@x.com").masked shouldBe "a***@x.com"
		}

		test("masks the middle block of a phone number and keeps the last four digits") {
			CustomerPhone("010-1234-5678").masked shouldBe "010-****-5678"
			// 하이픈 없이 입력해도 같은 결과여야 한다 — 마스킹이 입력 형식에 좌우되면 안 된다.
			CustomerPhone("01012345678").masked shouldBe "010-****-5678"
		}

		/**
		 * **가린 자리에 원래 값이 남아 있으면 마스킹이 아니다.** 규칙이 바뀌어도 이것만은
		 * 유지돼야 해서 형식이 아니라 사실로 확인한다.
		 */
		test("a masked value never contains the part it hides") {
			CustomerName("홍길동").masked shouldNotContain "길"
			CustomerEmail("abcdef@example.com").masked shouldNotContain "cdef"
			CustomerPhone("010-1234-5678").masked shouldNotContain "1234"
		}

		/**
		 * 정규화는 **Blind Index가 같은 값을 같은 것으로 보게** 하는 장치다 — 이게 없으면
		 * 대소문자나 하이픈 차이만으로 검색에 걸리지 않는다.
		 */
		test("email normalization lowercases and trims") {
			CustomerEmail(" Abc@Example.COM ".trim()).normalized shouldBe "abc@example.com"
		}

		test("phone normalization strips hyphens") {
			CustomerPhone("010-1234-5678").normalized shouldBe "01012345678"
			CustomerPhone("01012345678").normalized shouldBe "01012345678"
		}

		test("rejects malformed values") {
			shouldThrow<IllegalArgumentException> { CustomerEmail("not-an-email") }
			shouldThrow<IllegalArgumentException> { CustomerEmail("a@b") }
			shouldThrow<IllegalArgumentException> { CustomerPhone("02-1234-5678") }
			shouldThrow<IllegalArgumentException> { CustomerPhone("010-12-34") }
			shouldThrow<IllegalArgumentException> { CustomerName("   ") }
		}

		/** 옛 번호(`011`/`016`~`019`)가 아직 남아 있다. */
		test("accepts legacy mobile prefixes other than 010") {
			CustomerPhone("011-234-5678").masked shouldBe "011-****-5678"
		}
	})
