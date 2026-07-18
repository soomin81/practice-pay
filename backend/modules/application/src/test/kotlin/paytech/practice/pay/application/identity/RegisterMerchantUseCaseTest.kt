package paytech.practice.pay.application.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.AccountInvitationRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.InvitationTokenHasher
import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.identity.AccountInvitation
import paytech.practice.pay.domain.identity.AccountInvitationStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUser
import paytech.practice.pay.domain.merchant.Merchant
import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-07-19T00:00:00Z")
private val FIXED_CLOCK: Clock = Clock.fixed(NOW, ZoneOffset.UTC)
private val REGISTRAR_ID = InternalUserId("iu_operator")
private val MERCHANT_CODE = MerchantCode("NEW_MERCHANT")
private val OWNER_LOGIN_ID = LoginId("owner-login")
private val OWNER_EMAIL = Email("owner@example.com")

private fun newCommand(): RegisterMerchantCommand =
	RegisterMerchantCommand(
		merchantCode = MERCHANT_CODE,
		merchantName = "새 가맹점",
		webhookUrl = null,
		ownerLoginId = OWNER_LOGIN_ID,
		ownerEmail = OWNER_EMAIL,
		ownerUserName = "가맹점 대표",
		registeredByInternalUserId = REGISTRAR_ID,
	)

private class RegisterImmediateTransactionManager : TransactionManager {
	override fun <T> runInTransaction(block: () -> T): T = block()
}

private class RegisterFakeIdGenerator : IdGenerator {
	private var counter = 0

	override fun newId(): String {
		counter += 1
		return "id$counter"
	}
}

private fun newUseCase(
	merchantRepository: MerchantRepository,
	merchantUserRepository: MerchantUserRepository,
	accountInvitationRepository: AccountInvitationRepository,
	invitationTokenHasher: InvitationTokenHasher = mockk { every { hash(any()) } answers { "hashed:${firstArg<String>()}" } },
): RegisterMerchantUseCase =
	RegisterMerchantUseCase(
		merchantRepository = merchantRepository,
		merchantUserRepository = merchantUserRepository,
		accountInvitationRepository = accountInvitationRepository,
		invitationTokenHasher = invitationTokenHasher,
		idGenerator = RegisterFakeIdGenerator(),
		transactionManager = RegisterImmediateTransactionManager(),
		clock = FIXED_CLOCK,
	)

class RegisterMerchantUseCaseTest :
	FunSpec({

		test("registers an ACTIVE Merchant with an INVITED OWNER and a PENDING AccountInvitation") {
			val merchantRepository = mockk<MerchantRepository>(relaxed = true)
			val merchantUserRepository = mockk<MerchantUserRepository>(relaxed = true)
			val accountInvitationRepository = mockk<AccountInvitationRepository>(relaxed = true)
			every { merchantRepository.findByCode(MERCHANT_CODE) } returns null

			val savedMerchants = mutableListOf<Merchant>()
			val savedOwners = mutableListOf<MerchantUser>()
			val savedInvitations = mutableListOf<AccountInvitation>()
			every { merchantRepository.save(capture(savedMerchants)) } returns Unit
			every { merchantUserRepository.save(capture(savedOwners)) } returns Unit
			every { accountInvitationRepository.save(capture(savedInvitations)) } returns Unit

			val result = newUseCase(merchantRepository, merchantUserRepository, accountInvitationRepository).execute(newCommand())

			result.merchantCode shouldBe MERCHANT_CODE
			result.ownerLoginId shouldBe OWNER_LOGIN_ID
			result.ownerEmail shouldBe OWNER_EMAIL
			result.invitationToken shouldNotBe "hashed:${result.invitationToken}"

			verify(exactly = 1) { merchantRepository.save(any()) }
			verify(exactly = 1) { merchantUserRepository.save(any()) }
			verify(exactly = 1) { accountInvitationRepository.save(any()) }

			val savedMerchant = savedMerchants.single()
			savedMerchant.status.name shouldBe "ACTIVE"
			savedMerchant.code shouldBe MERCHANT_CODE

			val savedOwner = savedOwners.single()
			savedOwner.status.name shouldBe "INVITED"
			savedOwner.role.name shouldBe "OWNER"
			savedOwner.merchantId shouldBe savedMerchant.id
			savedOwner.invitedByInternalUserId shouldBe REGISTRAR_ID

			val savedInvitation = savedInvitations.single()
			savedInvitation.status shouldBe AccountInvitationStatus.PENDING
			savedInvitation.merchantUserId shouldBe savedOwner.id
			savedInvitation.tokenHash shouldBe "hashed:${result.invitationToken}"
		}

		test("duplicate merchantCode throws DuplicateMerchantException") {
			val merchantRepository = mockk<MerchantRepository>()
			every { merchantRepository.findByCode(MERCHANT_CODE) } returns
				Merchant.create(
					id = MerchantId("mrc_existing"),
					code = MERCHANT_CODE,
					name = "기존 가맹점",
					webhookUrl = null,
					createdAt = NOW.minusSeconds(3_600),
				)

			shouldThrow<DuplicateMerchantException> {
				newUseCase(merchantRepository, mockk(), mockk()).execute(newCommand())
			}
		}
	})
