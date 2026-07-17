package paytech.practice.pay.infra.persistence.jooq

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT

class TransactionManagerAdapterTest :
	FunSpec({
		val transactionManager = TransactionManagerAdapter(PersistenceTestSupport.transactionManager)

		test("rolls back every write in the block when it throws") {
			val merchantId = "mrc_${uniqueSuffix()}"

			shouldThrow<IllegalStateException> {
				transactionManager.runInTransaction {
					insertTestMerchant(merchantId)
					error("boom")
				}
			}

			PersistenceTestSupport.dsl
				.selectFrom(MERCHANT)
				.where(MERCHANT.MERCHANT_ID.eq(merchantId))
				.fetchOne()
				.shouldBeNull()
		}

		test("commits every write in the block when it succeeds") {
			val merchantId = "mrc_${uniqueSuffix()}"

			transactionManager.runInTransaction {
				insertTestMerchant(merchantId)
			}

			PersistenceTestSupport.dsl
				.selectFrom(MERCHANT)
				.where(MERCHANT.MERCHANT_ID.eq(merchantId))
				.fetchOne()
				.shouldNotBeNull()
		}
	})
