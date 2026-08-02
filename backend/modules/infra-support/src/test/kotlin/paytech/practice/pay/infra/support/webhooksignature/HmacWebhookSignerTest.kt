package paytech.practice.pay.infra.support.webhooksignature

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Instant
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val PEPPER = "test-pepper"
private val MERCHANT = MerchantId("mrc_test_001")
private val SIGNED_AT: Instant = Instant.parse("2026-08-02T00:00:00Z")

private fun signer(pepper: String = PEPPER) = HmacWebhookSigner(pepper)

class HmacWebhookSignerTest :
	FunSpec({

		test("a derived secret is stable for the same merchant and version") {
			signer().deriveSecret(MERCHANT, 1) shouldBe signer().deriveSecret(MERCHANT, 1)
		}

		test("a derived secret is prefixed so merchants can recognise it") {
			signer().deriveSecret(MERCHANT, 1) shouldStartWith "whsec_"
		}

		/**
		 * **교체가 실제로 무언가를 무효화한다**는 것이 이 테스트다. 세대만 올리고
		 * 비밀이 그대로면 노출된 비밀을 회수할 방법이 없어진다.
		 */
		test("advancing the version yields a completely different secret") {
			signer().deriveSecret(MERCHANT, 1) shouldNotBe signer().deriveSecret(MERCHANT, 2)
		}

		test("different merchants never share a secret") {
			signer().deriveSecret(MERCHANT, 1) shouldNotBe signer().deriveSecret(MerchantId("mrc_test_002"), 1)
		}

		/**
		 * Pepper가 다르면 같은 가맹점·같은 세대라도 비밀이 달라진다 — `batch`와
		 * `api-merchant`가 **같은 Pepper를 써야 하는 이유**가 이것이다. 어긋나면
		 * 콘솔이 알려준 비밀로는 어떤 서명도 검증되지 않는다.
		 */
		test("a different pepper yields a different secret for the same merchant and version") {
			signer().deriveSecret(MERCHANT, 1) shouldNotBe signer("other-pepper").deriveSecret(MERCHANT, 1)
		}

		test("deriveSecret rejects a version below 1") {
			shouldThrow<IllegalArgumentException> { signer().deriveSecret(MERCHANT, 0) }
		}

		/**
		 * 헤더 형식은 **가맹점이 코드에 적어 넣는 공개 계약**이라, 바꾸면 모든 가맹점의
		 * 검증이 깨진다 — 그래서 형식 자체를 고정한다
		 * (`docs/architecture/webhook-api.md`).
		 */
		test("the signature header carries the unix timestamp and a v1 hex signature") {
			val header = signer().signatureHeaderValue(MERCHANT, listOf(1), "{\"a\":1}", SIGNED_AT)

			header shouldStartWith "t=${SIGNED_AT.epochSecond},v1="
			// SHA-256을 HEX로 적으면 언제나 64자다 — 길이가 흔들리면 알고리즘이나
			// 인코딩이 바뀐 것이고, 그건 가맹점 쪽 검증을 깨뜨린다.
			header.substringAfter("v1=").length shouldBe 64
			header.substringAfter("v1=").all { it in "0123456789abcdef" } shouldBe true
		}

		/**
		 * **본문만이 아니라 `"{t}.{본문}"`에 서명한다** — 이걸 어기면 가로챈 요청을
		 * 그대로 다시 보내는 재전송 공격을 가맹점이 판단할 수 없다(본문만 서명하면
		 * 그 서명이 영원히 유효하다). 문서가 알려주는 검증 절차를 그대로 재현해서
		 * 값이 일치하는지 본다.
		 */
		test("the signature matches what a merchant recomputes from the documented recipe") {
			val payload = "{\"paymentId\":\"pay_001\"}"
			val header = signer().signatureHeaderValue(MERCHANT, listOf(1), payload, SIGNED_AT)

			val timestamp = header.substringAfter("t=").substringBefore(",")
			val secret = signer().deriveSecret(MERCHANT, 1)
			val mac = Mac.getInstance("HmacSHA256")
			mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
			val expected = HexFormat.of().formatHex(mac.doFinal("$timestamp.$payload".toByteArray(Charsets.UTF_8)))

			header.substringAfter("v1=") shouldBe expected
		}

		test("the same payload signed at a different time produces a different signature") {
			val a = signer().signatureHeaderValue(MERCHANT, listOf(1), "{}", SIGNED_AT)
			val b = signer().signatureHeaderValue(MERCHANT, listOf(1), "{}", SIGNED_AT.plusSeconds(1))

			a.substringAfter("v1=") shouldNotBe b.substringAfter("v1=")
		}

		/**
		 * **겹침 기간의 핵심이다** — 세대를 둘 넘기면 서명도 둘 실려야 하고, 각각 그
		 * 세대의 비밀로 검증돼야 한다. 하나만 실리면 가맹점은 새 비밀을 반영하기 전까지
		 * Webhook을 놓친다.
		 */
		test("two secret versions produce two v1 signatures that each verify with their own secret") {
			val payload = "{\"paymentId\":\"pay_001\"}"

			val header = signer().signatureHeaderValue(MERCHANT, listOf(2, 1), payload, SIGNED_AT)

			val timestamp = header.substringAfter("t=").substringBefore(",")
			val signatures = header.split(",").filter { it.startsWith("v1=") }.map { it.removePrefix("v1=") }
			signatures.size shouldBe 2
			signatures[0] shouldBe hmacHex(signer().deriveSecret(MERCHANT, 2), "$timestamp.$payload")
			signatures[1] shouldBe hmacHex(signer().deriveSecret(MERCHANT, 1), "$timestamp.$payload")
			// 서로 다른 비밀이므로 두 서명도 달라야 한다 — 같다면 세대를 안 쓴 것이다.
			signatures[0] shouldNotBe signatures[1]
		}

		/** 타임스탬프는 **한 번만** 실린다 — 서명이 둘이어도 서명 대상 시각은 하나다. */
		test("the timestamp appears once no matter how many signatures are carried") {
			val header = signer().signatureHeaderValue(MERCHANT, listOf(2, 1), "{}", SIGNED_AT)

			header.split(",").count { it.startsWith("t=") } shouldBe 1
			header shouldStartWith "t=${SIGNED_AT.epochSecond},"
		}

		/** 서명할 비밀이 하나도 없으면 호출 자체가 잘못이다 — 서명 없는 요청이 나가면 안 된다. */
		test("signing with no secret version at all is rejected") {
			shouldThrow<IllegalArgumentException> {
				signer().signatureHeaderValue(MERCHANT, emptyList(), "{}", SIGNED_AT)
			}
		}
	})

private fun hmacHex(
	secret: String,
	message: String,
): String {
	val mac = Mac.getInstance("HmacSHA256")
	mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
	return HexFormat.of().formatHex(mac.doFinal(message.toByteArray(Charsets.UTF_8)))
}
