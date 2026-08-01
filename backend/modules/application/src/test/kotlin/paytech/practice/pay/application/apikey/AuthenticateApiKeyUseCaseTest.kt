package paytech.practice.pay.application.apikey

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.ApiKeySecretHasher
import paytech.practice.pay.application.port.outbound.MerchantApiKeyRepository
import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.apikey.ApiEnvironment
import paytech.practice.pay.domain.apikey.ApiKeyPrefix
import paytech.practice.pay.domain.apikey.ApiKeyScope
import paytech.practice.pay.domain.apikey.MerchantApiKey
import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.merchant.Merchant
import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private const val PREFIX_TOKEN = "ab12cd34"
private val PREFIX = ApiKeyPrefix("sk_test_$PREFIX_TOKEN")
private const val SECRET = "s3cr3tValue"
private val RAW_KEY = "sk_test_${PREFIX_TOKEN}_$SECRET"
private val STORED_HASH = "hashed:$RAW_KEY"
private val MERCHANT_ID = MerchantId("mrc_test_001")

private fun activeKey(): MerchantApiKey =
	MerchantApiKey.create(
		id = MerchantApiKeyId("mak_test_001"),
		merchantId = MERCHANT_ID,
		keyName = "테스트 Key",
		environment = ApiEnvironment.TEST,
		keyPrefix = PREFIX,
		secretHash = STORED_HASH,
		hashAlgorithm = "HMAC-SHA256",
		scopes = setOf(ApiKeyScope.PAYMENT_CREATE),
		createdByMerchantUserId = MerchantUserId("mu_test_001"),
		expiresAt = null,
		createdAt = NOW.minusSeconds(3_600),
	)

private fun activeMerchant(): Merchant =
	Merchant.create(
		id = MERCHANT_ID,
		code = MerchantCode("test-merchant"),
		name = "테스트 가맹점",
		webhookUrl = null,
		createdAt = NOW.minusSeconds(7_200),
	)

private fun newUseCase(
	merchantApiKeyRepository: MerchantApiKeyRepository,
	merchantRepository: MerchantRepository = mockk { every { findById(MERCHANT_ID) } returns activeMerchant() },
	apiKeySecretHasher: ApiKeySecretHasher = mockk { every { matches(RAW_KEY, STORED_HASH) } returns true },
): AuthenticateApiKeyUseCase =
	AuthenticateApiKeyUseCase(
		merchantApiKeyRepository = merchantApiKeyRepository,
		merchantRepository = merchantRepository,
		apiKeySecretHasher = apiKeySecretHasher,
		transactionManager = ImmediateTransactionManager(),
		clock = FIXED_CLOCK,
	)

/** `runInTransaction`을 그대로 실행하는 가짜(다른 Use Case 테스트들과 같은 방식). */
private class ImmediateTransactionManager : TransactionManager {
	override fun <T> runInTransaction(block: () -> T): T = block()
}

class AuthenticateApiKeyUseCaseTest :
	FunSpec({

		test("a valid key returns the authenticated identity and records usage") {
			val repository = mockk<MerchantApiKeyRepository>(relaxed = true)
			every { repository.findByPrefixForUpdate(PREFIX) } returns activeKey()

			val result = newUseCase(repository).execute(AuthenticateApiKeyCommand(RAW_KEY))

			result.merchantId shouldBe MERCHANT_ID
			result.scopes shouldBe setOf(ApiKeyScope.PAYMENT_CREATE)
			verify(exactly = 1) { repository.save(any()) }
		}

		test("a malformed key (no underscores) throws InvalidApiKeyException") {
			val repository = mockk<MerchantApiKeyRepository>()

			shouldThrow<InvalidApiKeyException> {
				newUseCase(repository).execute(AuthenticateApiKeyCommand("not-a-valid-key"))
			}
		}

		test("an unknown prefix throws InvalidApiKeyException") {
			val repository = mockk<MerchantApiKeyRepository> { every { findByPrefixForUpdate(any()) } returns null }

			shouldThrow<InvalidApiKeyException> {
				newUseCase(repository).execute(AuthenticateApiKeyCommand(RAW_KEY))
			}
		}

		test("a secret mismatch throws InvalidApiKeyException") {
			val repository = mockk<MerchantApiKeyRepository> { every { findByPrefixForUpdate(PREFIX) } returns activeKey() }
			val hasher = mockk<ApiKeySecretHasher> { every { matches(any(), any()) } returns false }

			shouldThrow<InvalidApiKeyException> {
				newUseCase(repository, apiKeySecretHasher = hasher).execute(AuthenticateApiKeyCommand(RAW_KEY))
			}
		}

		test("a revoked key throws InvalidApiKeyException") {
			val key = activeKey().apply { revoke(MerchantUserId("mu_test_001"), NOW.minusSeconds(60)) }
			val repository = mockk<MerchantApiKeyRepository> { every { findByPrefixForUpdate(PREFIX) } returns key }

			shouldThrow<InvalidApiKeyException> {
				newUseCase(repository).execute(AuthenticateApiKeyCommand(RAW_KEY))
			}
		}

		test("an expired key throws InvalidApiKeyException") {
			val key =
				MerchantApiKey.create(
					id = MerchantApiKeyId("mak_test_002"),
					merchantId = MERCHANT_ID,
					keyName = "만료된 Key",
					environment = ApiEnvironment.TEST,
					keyPrefix = PREFIX,
					secretHash = STORED_HASH,
					hashAlgorithm = "HMAC-SHA256",
					scopes = setOf(ApiKeyScope.PAYMENT_CREATE),
					createdByMerchantUserId = MerchantUserId("mu_test_001"),
					expiresAt = NOW.minusSeconds(1),
					createdAt = NOW.minusSeconds(3_600),
				)
			val repository = mockk<MerchantApiKeyRepository> { every { findByPrefixForUpdate(PREFIX) } returns key }

			shouldThrow<InvalidApiKeyException> {
				newUseCase(repository).execute(AuthenticateApiKeyCommand(RAW_KEY))
			}
		}

		test("a LIVE environment key throws InvalidApiKeyException") {
			val key =
				MerchantApiKey.create(
					id = MerchantApiKeyId("mak_test_003"),
					merchantId = MERCHANT_ID,
					keyName = "라이브 Key",
					environment = ApiEnvironment.LIVE,
					keyPrefix = PREFIX,
					secretHash = STORED_HASH,
					hashAlgorithm = "HMAC-SHA256",
					scopes = setOf(ApiKeyScope.PAYMENT_CREATE),
					createdByMerchantUserId = MerchantUserId("mu_test_001"),
					expiresAt = null,
					createdAt = NOW.minusSeconds(3_600),
				)
			val repository = mockk<MerchantApiKeyRepository> { every { findByPrefixForUpdate(PREFIX) } returns key }

			shouldThrow<InvalidApiKeyException> {
				newUseCase(repository).execute(AuthenticateApiKeyCommand(RAW_KEY))
			}
		}

		test("a merchant that cannot accept payments throws InvalidApiKeyException") {
			val repository = mockk<MerchantApiKeyRepository> { every { findByPrefixForUpdate(PREFIX) } returns activeKey() }
			val suspendedMerchant = activeMerchant().apply { suspend(NOW.minusSeconds(60)) }
			val merchantRepository = mockk<MerchantRepository> { every { findById(MERCHANT_ID) } returns suspendedMerchant }

			shouldThrow<InvalidApiKeyException> {
				newUseCase(repository, merchantRepository).execute(AuthenticateApiKeyCommand(RAW_KEY))
			}
		}
	})
