package paytech.practice.pay.infra.persistence.jooq.identity

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUser
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-19T00:00:00Z")

/** `merchant_user.invited_by_internal_user_seq`의 FK 제약을 만족시키기 위해 실제 InternalUser 행을 심는다. */
private fun insertTestInternalUser(): InternalUserId {
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
	return internalUserId
}

private fun ownerIn(
	merchantId: MerchantId,
	invitedBy: InternalUserId,
	createdAt: Instant,
): MerchantUser =
	MerchantUser.inviteInitialOwner(
		id = MerchantUserId("mu_${uniqueSuffix()}"),
		merchantId = merchantId,
		loginId = LoginId("owner-${uniqueSuffix()}"),
		email = Email("${uniqueSuffix()}@example.com"),
		userName = "테스트 오너",
		invitedByInternalUserId = invitedBy,
		createdAt = createdAt,
	)

private fun subAccountIn(
	merchantId: MerchantId,
	invitedBy: MerchantUserId,
	role: MerchantUserRole,
	createdAt: Instant,
): MerchantUser =
	MerchantUser.inviteSubAccount(
		id = MerchantUserId("mu_${uniqueSuffix()}"),
		merchantId = merchantId,
		loginId = LoginId("sub-${uniqueSuffix()}"),
		email = Email("${uniqueSuffix()}@example.com"),
		userName = "테스트 하위 계정",
		role = role,
		invitedByMerchantUserId = invitedBy,
		createdAt = createdAt,
	)

class MerchantUserListProjectionAdapterTest :
	FunSpec({
		val repositoryAdapter = MerchantUserRepositoryAdapter(PersistenceTestSupport.dsl)
		val projectionAdapter = MerchantUserListProjectionAdapter(PersistenceTestSupport.dsl)

		test("findByMerchantId returns summaries ordered by createdAt descending") {
			val merchantId = MerchantId(insertTestMerchant())
			val internalUserId = insertTestInternalUser()
			val owner = ownerIn(merchantId, internalUserId, NOW.minusSeconds(3_600))
			repositoryAdapter.save(owner)
			val newer = subAccountIn(merchantId, owner.id, MerchantUserRole.ADMIN, NOW.minusSeconds(60))
			val older = subAccountIn(merchantId, owner.id, MerchantUserRole.VIEWER, NOW.minusSeconds(1_800))
			repositoryAdapter.save(newer)
			repositoryAdapter.save(older)

			val summaries = projectionAdapter.findByMerchantId(merchantId)

			summaries.map { it.merchantUserId } shouldBe listOf(newer.id, older.id, owner.id)
		}

		test("INVITED and ACTIVE users are both returned with their own status") {
			val merchantId = MerchantId(insertTestMerchant())
			val internalUserId = insertTestInternalUser()
			val owner = ownerIn(merchantId, internalUserId, NOW.minusSeconds(3_600))
			owner.activate("hashed-password", NOW.minusSeconds(1_800))
			repositoryAdapter.save(owner)
			val invited = subAccountIn(merchantId, owner.id, MerchantUserRole.ADMIN, NOW.minusSeconds(60))
			repositoryAdapter.save(invited)

			val byId = projectionAdapter.findByMerchantId(merchantId).associateBy { it.merchantUserId }

			byId.getValue(owner.id).status shouldBe AccountStatus.ACTIVE
			byId.getValue(invited.id).status shouldBe AccountStatus.INVITED
			byId.getValue(invited.id).role shouldBe MerchantUserRole.ADMIN
			// 아직 로그인한 적이 없으면 null이다 — 화면이 "—"로 그린다.
			byId.getValue(invited.id).lastLoginAt shouldBe null
		}

		test("users of another merchant are never included") {
			val merchantId = MerchantId(insertTestMerchant())
			val otherMerchantId = MerchantId(insertTestMerchant())
			val internalUserId = insertTestInternalUser()
			val mine = ownerIn(merchantId, internalUserId, NOW.minusSeconds(3_600))
			val theirs = ownerIn(otherMerchantId, internalUserId, NOW.minusSeconds(3_600))
			repositoryAdapter.save(mine)
			repositoryAdapter.save(theirs)

			val summaries = projectionAdapter.findByMerchantId(merchantId)

			summaries.map { it.merchantUserId } shouldBe listOf(mine.id)
		}

		test("an unknown merchant returns an empty list") {
			projectionAdapter.findByMerchantId(MerchantId("mrc_no_such_merchant")) shouldBe emptyList()
		}
	})
