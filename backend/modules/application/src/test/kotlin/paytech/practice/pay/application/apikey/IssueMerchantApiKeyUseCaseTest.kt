package paytech.practice.pay.application.apikey

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.ApiKeySecretHasher
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.MerchantApiKeyRepository
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.domain.apikey.ApiEnvironment
import paytech.practice.pay.domain.apikey.ApiKeyScope
import paytech.practice.pay.domain.apikey.MerchantApiKey
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
private val ISSUER_ID = MerchantUserId("mu_issuer")

private fun activeOwner(): MerchantUser =
	MerchantUser
		.inviteInitialOwner(
			id = ISSUER_ID,
			merchantId = MERCHANT_ID,
			loginId = LoginId("owner-login"),
			email = Email("owner@example.com"),
			userName = "가맹점 대표",
			invitedByInternalUserId = InternalUserId("iu_registrar"),
			createdAt = NOW.minusSeconds(3_600),
		).apply { activate("hashed-password", NOW.minusSeconds(1_800)) }

private fun newCommand(scopes: Set<ApiKeyScope> = setOf(ApiKeyScope.PAYMENT_CREATE, ApiKeyScope.PAYMENT_READ)): IssueMerchantApiKeyCommand =
	IssueMerchantApiKeyCommand(
		keyName = "운영 서버용 Key",
		scopes = scopes,
		issuedByMerchantUserId = ISSUER_ID,
	)

private class IssueFakeIdGenerator : IdGenerator {
	private var counter = 0

	override fun newId(): String {
		counter += 1
		return "idfake$counter"
	}
}

private fun newUseCase(
	merchantUserRepository: MerchantUserRepository,
	merchantApiKeyRepository: MerchantApiKeyRepository,
	apiKeySecretHasher: ApiKeySecretHasher = mockk { every { hash(any()) } answers { "hashed:${firstArg<String>()}" } },
): IssueMerchantApiKeyUseCase =
	IssueMerchantApiKeyUseCase(
		merchantUserRepository = merchantUserRepository,
		merchantApiKeyRepository = merchantApiKeyRepository,
		apiKeySecretHasher = apiKeySecretHasher,
		idGenerator = IssueFakeIdGenerator(),
		clock = FIXED_CLOCK,
	)

class IssueMerchantApiKeyUseCaseTest :
	FunSpec({

		test("an ACTIVE OWNER issues an ACTIVE TEST key with the requested scopes") {
			val merchantUserRepository = mockk<MerchantUserRepository>()
			val merchantApiKeyRepository = mockk<MerchantApiKeyRepository>(relaxed = true)
			every { merchantUserRepository.findById(ISSUER_ID) } returns activeOwner()

			val savedKeys = mutableListOf<MerchantApiKey>()
			every { merchantApiKeyRepository.save(capture(savedKeys)) } returns Unit

			val result = newUseCase(merchantUserRepository, merchantApiKeyRepository).execute(newCommand())

			result.environment shouldBe ApiEnvironment.TEST
			result.scopes shouldBe setOf(ApiKeyScope.PAYMENT_CREATE, ApiKeyScope.PAYMENT_READ)
			result.keyPrefix.value.startsWith("sk_test_") shouldBe true

			// AuthenticateApiKeyUseCase.extractPrefix()와 같은 방식(앞 3세그먼트)으로 잘라도
			// 같은 keyPrefix가 나와야 한다 — 발급이 만드는 형식과 인증이 파싱하는 형식이
			// 어긋나면 방금 발급한 Key로 곧바로 인증에 실패하는 상황이 생긴다.
			val parsedPrefix =
				result.rawApiKey
					.split("_", limit = 4)
					.take(3)
					.joinToString("_")
			parsedPrefix shouldBe result.keyPrefix.value

			verify(exactly = 1) { merchantApiKeyRepository.save(any()) }
			val saved = savedKeys.single()
			saved.status.name shouldBe "ACTIVE"
			saved.merchantId shouldBe MERCHANT_ID
			saved.createdByMerchantUserId shouldBe ISSUER_ID
			saved.expiresAt shouldBe null
		}

		test("a VIEWER issuer throws MerchantUserCannotManageApiKeysException") {
			val viewer =
				MerchantUser
					.inviteSubAccount(
						id = ISSUER_ID,
						merchantId = MERCHANT_ID,
						loginId = LoginId("viewer-login"),
						email = Email("viewer@example.com"),
						userName = "뷰어",
						role = MerchantUserRole.VIEWER,
						invitedByMerchantUserId = MerchantUserId("mu_owner"),
						createdAt = NOW.minusSeconds(3_600),
					).apply { activate("hashed-password", NOW.minusSeconds(1_800)) }

			val merchantUserRepository = mockk<MerchantUserRepository>()
			every { merchantUserRepository.findById(ISSUER_ID) } returns viewer

			shouldThrow<MerchantUserCannotManageApiKeysException> {
				newUseCase(merchantUserRepository, mockk()).execute(newCommand())
			}
		}

		test("a SUSPENDED OWNER cannot issue even though the role would allow it") {
			val suspendedOwner = activeOwner().apply { suspend(NOW.minusSeconds(600)) }

			val merchantUserRepository = mockk<MerchantUserRepository>()
			every { merchantUserRepository.findById(ISSUER_ID) } returns suspendedOwner

			shouldThrow<MerchantUserCannotManageApiKeysException> {
				newUseCase(merchantUserRepository, mockk()).execute(newCommand())
			}
		}

		test("empty scopes throws IllegalArgumentException") {
			val merchantUserRepository = mockk<MerchantUserRepository>()
			every { merchantUserRepository.findById(ISSUER_ID) } returns activeOwner()

			shouldThrow<IllegalArgumentException> {
				newUseCase(merchantUserRepository, mockk()).execute(newCommand(scopes = emptySet()))
			}
		}

		test("a scope outside the MVP allow-list throws IllegalArgumentException") {
			val merchantUserRepository = mockk<MerchantUserRepository>()
			every { merchantUserRepository.findById(ISSUER_ID) } returns activeOwner()

			shouldThrow<IllegalArgumentException> {
				newUseCase(merchantUserRepository, mockk()).execute(newCommand(scopes = setOf(ApiKeyScope.SETTLEMENT_READ)))
			}
		}
	})
