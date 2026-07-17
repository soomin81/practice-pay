package paytech.practice.pay.infra.persistence.jooq.merchant

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.merchant.MerchantStatus
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant

class MerchantRepositoryAdapterTest :
	FunSpec({
		val adapter = MerchantRepositoryAdapter(PersistenceTestSupport.dsl)

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
	})
