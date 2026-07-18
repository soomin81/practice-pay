package paytech.practice.pay.infra.persistence.jooq.identity

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUser
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")

/** `merchant_user.invited_by_internal_user_seq`의 FK 제약을 만족시키기 위해 실제 InternalUser 행을 심는다. */
private fun insertTestInternalUser(): InternalUserId {
	val id = InternalUserId("iu_${uniqueSuffix()}")
	InternalUserRepositoryAdapter(PersistenceTestSupport.dsl).save(
		InternalUser.bootstrap(
			id = id,
			loginId = LoginId("internal-${uniqueSuffix()}"),
			email = Email("${uniqueSuffix()}@example.com"),
			userName = "테스트 내부 운영자",
			passwordHash = "hashed-password",
			createdAt = NOW,
		),
	)
	return id
}

class MerchantUserRepositoryAdapterTest :
	FunSpec({
		val adapter = MerchantUserRepositoryAdapter(PersistenceTestSupport.dsl)

		test("save inserts a new MerchantUser and findByMerchantIdAndLoginId round-trips it") {
			val merchantId = MerchantId(insertTestMerchant())
			val loginId = LoginId("owner-${uniqueSuffix()}")
			val user =
				MerchantUser.inviteInitialOwner(
					id = MerchantUserId("mu_${uniqueSuffix()}"),
					merchantId = merchantId,
					loginId = loginId,
					email = Email("${uniqueSuffix()}@example.com"),
					userName = "테스트 오너",
					invitedByInternalUserId = insertTestInternalUser(),
					createdAt = NOW,
				)

			adapter.save(user)
			val found = adapter.findByMerchantIdAndLoginId(merchantId, loginId)

			found.shouldNotBeNull()
			found.id shouldBe user.id
			found.status shouldBe AccountStatus.INVITED
		}

		test("save persists an activation on an existing MerchantUser") {
			val merchantId = MerchantId(insertTestMerchant())
			val loginId = LoginId("owner-${uniqueSuffix()}")
			val user =
				MerchantUser.inviteInitialOwner(
					id = MerchantUserId("mu_${uniqueSuffix()}"),
					merchantId = merchantId,
					loginId = loginId,
					email = Email("${uniqueSuffix()}@example.com"),
					userName = "테스트 오너",
					invitedByInternalUserId = insertTestInternalUser(),
					createdAt = NOW,
				)
			adapter.save(user)

			user.activate("hashed-password", NOW.plusSeconds(1))
			adapter.save(user)

			val found = adapter.findByMerchantIdAndLoginId(merchantId, loginId)
			found.shouldNotBeNull()
			found.status shouldBe AccountStatus.ACTIVE
		}

		test("findByMerchantIdAndLoginId returns null when no such login id exists for the merchant") {
			val merchantId = MerchantId(insertTestMerchant())

			adapter.findByMerchantIdAndLoginId(merchantId, LoginId("no-such-login-id")).shouldBeNull()
		}

		test("save inserts a new MerchantUser and findById round-trips it, resolving merchantId from the row") {
			val merchantId = MerchantId(insertTestMerchant())
			val id = MerchantUserId("mu_${uniqueSuffix()}")
			val user =
				MerchantUser.inviteInitialOwner(
					id = id,
					merchantId = merchantId,
					loginId = LoginId("owner-${uniqueSuffix()}"),
					email = Email("${uniqueSuffix()}@example.com"),
					userName = "테스트 오너",
					invitedByInternalUserId = insertTestInternalUser(),
					createdAt = NOW,
				)
			adapter.save(user)

			val found = adapter.findById(id)

			found.shouldNotBeNull()
			found.id shouldBe id
			found.merchantId shouldBe merchantId
		}

		test("findById returns null when no such id exists") {
			adapter.findById(MerchantUserId("mu_no-such-id")).shouldBeNull()
		}
	})
