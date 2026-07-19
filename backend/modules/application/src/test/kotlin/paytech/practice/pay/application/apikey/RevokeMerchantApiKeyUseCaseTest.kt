package paytech.practice.pay.application.apikey

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.MerchantApiKeyRepository
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.domain.apikey.ApiEnvironment
import paytech.practice.pay.domain.apikey.ApiKeyPrefix
import paytech.practice.pay.domain.apikey.ApiKeyScope
import paytech.practice.pay.domain.apikey.MerchantApiKey
import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUser
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-19T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val MERCHANT_ID = MerchantId("mrc_test_001")
private val OTHER_MERCHANT_ID = MerchantId("mrc_other_001")
private val REVOKER_ID = MerchantUserId("mu_revoker")
private val KEY_ID = MerchantApiKeyId("mak_target")

private fun activeOwner(merchantId: MerchantId = MERCHANT_ID): MerchantUser =
	MerchantUser
		.inviteInitialOwner(
			id = REVOKER_ID,
			merchantId = merchantId,
			loginId = LoginId("owner-login"),
			email = Email("owner@example.com"),
			userName = "가맹점 대표",
			invitedByInternalUserId = InternalUserId("iu_registrar"),
			createdAt = NOW.minusSeconds(3_600),
		).apply { activate("hashed-password", NOW.minusSeconds(1_800)) }

private fun keyIn(
	merchantId: MerchantId,
	status: ApiKeyStatusFixture = ApiKeyStatusFixture.ACTIVE,
): MerchantApiKey {
	val key =
		MerchantApiKey.create(
			id = KEY_ID,
			merchantId = merchantId,
			keyName = "테스트 Key",
			environment = ApiEnvironment.TEST,
			keyPrefix = ApiKeyPrefix("sk_test_abcdefgh"),
			secretHash = "hashed-secret",
			hashAlgorithm = "HMAC-SHA256",
			scopes = setOf(ApiKeyScope.PAYMENT_CREATE),
			createdByMerchantUserId = REVOKER_ID,
			expiresAt = null,
			createdAt = NOW.minusSeconds(7_200),
		)
	when (status) {
		ApiKeyStatusFixture.ACTIVE -> Unit
		ApiKeyStatusFixture.REVOKED -> key.revoke(REVOKER_ID, NOW.minusSeconds(600))
	}
	return key
}

private enum class ApiKeyStatusFixture { ACTIVE, REVOKED }

private fun newCommand(): RevokeMerchantApiKeyCommand =
	RevokeMerchantApiKeyCommand(merchantApiKeyId = KEY_ID, revokedByMerchantUserId = REVOKER_ID)

private fun newUseCase(
	merchantUserRepository: MerchantUserRepository,
	merchantApiKeyRepository: MerchantApiKeyRepository,
): RevokeMerchantApiKeyUseCase =
	RevokeMerchantApiKeyUseCase(
		merchantUserRepository = merchantUserRepository,
		merchantApiKeyRepository = merchantApiKeyRepository,
		clock = FIXED_CLOCK,
	)

class RevokeMerchantApiKeyUseCaseTest :
	FunSpec({

		test("an ACTIVE OWNER revokes their own merchant's ACTIVE key") {
			val merchantUserRepository = mockk<MerchantUserRepository>()
			val merchantApiKeyRepository = mockk<MerchantApiKeyRepository>(relaxed = true)
			every { merchantUserRepository.findById(REVOKER_ID) } returns activeOwner()
			every { merchantApiKeyRepository.findById(KEY_ID) } returns keyIn(MERCHANT_ID)

			val savedKeys = mutableListOf<MerchantApiKey>()
			every { merchantApiKeyRepository.save(capture(savedKeys)) } returns Unit

			val result = newUseCase(merchantUserRepository, merchantApiKeyRepository).execute(newCommand())

			result.merchantApiKeyId shouldBe KEY_ID
			result.revokedAt shouldBe NOW

			verify(exactly = 1) { merchantApiKeyRepository.save(any()) }
			val saved = savedKeys.single()
			saved.status.name shouldBe "REVOKED"
			saved.revokedByMerchantUserId shouldBe REVOKER_ID
		}

		test("a VIEWER revoker throws MerchantUserCannotManageApiKeysException") {
			val viewer =
				MerchantUser
					.inviteSubAccount(
						id = REVOKER_ID,
						merchantId = MERCHANT_ID,
						loginId = LoginId("viewer-login"),
						email = Email("viewer@example.com"),
						userName = "뷰어",
						role = MerchantUserRole.VIEWER,
						invitedByMerchantUserId = MerchantUserId("mu_owner"),
						createdAt = NOW.minusSeconds(3_600),
					).apply { activate("hashed-password", NOW.minusSeconds(1_800)) }

			val merchantUserRepository = mockk<MerchantUserRepository>()
			every { merchantUserRepository.findById(REVOKER_ID) } returns viewer

			shouldThrow<MerchantUserCannotManageApiKeysException> {
				newUseCase(merchantUserRepository, mockk()).execute(newCommand())
			}
		}

		test("a nonexistent key throws MerchantApiKeyNotFoundException") {
			val merchantUserRepository = mockk<MerchantUserRepository>()
			val merchantApiKeyRepository = mockk<MerchantApiKeyRepository>()
			every { merchantUserRepository.findById(REVOKER_ID) } returns activeOwner()
			every { merchantApiKeyRepository.findById(KEY_ID) } returns null

			shouldThrow<MerchantApiKeyNotFoundException> {
				newUseCase(merchantUserRepository, merchantApiKeyRepository).execute(newCommand())
			}
		}

		test("a key belonging to a different merchant throws MerchantApiKeyNotFoundException, not a permission error") {
			val merchantUserRepository = mockk<MerchantUserRepository>()
			val merchantApiKeyRepository = mockk<MerchantApiKeyRepository>()
			every { merchantUserRepository.findById(REVOKER_ID) } returns activeOwner(merchantId = MERCHANT_ID)
			every { merchantApiKeyRepository.findById(KEY_ID) } returns keyIn(OTHER_MERCHANT_ID)

			shouldThrow<MerchantApiKeyNotFoundException> {
				newUseCase(merchantUserRepository, merchantApiKeyRepository).execute(newCommand())
			}
		}

		test("an already-REVOKED key throws MerchantApiKeyNotActiveException instead of a raw domain IllegalStateException") {
			val merchantUserRepository = mockk<MerchantUserRepository>()
			val merchantApiKeyRepository = mockk<MerchantApiKeyRepository>()
			every { merchantUserRepository.findById(REVOKER_ID) } returns activeOwner()
			every { merchantApiKeyRepository.findById(KEY_ID) } returns keyIn(MERCHANT_ID, ApiKeyStatusFixture.REVOKED)

			shouldThrow<MerchantApiKeyNotActiveException> {
				newUseCase(merchantUserRepository, merchantApiKeyRepository).execute(newCommand())
			}
		}
	})
