package paytech.practice.pay.infra.support.pii

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.Base64

/** 32바이트 키 두 개 — 하나는 정상용, 하나는 "다른 키로는 못 읽는다"를 보이는 용도다. */
private val KEY = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
private val OTHER_KEY = Base64.getEncoder().encodeToString(ByteArray(32) { (it + 1).toByte() })

class AesGcmPiiEncryptorTest :
	FunSpec({
		val encryptor = AesGcmPiiEncryptor(KEY)

		test("암호화한 값을 그대로 되돌린다") {
			val plaintext = "홍길동"

			encryptor.decrypt(encryptor.encrypt(plaintext)) shouldBe plaintext
		}

		test("한글과 긴 값도 왕복한다") {
			val plaintext = "가나다라마바사".repeat(20)

			encryptor.decrypt(encryptor.encrypt(plaintext)) shouldBe plaintext
		}

		/**
		 * **이 테스트가 랜덤 IV를 쓰는 이유 전체다.** 같은 이메일이 같은 암호문이 되면 DB만
		 * 유출돼도 동일인 여부가 드러난다(ADR-008).
		 */
		test("같은 평문이라도 매번 다른 암호문이 나온다") {
			val plaintext = "customer@example.com"

			val first = encryptor.encrypt(plaintext)
			val second = encryptor.encrypt(plaintext)

			first shouldNotBe second
			// 그래도 둘 다 같은 평문으로 돌아온다.
			encryptor.decrypt(first) shouldBe plaintext
			encryptor.decrypt(second) shouldBe plaintext
		}

		/**
		 * **인증 암호화를 고른 이유다** — CBC/CTR이라면 변조된 암호문이 조용히 다른 평문을
		 * 내놓을 수 있고, 그게 이메일이라면 엉뚱한 사람에게 연락하게 된다.
		 */
		test("변조된 암호문은 조용히 다른 값을 내놓지 않고 실패한다") {
			val encrypted = Base64.getDecoder().decode(encryptor.encrypt("customer@example.com"))
			// 마지막 바이트 하나만 뒤집는다.
			encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1] + 1).toByte()

			shouldThrow<Exception> { encryptor.decrypt(Base64.getEncoder().encodeToString(encrypted)) }
		}

		test("다른 키로는 읽지 못한다") {
			val encrypted = encryptor.encrypt("customer@example.com")

			shouldThrow<Exception> { AesGcmPiiEncryptor(OTHER_KEY).decrypt(encrypted) }
		}

		/**
		 * 잘못된 키로 암호화가 **시작되면** 그 데이터는 되살릴 수 없다 — 늦게 실패하느니
		 * 기동 시점에 아예 뜨지 않는 편이 낫다.
		 */
		test("키 길이가 32바이트가 아니면 만들어지지 않는다") {
			val shortKey = Base64.getEncoder().encodeToString(ByteArray(16))

			shouldThrow<IllegalArgumentException> { AesGcmPiiEncryptor(shortKey) }
		}

		test("IV를 담기에도 짧은 암호문은 거부한다") {
			shouldThrow<IllegalArgumentException> {
				encryptor.decrypt(Base64.getEncoder().encodeToString(ByteArray(4)))
			}
		}
	})

class HmacPiiBlindIndexerTest :
	FunSpec({
		val indexer = HmacPiiBlindIndexer("test-pepper")

		/** 검색이 성립하려면 같은 값이 언제나 같은 인덱스여야 한다. */
		test("같은 값은 언제나 같은 인덱스를 만든다") {
			indexer.index("customer@example.com") shouldBe indexer.index("customer@example.com")
		}

		test("다른 값은 다른 인덱스를 만든다") {
			indexer.index("a@example.com") shouldNotBe indexer.index("b@example.com")
		}

		/** Pepper가 다르면 인덱스도 달라야 한다 — 그게 이 값을 비밀로 두는 이유다. */
		test("Pepper가 다르면 인덱스가 달라진다") {
			HmacPiiBlindIndexer("other-pepper").index("customer@example.com") shouldNotBe
				indexer.index("customer@example.com")
		}

		/** `payment_customer.*_index`가 `CHAR(64)`다 — 길이가 어긋나면 저장이 조용히 잘린다. */
		test("인덱스는 hex 64자다") {
			indexer.index("customer@example.com").length shouldBe 64
			indexer.index("customer@example.com") shouldBe indexer.index("customer@example.com").lowercase()
		}
	})
