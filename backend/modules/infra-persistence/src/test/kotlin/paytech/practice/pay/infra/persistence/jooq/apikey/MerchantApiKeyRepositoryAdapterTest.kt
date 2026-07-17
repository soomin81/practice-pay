package paytech.practice.pay.infra.persistence.jooq.apikey

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.apikey.ApiEnvironment
import paytech.practice.pay.domain.apikey.ApiKeyPrefix
import paytech.practice.pay.domain.apikey.ApiKeyScope
import paytech.practice.pay.domain.apikey.ApiKeyStatus
import paytech.practice.pay.domain.apikey.MerchantApiKey
import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUser
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.identity.InternalUserRepositoryAdapter
import paytech.practice.pay.infra.persistence.jooq.identity.MerchantUserRepositoryAdapter
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")

/** `merchant_api_key.created_by_merchant_user_seq`의 FK 제약을 만족시키기 위해 실제 MerchantUser 행을 심는다. */
private fun insertTestMerchantUser(merchantId: MerchantId): MerchantUserId {
	val internalUserId = InternalUserId("iu_${uniqueSuffix()}")
	InternalUserRepositoryAdapter(PersistenceTestSupport.dsl).save(
		InternalUser.bootstrap(
			id = internalUserId,
			loginId = LoginId("internal-${uniqueSuffix()}"),
			email = Email("${uniqueSuffix()}@example.com"),
			userName = "테스트 내부 운영자",
			passwordHash = "hashed-password",
			createdAt = NOW,
		),
	)
	val merchantUserId = MerchantUserId("mu_${uniqueSuffix()}")
	MerchantUserRepositoryAdapter(PersistenceTestSupport.dsl).save(
		MerchantUser.inviteInitialOwner(
			id = merchantUserId,
			merchantId = merchantId,
			loginId = LoginId("owner-${uniqueSuffix()}"),
			email = Email("${uniqueSuffix()}@example.com"),
			userName = "테스트 오너",
			invitedByInternalUserId = internalUserId,
			createdAt = NOW,
		),
	)
	return merchantUserId
}

class MerchantApiKeyRepositoryAdapterTest :
	FunSpec({
		val adapter = MerchantApiKeyRepositoryAdapter(PersistenceTestSupport.dsl)

		test("save inserts a new MerchantApiKey with its scopes and findByPrefix round-trips it") {
			val merchantId = MerchantId(insertTestMerchant())
			val createdBy = insertTestMerchantUser(merchantId)
			val prefix = ApiKeyPrefix("sk_test_${uniqueSuffix().take(8)}")
			val key =
				MerchantApiKey.create(
					id = MerchantApiKeyId("mak_${uniqueSuffix()}"),
					merchantId = merchantId,
					keyName = "테스트 Key",
					environment = ApiEnvironment.TEST,
					keyPrefix = prefix,
					secretHash = "hashed-secret",
					hashAlgorithm = "HMAC-SHA256",
					scopes = setOf(ApiKeyScope.PAYMENT_CREATE, ApiKeyScope.PAYMENT_READ),
					createdByMerchantUserId = createdBy,
					expiresAt = null,
					createdAt = NOW,
				)

			adapter.save(key)
			val found = adapter.findByPrefix(prefix)

			found.shouldNotBeNull()
			found.id shouldBe key.id
			found.status shouldBe ApiKeyStatus.ACTIVE
			found.scopes shouldBe setOf(ApiKeyScope.PAYMENT_CREATE, ApiKeyScope.PAYMENT_READ)
		}

		test("save persists a revocation on an existing MerchantApiKey without touching its scopes") {
			val merchantId = MerchantId(insertTestMerchant())
			val createdBy = insertTestMerchantUser(merchantId)
			val prefix = ApiKeyPrefix("sk_test_${uniqueSuffix().take(8)}")
			val key =
				MerchantApiKey.create(
					id = MerchantApiKeyId("mak_${uniqueSuffix()}"),
					merchantId = merchantId,
					keyName = "테스트 Key",
					environment = ApiEnvironment.TEST,
					keyPrefix = prefix,
					secretHash = "hashed-secret",
					hashAlgorithm = "HMAC-SHA256",
					scopes = setOf(ApiKeyScope.PAYMENT_CREATE),
					createdByMerchantUserId = createdBy,
					expiresAt = null,
					createdAt = NOW,
				)
			adapter.save(key)

			key.revoke(createdBy, NOW.plusSeconds(1))
			adapter.save(key)

			val found = adapter.findByPrefix(prefix)
			found.shouldNotBeNull()
			found.status shouldBe ApiKeyStatus.REVOKED
			found.scopes shouldBe setOf(ApiKeyScope.PAYMENT_CREATE)
		}

		test("findByPrefix returns null for a nonexistent prefix") {
			adapter.findByPrefix(ApiKeyPrefix("sk_test_no_such_prefix")).shouldBeNull()
		}
	})
