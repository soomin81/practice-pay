package paytech.practice.pay.infra.persistence.jooq.identity

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantLoginAudit
import paytech.practice.pay.domain.identity.MerchantLoginAuditId
import paytech.practice.pay.domain.identity.MerchantLoginOutcome
import paytech.practice.pay.domain.identity.MerchantUser
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")

/** merchant_user의 invited_by FK를 만족시킬 최소 internal_user 하나(다른 어댑터 테스트와 같은 헬퍼). */
private fun insertTestInternalUser(): InternalUserId {
	val id = InternalUserId("iu_${uniqueSuffix()}")
	InternalUserRepositoryAdapter(PersistenceTestSupport.dsl).save(
		InternalUser.bootstrap(
			id = id,
			loginId = LoginId("internal-${uniqueSuffix()}"),
			email = Email("${uniqueSuffix()}@example.com"),
			userName = "테스트 내부 운영자",
			passwordHash = "hashed-password",
			createdAt = NOW,
		),
	)
	return id
}

private fun savedOwner(merchantId: MerchantId): MerchantUser {
	val user =
		MerchantUser.inviteInitialOwner(
			id = MerchantUserId("mu_${uniqueSuffix()}"),
			merchantId = merchantId,
			loginId = LoginId("owner-${uniqueSuffix()}"),
			email = Email("${uniqueSuffix()}@example.com"),
			userName = "테스트 오너",
			invitedByInternalUserId = insertTestInternalUser(),
			createdAt = NOW,
		)
	MerchantUserRepositoryAdapter(PersistenceTestSupport.dsl).save(user)
	return user
}

class MerchantLoginAuditAdapterTest :
	FunSpec({
		val recorder = MerchantLoginAuditRepositoryAdapter(PersistenceTestSupport.dsl)
		val projection = MerchantLoginAuditProjectionAdapter(PersistenceTestSupport.dsl)

		test("append persists a SUCCESS entry and findRecent joins merchant name and user name") {
			val merchantId = MerchantId(insertTestMerchant())
			val owner = savedOwner(merchantId)
			val auditId = MerchantLoginAuditId("mla_${uniqueSuffix()}")
			recorder.append(
				MerchantLoginAudit(
					id = auditId,
					merchantId = merchantId,
					merchantUserId = owner.id,
					attemptedMerchantCode = "code-${uniqueSuffix()}",
					attemptedLoginId = owner.loginId,
					outcome = MerchantLoginOutcome.SUCCESS,
					clientIp = "203.0.113.7",
					occurredAt = NOW,
				),
			)

			val entry = projection.findRecent(500).first { it.auditId == auditId }

			entry.merchantId shouldBe merchantId
			entry.merchantName shouldBe "테스트 가맹점"
			entry.userName shouldBe "테스트 오너"
			entry.outcome shouldBe MerchantLoginOutcome.SUCCESS
			entry.clientIp shouldBe "203.0.113.7"
		}

		test("an attempt on an unknown merchantCode is stored with null merchant and user") {
			val auditId = MerchantLoginAuditId("mla_${uniqueSuffix()}")
			val attemptedCode = "ghost-${uniqueSuffix()}"
			recorder.append(
				MerchantLoginAudit(
					id = auditId,
					merchantId = null,
					merchantUserId = null,
					attemptedMerchantCode = attemptedCode,
					attemptedLoginId = LoginId("ghost-${uniqueSuffix()}"),
					outcome = MerchantLoginOutcome.INVALID_CREDENTIALS,
					clientIp = null,
					occurredAt = NOW,
				),
			)

			val entry = projection.findRecent(500).first { it.auditId == auditId }

			entry.merchantId.shouldBeNull()
			entry.merchantName.shouldBeNull()
			entry.userName.shouldBeNull()
			entry.attemptedMerchantCode shouldBe attemptedCode
		}

		test("findRecent orders by occurredAt descending") {
			val merchantId = MerchantId(insertTestMerchant())
			val owner = savedOwner(merchantId)
			val older = MerchantLoginAuditId("mla_${uniqueSuffix()}")
			val newer = MerchantLoginAuditId("mla_${uniqueSuffix()}")
			recorder.append(audit(older, merchantId, owner, NOW.minusSeconds(3_600)))
			recorder.append(audit(newer, merchantId, owner, NOW))

			val ids = projection.findRecent(500).map { it.auditId }

			(ids.indexOf(newer) >= 0 && ids.indexOf(older) >= 0) shouldBe true
			(ids.indexOf(newer) < ids.indexOf(older)) shouldBe true
		}
	})

private fun audit(
	id: MerchantLoginAuditId,
	merchantId: MerchantId,
	owner: MerchantUser,
	occurredAt: Instant,
): MerchantLoginAudit =
	MerchantLoginAudit(
		id = id,
		merchantId = merchantId,
		merchantUserId = owner.id,
		attemptedMerchantCode = "code-${uniqueSuffix()}",
		attemptedLoginId = owner.loginId,
		outcome = MerchantLoginOutcome.SUCCESS,
		clientIp = null,
		occurredAt = occurredAt,
	)
