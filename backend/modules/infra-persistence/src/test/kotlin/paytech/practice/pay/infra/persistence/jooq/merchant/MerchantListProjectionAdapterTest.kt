package paytech.practice.pay.infra.persistence.jooq.merchant

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.merchant.MerchantStatus
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.LocalDateTime

class MerchantListProjectionAdapterTest :
	FunSpec({
		val adapter = MerchantListProjectionAdapter(PersistenceTestSupport.dsl)

		test("findAll includes every inserted merchant as a summary") {
			val merchantCode = "code-${uniqueSuffix()}"
			val merchantId = MerchantId(insertTestMerchant(merchantCode = merchantCode))

			val found = adapter.findAll().find { it.merchantId == merchantId }

			found?.merchantCode?.value shouldBe merchantCode
			found?.status shouldBe MerchantStatus.ACTIVE
		}

		// 다른 테스트가 같은 공유 Testcontainers DB에 얼마든지 다른 가맹점을 심겨 있을 수 있어서
		// (PersistenceTestSupport는 테스트 JVM 전체가 공유한다), 목록 전체 크기나 순서를
		// 단정하지 않는다 — 이 두 행이 서로에 대해 상대적으로 올바른 순서인지만 확인한다.
		test("findAll orders by createdAt descending") {
			val earlierId = MerchantId(insertTestMerchant(createdAt = LocalDateTime.parse("2020-01-01T00:00:00")))
			val laterId = MerchantId(insertTestMerchant(createdAt = LocalDateTime.parse("2020-01-02T00:00:00")))

			val ids = adapter.findAll().map { it.merchantId }
			val laterIndex = ids.indexOf(laterId)
			val earlierIndex = ids.indexOf(earlierId)

			(laterIndex < earlierIndex) shouldBe true
		}
	})
