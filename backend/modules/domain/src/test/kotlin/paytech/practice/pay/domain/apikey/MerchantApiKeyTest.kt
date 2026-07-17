package paytech.practice.pay.domain.apikey

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.merchant.MerchantId

private val CREATED_AT: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val CREATOR = MerchantUserId("mu_test_001")

private fun newApiKey(): MerchantApiKey = MerchantApiKey.create(
	id = MerchantApiKeyId("mak_test_001"),
	merchantId = MerchantId("mrc_test_001"),
	keyName = "개발 서버",
	environment = ApiEnvironment.TEST,
	keyPrefix = ApiKeyPrefix("sk_test_ab12cd34"),
	secretHash = "hashed-secret",
	hashAlgorithm = "HMAC-SHA-256",
	scopes = setOf(ApiKeyScope.PAYMENT_CREATE, ApiKeyScope.PAYMENT_READ),
	createdByMerchantUserId = CREATOR,
	expiresAt = null,
	createdAt = CREATED_AT,
)

class MerchantApiKeyTest : FunSpec({

	test("create starts ACTIVE and usable") {
		val apiKey = newApiKey()

		apiKey.status shouldBe ApiKeyStatus.ACTIVE
		apiKey.isUsable() shouldBe true
		apiKey.revokedAt.shouldBeNull()
	}

	test("hasScope reflects the granted scopes only") {
		val apiKey = newApiKey()

		apiKey.hasScope(ApiKeyScope.PAYMENT_CREATE) shouldBe true
		apiKey.hasScope(ApiKeyScope.REFUND_CREATE) shouldBe false
	}

	test("create rejects a blank keyName") {
		shouldThrow<IllegalArgumentException> {
			MerchantApiKey.create(
				id = MerchantApiKeyId("mak_test_002"),
				merchantId = MerchantId("mrc_test_001"),
				keyName = "   ",
				environment = ApiEnvironment.TEST,
				keyPrefix = ApiKeyPrefix("sk_test_xyz"),
				secretHash = "hashed-secret",
				hashAlgorithm = "HMAC-SHA-256",
				scopes = setOf(ApiKeyScope.PAYMENT_READ),
				createdByMerchantUserId = CREATOR,
				expiresAt = null,
				createdAt = CREATED_AT,
			)
		}
	}

	test("recordUsage sets lastUsedAt while ACTIVE") {
		val apiKey = newApiKey()
		val usedAt = CREATED_AT.plusSeconds(1)

		apiKey.recordUsage(usedAt)

		apiKey.lastUsedAt shouldBe usedAt
	}

	test("recordUsage fails once REVOKED") {
		val apiKey = newApiKey()
		apiKey.revoke(CREATOR, CREATED_AT.plusSeconds(1))

		shouldThrow<IllegalStateException> { apiKey.recordUsage(CREATED_AT.plusSeconds(2)) }
	}

	test("revoke moves ACTIVE to REVOKED and records who revoked it") {
		val apiKey = newApiKey()
		val revokedAt = CREATED_AT.plusSeconds(1)

		apiKey.revoke(CREATOR, revokedAt)

		apiKey.status shouldBe ApiKeyStatus.REVOKED
		apiKey.revokedByMerchantUserId shouldBe CREATOR
		apiKey.revokedAt shouldBe revokedAt
		apiKey.isUsable() shouldBe false
	}

	test("revoke fails once already REVOKED") {
		val apiKey = newApiKey()
		apiKey.revoke(CREATOR, CREATED_AT.plusSeconds(1))

		shouldThrow<IllegalStateException> { apiKey.revoke(CREATOR, CREATED_AT.plusSeconds(2)) }
	}

	test("expire moves ACTIVE to EXPIRED") {
		val apiKey = newApiKey()
		val changedAt = CREATED_AT.plusSeconds(1)

		apiKey.expire(changedAt)

		apiKey.status shouldBe ApiKeyStatus.EXPIRED
		apiKey.isUsable() shouldBe false
	}

	test("reconstitute rejects REVOKED without revokedAt") {
		shouldThrow<IllegalArgumentException> {
			MerchantApiKey.reconstitute(
				id = MerchantApiKeyId("mak_test_003"),
				merchantId = MerchantId("mrc_test_001"),
				keyName = "broken",
				environment = ApiEnvironment.TEST,
				keyPrefix = ApiKeyPrefix("sk_test_broken"),
				secretHash = "hashed-secret",
				hashAlgorithm = "HMAC-SHA-256",
				scopes = setOf(ApiKeyScope.PAYMENT_READ),
				createdByMerchantUserId = CREATOR,
				createdAt = CREATED_AT,
				status = ApiKeyStatus.REVOKED,
				expiresAt = null,
				lastUsedAt = null,
				revokedByMerchantUserId = CREATOR,
				revokedAt = null,
				updatedAt = CREATED_AT,
			)
		}
	}

	test("reconstitute restores a REVOKED key faithfully") {
		val revokedAt = CREATED_AT.plusSeconds(10)

		val apiKey = MerchantApiKey.reconstitute(
			id = MerchantApiKeyId("mak_test_004"),
			merchantId = MerchantId("mrc_test_001"),
			keyName = "운영 서버",
			environment = ApiEnvironment.TEST,
			keyPrefix = ApiKeyPrefix("sk_test_restored"),
			secretHash = "hashed-secret",
			hashAlgorithm = "HMAC-SHA-256",
			scopes = setOf(ApiKeyScope.PAYMENT_CREATE),
			createdByMerchantUserId = CREATOR,
			createdAt = CREATED_AT,
			status = ApiKeyStatus.REVOKED,
			expiresAt = null,
			lastUsedAt = CREATED_AT.plusSeconds(5),
			revokedByMerchantUserId = CREATOR,
			revokedAt = revokedAt,
			updatedAt = revokedAt,
		)

		apiKey.status shouldBe ApiKeyStatus.REVOKED
		apiKey.revokedAt shouldBe revokedAt
	}
})
