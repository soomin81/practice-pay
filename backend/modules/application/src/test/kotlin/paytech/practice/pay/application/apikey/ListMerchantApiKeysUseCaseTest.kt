package paytech.practice.pay.application.apikey

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import paytech.practice.pay.application.port.outbound.MerchantApiKeyListProjection
import paytech.practice.pay.application.port.outbound.MerchantApiKeySummary
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.domain.apikey.ApiEnvironment
import paytech.practice.pay.domain.apikey.ApiKeyPrefix
import paytech.practice.pay.domain.apikey.ApiKeyScope
import paytech.practice.pay.domain.apikey.ApiKeyStatus
import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUser
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-19T00:00:00Z")
private val MERCHANT_ID = MerchantId("mrc_test_001")
private val QUERIER_ID = MerchantUserId("mu_querier")

private fun activeOwner(): MerchantUser =
	MerchantUser
		.inviteInitialOwner(
			id = QUERIER_ID,
			merchantId = MERCHANT_ID,
			loginId = LoginId("owner-login"),
			email = Email("owner@example.com"),
			userName = "가맹점 대표",
			invitedByInternalUserId = InternalUserId("iu_registrar"),
			createdAt = NOW.minusSeconds(3_600),
		).apply { activate("hashed-password", NOW.minusSeconds(1_800)) }

private fun newCommand(): ListMerchantApiKeysCommand = ListMerchantApiKeysCommand(queriedByMerchantUserId = QUERIER_ID)

class ListMerchantApiKeysUseCaseTest :
	FunSpec({

		test("an ACTIVE OWNER lists their own merchant's API keys") {
			val summaries =
				listOf(
					MerchantApiKeySummary(
						merchantApiKeyId = MerchantApiKeyId("mak_001"),
						keyName = "운영 서버용 Key",
						environment = ApiEnvironment.TEST,
						keyPrefix = ApiKeyPrefix("sk_test_ab12cd34"),
						scopes = setOf(ApiKeyScope.PAYMENT_CREATE),
						status = ApiKeyStatus.ACTIVE,
						createdAt = NOW.minusSeconds(3_600),
						lastUsedAt = null,
						revokedAt = null,
					),
				)
			val merchantUserRepository = mockk<MerchantUserRepository>()
			val merchantApiKeyListProjection = mockk<MerchantApiKeyListProjection>()
			every { merchantUserRepository.findById(QUERIER_ID) } returns activeOwner()
			every { merchantApiKeyListProjection.findByMerchantId(MERCHANT_ID) } returns summaries

			val result =
				ListMerchantApiKeysUseCase(merchantUserRepository, merchantApiKeyListProjection).execute(newCommand())

			result.apiKeys shouldBe summaries
		}

		test("a VIEWER querier throws MerchantUserCannotManageApiKeysException") {
			val viewer =
				MerchantUser
					.inviteSubAccount(
						id = QUERIER_ID,
						merchantId = MERCHANT_ID,
						loginId = LoginId("viewer-login"),
						email = Email("viewer@example.com"),
						userName = "뷰어",
						role = MerchantUserRole.VIEWER,
						invitedByMerchantUserId = MerchantUserId("mu_owner"),
						createdAt = NOW.minusSeconds(3_600),
					).apply { activate("hashed-password", NOW.minusSeconds(1_800)) }

			val merchantUserRepository = mockk<MerchantUserRepository>()
			every { merchantUserRepository.findById(QUERIER_ID) } returns viewer

			shouldThrow<MerchantUserCannotManageApiKeysException> {
				ListMerchantApiKeysUseCase(merchantUserRepository, mockk()).execute(newCommand())
			}
		}

		test("a SUSPENDED OWNER cannot list even though the role would allow it") {
			val suspendedOwner = activeOwner().apply { suspend(NOW.minusSeconds(600)) }

			val merchantUserRepository = mockk<MerchantUserRepository>()
			every { merchantUserRepository.findById(QUERIER_ID) } returns suspendedOwner

			shouldThrow<MerchantUserCannotManageApiKeysException> {
				ListMerchantApiKeysUseCase(merchantUserRepository, mockk()).execute(newCommand())
			}
		}

		test("returns an empty list when the merchant has no API keys") {
			val merchantUserRepository = mockk<MerchantUserRepository>()
			val merchantApiKeyListProjection = mockk<MerchantApiKeyListProjection>()
			every { merchantUserRepository.findById(QUERIER_ID) } returns activeOwner()
			every { merchantApiKeyListProjection.findByMerchantId(MERCHANT_ID) } returns emptyList()

			val result =
				ListMerchantApiKeysUseCase(merchantUserRepository, merchantApiKeyListProjection).execute(newCommand())

			result.apiKeys shouldBe emptyList()
		}
	})
