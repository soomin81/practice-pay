package paytech.practice.pay.infra.persistence.jooq.apikey

import io.kotest.core.spec.style.FunSpec
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

private val NOW: Instant = Instant.parse("2026-07-19T00:00:00Z")

/** `merchant_api_key.created_by_merchant_user_seq`의 FK 제약을 만족시키기 위해 실제 MerchantUser 행을 심는다. */
private fun insertTestMerchantUserForList(merchantId: MerchantId): MerchantUserId {
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

private fun keyIn(
	merchantId: MerchantId,
	createdBy: MerchantUserId,
	createdAt: Instant,
	scopes: Set<ApiKeyScope> = setOf(ApiKeyScope.PAYMENT_CREATE, ApiKeyScope.PAYMENT_READ),
): MerchantApiKey =
	MerchantApiKey.create(
		id = MerchantApiKeyId("mak_${uniqueSuffix()}"),
		merchantId = merchantId,
		keyName = "테스트 Key",
		environment = ApiEnvironment.TEST,
		keyPrefix = ApiKeyPrefix("sk_test_${uniqueSuffix().take(8)}"),
		secretHash = "hashed-secret",
		hashAlgorithm = "HMAC-SHA256",
		scopes = scopes,
		createdByMerchantUserId = createdBy,
		expiresAt = null,
		createdAt = createdAt,
	)

class MerchantApiKeyListProjectionAdapterTest :
	FunSpec({
		val repositoryAdapter = MerchantApiKeyRepositoryAdapter(PersistenceTestSupport.dsl)
		val projectionAdapter = MerchantApiKeyListProjectionAdapter(PersistenceTestSupport.dsl)

		test("findByMerchantId returns summaries ordered by createdAt descending, with scopes") {
			val merchantId = MerchantId(insertTestMerchant())
			val createdBy = insertTestMerchantUserForList(merchantId)
			val earlier = keyIn(merchantId, createdBy, NOW, scopes = setOf(ApiKeyScope.PAYMENT_CREATE))
			val later = keyIn(merchantId, createdBy, NOW.plusSeconds(60), scopes = setOf(ApiKeyScope.PAYMENT_READ))
			repositoryAdapter.save(earlier)
			repositoryAdapter.save(later)

			val summaries = projectionAdapter.findByMerchantId(merchantId)

			summaries.map { it.merchantApiKeyId } shouldBe listOf(later.id, earlier.id)
			summaries.first { it.merchantApiKeyId == later.id }.scopes shouldBe setOf(ApiKeyScope.PAYMENT_READ)
			summaries.first { it.merchantApiKeyId == earlier.id }.scopes shouldBe setOf(ApiKeyScope.PAYMENT_CREATE)
		}

		test("findByMerchantId reflects a revoked key's status") {
			val merchantId = MerchantId(insertTestMerchant())
			val createdBy = insertTestMerchantUserForList(merchantId)
			val key = keyIn(merchantId, createdBy, NOW)
			repositoryAdapter.save(key)

			key.revoke(createdBy, NOW.plusSeconds(120))
			repositoryAdapter.save(key)

			val summary = projectionAdapter.findByMerchantId(merchantId).single { it.merchantApiKeyId == key.id }
			summary.status shouldBe ApiKeyStatus.REVOKED
			summary.revokedAt shouldBe NOW.plusSeconds(120)
		}

		test("findByMerchantId does not include another merchant's keys") {
			val merchantId = MerchantId(insertTestMerchant())
			val otherMerchantId = MerchantId(insertTestMerchant())
			val createdBy = insertTestMerchantUserForList(merchantId)
			val otherCreatedBy = insertTestMerchantUserForList(otherMerchantId)
			repositoryAdapter.save(keyIn(merchantId, createdBy, NOW))
			repositoryAdapter.save(keyIn(otherMerchantId, otherCreatedBy, NOW))

			val merchantIds = projectionAdapter.findByMerchantId(merchantId).map { }

			// merchantId로 조회한 결과가 정확히 이 가맹점 소속 Key 개수와 같아야 한다 —
			// 다른 가맹점의 Key가 섞여 들어오면 이 개수가 늘어난다.
			merchantIds.size shouldBe 1
		}

		test("findByMerchantId returns an empty list for a merchant with no keys") {
			val merchantId = MerchantId(insertTestMerchant())

			projectionAdapter.findByMerchantId(merchantId) shouldBe emptyList()
		}
	})
