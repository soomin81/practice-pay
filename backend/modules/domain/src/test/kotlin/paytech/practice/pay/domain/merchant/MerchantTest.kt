package paytech.practice.pay.domain.merchant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.shared.HttpUrl
import java.time.Instant

private val CREATED_AT: Instant = Instant.parse("2026-07-17T00:00:00Z")

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
					updatedAt = updatedAt,
				)

			merchant.status shouldBe MerchantStatus.SUSPENDED
			merchant.canAcceptPayments() shouldBe false
			merchant.updatedAt shouldBe updatedAt
		}
	})
