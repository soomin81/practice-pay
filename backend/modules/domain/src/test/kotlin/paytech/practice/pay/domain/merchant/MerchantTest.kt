package paytech.practice.pay.domain.merchant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.shared.HttpUrl
import java.time.Duration
import java.time.Instant

private val CREATED_AT: Instant = Instant.parse("2026-07-17T00:00:00Z")

/** 겹침 기간은 운영 정책이라 도메인이 상수로 갖지 않는다 — 테스트가 값을 정해 넘긴다. */
private val OVERLAP: Duration = Duration.ofHours(24)

private fun newMerchant(): Merchant =
	Merchant.create(
		id = MerchantId("mrc_test_001"),
		code = MerchantCode("TEST_MERCHANT"),
		name = "테스트 가맹점",
		webhookUrl = HttpUrl("https://merchant.example.com/webhooks/stablecoin"),
		createdAt = CREATED_AT,
	)

class MerchantTest :
	FunSpec({

		test("create starts ACTIVE and can accept payments") {
			val merchant = newMerchant()

			merchant.status shouldBe MerchantStatus.ACTIVE
			merchant.canAcceptPayments() shouldBe true
			merchant.updatedAt shouldBe CREATED_AT
		}

		test("create rejects a blank name") {
			shouldThrow<IllegalArgumentException> {
				Merchant.create(
					id = MerchantId("mrc_test_002"),
					code = MerchantCode("TEST_MERCHANT_2"),
					name = "   ",
					webhookUrl = null,
					createdAt = CREATED_AT,
				)
			}
		}

		test("create allows a null webhookUrl") {
			val merchant =
				Merchant.create(
					id = MerchantId("mrc_test_003"),
					code = MerchantCode("TEST_MERCHANT_3"),
					name = "테스트 가맹점",
					webhookUrl = null,
					createdAt = CREATED_AT,
				)

			merchant.webhookUrl.shouldBeNull()
		}

		test("suspend moves ACTIVE to SUSPENDED and blocks payments") {
			val merchant = newMerchant()
			val changedAt = CREATED_AT.plusSeconds(1)

			merchant.suspend(changedAt)

			merchant.status shouldBe MerchantStatus.SUSPENDED
			merchant.canAcceptPayments() shouldBe false
			merchant.updatedAt shouldBe changedAt
		}

		test("suspend fails when not ACTIVE") {
			val merchant = newMerchant()
			merchant.suspend(CREATED_AT.plusSeconds(1))

			shouldThrow<IllegalStateException> { merchant.suspend(CREATED_AT.plusSeconds(2)) }
		}

		test("reactivate moves SUSPENDED back to ACTIVE") {
			val merchant = newMerchant()
			merchant.suspend(CREATED_AT.plusSeconds(1))
			val changedAt = CREATED_AT.plusSeconds(2)

			merchant.reactivate(changedAt)

			merchant.status shouldBe MerchantStatus.ACTIVE
			merchant.canAcceptPayments() shouldBe true
		}

		test("reactivate fails when not SUSPENDED") {
			val merchant = newMerchant()

			shouldThrow<IllegalStateException> { merchant.reactivate(CREATED_AT.plusSeconds(1)) }
		}

		test("terminate moves ACTIVE or SUSPENDED to TERMINATED") {
			val fromActive = newMerchant()
			fromActive.terminate(CREATED_AT.plusSeconds(1))
			fromActive.status shouldBe MerchantStatus.TERMINATED

			val fromSuspended = newMerchant()
			fromSuspended.suspend(CREATED_AT.plusSeconds(1))
			fromSuspended.terminate(CREATED_AT.plusSeconds(2))
			fromSuspended.status shouldBe MerchantStatus.TERMINATED
		}

		test("terminate fails once already TERMINATED") {
			val merchant = newMerchant()
			merchant.terminate(CREATED_AT.plusSeconds(1))

			shouldThrow<IllegalStateException> { merchant.terminate(CREATED_AT.plusSeconds(2)) }
		}

		test("suspend and reactivate both fail once TERMINATED") {
			val merchant = newMerchant()
			merchant.terminate(CREATED_AT.plusSeconds(1))

			shouldThrow<IllegalStateException> { merchant.suspend(CREATED_AT.plusSeconds(2)) }
			shouldThrow<IllegalStateException> { merchant.reactivate(CREATED_AT.plusSeconds(2)) }
		}

		test("updateWebhookUrl replaces the URL without a status transition") {
			val merchant = newMerchant()
			val newUrl = HttpUrl("https://merchant.example.com/webhooks/v2")
			val changedAt = CREATED_AT.plusSeconds(1)

			merchant.updateWebhookUrl(newUrl, changedAt)

			merchant.webhookUrl shouldBe newUrl
			merchant.status shouldBe MerchantStatus.ACTIVE
			merchant.updatedAt shouldBe changedAt
		}

		test("updateWebhookUrl can clear the URL with null") {
			val merchant = newMerchant()

			merchant.updateWebhookUrl(null, CREATED_AT.plusSeconds(1))

			merchant.webhookUrl.shouldBeNull()
		}

		test("reconstitute restores a SUSPENDED merchant faithfully") {
			val updatedAt = CREATED_AT.plusSeconds(10)

			val merchant =
				Merchant.reconstitute(
					id = MerchantId("mrc_test_004"),
					code = MerchantCode("TEST_MERCHANT_4"),
					name = "테스트 가맹점",
					createdAt = CREATED_AT,
					status = MerchantStatus.SUSPENDED,
					webhookUrl = null,
					webhookSecretVersion = 3,
					webhookSecretRotatedAt = CREATED_AT,
					updatedAt = updatedAt,
				)

			merchant.status shouldBe MerchantStatus.SUSPENDED
			merchant.canAcceptPayments() shouldBe false
			merchant.updatedAt shouldBe updatedAt
			merchant.webhookSecretVersion shouldBe 3
		}

		test("a new merchant starts at webhook secret version 1") {
			newMerchant().webhookSecretVersion shouldBe 1
		}

		/**
		 * 세대 1에는 **직전이 없다** — 겹칠 것이 없으므로 교체한 적 없는 가맹점은 언제나
		 * 세대 하나만 유효하다.
		 */
		test("a merchant that never rotated has exactly one active secret version") {
			newMerchant().activeWebhookSecretVersions(CREATED_AT, OVERLAP) shouldBe listOf(1)
		}

		/**
		 * **이 겹침이 이 기능 전체의 목적이다.** 교체 직후에도 직전 비밀이 함께 유효해야
		 * 가맹점이 새 비밀을 자기 서버에 반영하는 동안 Webhook을 놓치지 않는다.
		 */
		test("both the new and the previous version are active during the overlap") {
			val merchant = newMerchant()
			merchant.rotateWebhookSecret(CREATED_AT)

			merchant.activeWebhookSecretVersions(CREATED_AT, OVERLAP) shouldBe listOf(2, 1)
			merchant.activeWebhookSecretVersions(CREATED_AT.plus(OVERLAP).minusSeconds(1), OVERLAP) shouldBe listOf(2, 1)
		}

		/** 겹침이 끝나면 직전 비밀은 **영영** 무효다 — 그러지 않으면 교체가 아무것도 회수하지 못한다. */
		test("the previous version stops being active once the overlap has passed") {
			val merchant = newMerchant()
			merchant.rotateWebhookSecret(CREATED_AT)

			merchant.activeWebhookSecretVersions(CREATED_AT.plus(OVERLAP), OVERLAP) shouldBe listOf(2)
			merchant.activeWebhookSecretVersions(CREATED_AT.plus(OVERLAP).plusSeconds(1), OVERLAP) shouldBe listOf(2)
		}

		/**
		 * 겹침 중에 또 교체하면 **직전은 방금 것 하나**다 — 세대를 셋 이상 살려 두면
		 * 노출된 비밀이 예상보다 오래 유효해진다.
		 */
		test("rotating twice within the overlap keeps only the latest two versions") {
			val merchant = newMerchant()
			merchant.rotateWebhookSecret(CREATED_AT)
			merchant.rotateWebhookSecret(CREATED_AT.plusSeconds(60))

			merchant.activeWebhookSecretVersions(CREATED_AT.plusSeconds(60), OVERLAP) shouldBe listOf(3, 2)
		}

		test("rotateWebhookSecret records when it happened") {
			val merchant = newMerchant()
			val rotatedAt = CREATED_AT.plusSeconds(1)

			merchant.rotateWebhookSecret(rotatedAt)

			merchant.webhookSecretRotatedAt shouldBe rotatedAt
		}

		/**
		 * 교체의 **목적**이 이 테스트다 — 세대가 바뀌면 파생 입력이 달라져 이전 비밀이
		 * 무효가 된다. 도메인은 비밀을 모르므로 여기서는 세대만 확인하고, 실제로 다른
		 * 비밀이 나오는지는 `HmacWebhookSignerTest`가 지킨다.
		 */
		test("rotateWebhookSecret advances the version without a status transition") {
			val merchant = newMerchant()
			val changedAt = CREATED_AT.plusSeconds(1)

			merchant.rotateWebhookSecret(changedAt)

			merchant.webhookSecretVersion shouldBe 2
			merchant.status shouldBe MerchantStatus.ACTIVE
			merchant.updatedAt shouldBe changedAt
		}

		test("reconstitute rejects a webhook secret version below 1") {
			shouldThrow<IllegalArgumentException> {
				Merchant.reconstitute(
					id = MerchantId("mrc_test_005"),
					code = MerchantCode("TEST_MERCHANT_5"),
					name = "테스트 가맹점",
					createdAt = CREATED_AT,
					status = MerchantStatus.ACTIVE,
					webhookUrl = null,
					webhookSecretVersion = 0,
					webhookSecretRotatedAt = null,
					updatedAt = CREATED_AT,
				)
			}
		}
	})
