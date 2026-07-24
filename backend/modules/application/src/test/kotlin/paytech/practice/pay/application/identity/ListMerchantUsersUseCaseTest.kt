package paytech.practice.pay.application.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import paytech.practice.pay.application.port.outbound.MerchantUserListProjection
import paytech.practice.pay.application.port.outbound.MerchantUserRepository
import paytech.practice.pay.application.port.outbound.MerchantUserSummary
import paytech.practice.pay.domain.identity.AccountStatus
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

private fun activeSubAccount(role: MerchantUserRole): MerchantUser =
	MerchantUser
		.inviteSubAccount(
			id = QUERIER_ID,
			merchantId = MERCHANT_ID,
			loginId = LoginId("sub-login"),
			email = Email("sub@example.com"),
			userName = "하위 계정",
			role = role,
			invitedByMerchantUserId = MerchantUserId("mu_owner"),
			createdAt = NOW.minusSeconds(3_600),
		).apply { activate("hashed-password", NOW.minusSeconds(1_800)) }

private fun summary(): MerchantUserSummary =
	MerchantUserSummary(
		merchantUserId = MerchantUserId("mu_001"),
		loginId = LoginId("member01"),
		email = Email("member01@example.com"),
		userName = "팀원",
		role = MerchantUserRole.ADMIN,
		status = AccountStatus.INVITED,
		lastLoginAt = null,
		createdAt = NOW.minusSeconds(600),
	)

private fun newCommand(): ListMerchantUsersCommand = ListMerchantUsersCommand(queriedByMerchantUserId = QUERIER_ID)

class ListMerchantUsersUseCaseTest :
	FunSpec({

		test("an ACTIVE OWNER lists their own merchant's users") {
			val summaries = listOf(summary())
			val merchantUserRepository = mockk<MerchantUserRepository>()
			val projection = mockk<MerchantUserListProjection>()
			every { merchantUserRepository.findById(QUERIER_ID) } returns activeOwner()
			every { projection.findByMerchantId(MERCHANT_ID) } returns summaries

			val result = ListMerchantUsersUseCase(merchantUserRepository, projection).execute(newCommand())

			result.merchantUsers shouldBe summaries
		}

		test("an ACTIVE ADMIN can also list") {
			val merchantUserRepository = mockk<MerchantUserRepository>()
			val projection = mockk<MerchantUserListProjection>()
			every { merchantUserRepository.findById(QUERIER_ID) } returns activeSubAccount(MerchantUserRole.ADMIN)
			every { projection.findByMerchantId(MERCHANT_ID) } returns emptyList()

			val result = ListMerchantUsersUseCase(merchantUserRepository, projection).execute(newCommand())

			result.merchantUsers shouldBe emptyList()
		}

		test("a VIEWER querier throws MerchantUserCannotInviteSubAccountsException") {
			val merchantUserRepository = mockk<MerchantUserRepository>()
			every { merchantUserRepository.findById(QUERIER_ID) } returns activeSubAccount(MerchantUserRole.VIEWER)

			shouldThrow<MerchantUserCannotInviteSubAccountsException> {
				ListMerchantUsersUseCase(merchantUserRepository, mockk()).execute(newCommand())
			}
		}

		test("a SUSPENDED OWNER cannot list even though the role would allow it") {
			val suspendedOwner = activeOwner().apply { suspend(NOW.minusSeconds(600)) }
			val merchantUserRepository = mockk<MerchantUserRepository>()
			every { merchantUserRepository.findById(QUERIER_ID) } returns suspendedOwner

			shouldThrow<MerchantUserCannotInviteSubAccountsException> {
				ListMerchantUsersUseCase(merchantUserRepository, mockk()).execute(newCommand())
			}
		}

		test("the queried merchant always comes from the querier, never from the request") {
			// 멀티테넌시 방어의 핵심 — Command에 merchantId가 없고, 조회는 요청자 소속 가맹점으로만 나간다.
			val merchantUserRepository = mockk<MerchantUserRepository>()
			val projection = mockk<MerchantUserListProjection>()
			every { merchantUserRepository.findById(QUERIER_ID) } returns activeOwner()
			every { projection.findByMerchantId(any()) } returns emptyList()

			ListMerchantUsersUseCase(merchantUserRepository, projection).execute(newCommand())

			verify(exactly = 1) { projection.findByMerchantId(MERCHANT_ID) }
		}
	})
