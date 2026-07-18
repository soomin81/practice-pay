package paytech.practice.pay.infra.persistence.jooq.merchant

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.merchant.Merchant
import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.merchant.MerchantStatus
import paytech.practice.pay.domain.shared.HttpUrl
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant

class MerchantRepositoryAdapterTest :
	FunSpec({
		val adapter = MerchantRepositoryAdapter(PersistenceTestSupport.dsl)

		test("save inserts a new Merchant and findById round-trips it") {
			val merchantId = MerchantId("mrc_${uniqueSuffix()}")
			val merchant =
				Merchant.create(
					id = merchantId,
					code = MerchantCode("code-${uniqueSuffix()}"),
					name = "새 가맹점",
					webhookUrl = HttpUrl("https://merchant.example.com/webhook"),
					createdAt = Instant.parse("2026-07-19T00:00:00Z"),
				)

			adapter.save(merchant)

			val found = adapter.findById(merchantId)
			found.shouldNotBeNull()
			found.code shouldBe merchant.code
			found.name shouldBe merchant.name
			found.status shouldBe MerchantStatus.ACTIVE
			found.webhookUrl shouldBe merchant.webhookUrl
		}

		test("save updates an existing Merchant's mutable fields") {
			val merchantId = MerchantId(insertTestMerchant())
			val merchant = adapter.findById(merchantId)!!

			merchant.suspend(Instant.parse("2026-07-19T01:00:00Z"))
			adapter.save(merchant)

			val found = adapter.findById(merchantId)
			found.shouldNotBeNull()
			found.status shouldBe MerchantStatus.SUSPENDED
		}

		test("findById returns a reconstituted Merchant for an existing row") {
			val merchantId = insertTestMerchant()

			val merchant = adapter.findById(MerchantId(merchantId))

			merchant.shouldNotBeNull()
			merchant.id shouldBe MerchantId(merchantId)
			merchant.status shouldBe MerchantStatus.ACTIVE
			merchant.canAcceptPayments() shouldBe true
		}

		test("findById returns null for a nonexistent merchant") {
			adapter.findById(MerchantId("mrc_does_not_exist")).shouldBeNull()
		}

		test("findByCode returns a reconstituted Merchant for an existing row") {
			val merchantCode = "code-${uniqueSuffix()}"
			val merchantId = insertTestMerchant(merchantCode = merchantCode)

			val merchant = adapter.findByCode(MerchantCode(merchantCode))

			merchant.shouldNotBeNull()
			merchant.id shouldBe MerchantId(merchantId)
		}

		test("findByCode returns null for a nonexistent merchant code") {
			adapter.findByCode(MerchantCode("no-such-code")).shouldBeNull()
		}
	})
