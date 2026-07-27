package paytech.practice.pay.infra.persistence.jooq.identity

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalLoginAudit
import paytech.practice.pay.domain.identity.InternalLoginAuditId
import paytech.practice.pay.domain.identity.InternalLoginOutcome
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")

private fun savedInternalUser(): InternalUser {
	val user =
		InternalUser.bootstrap(
			id = InternalUserId("iu_${uniqueSuffix()}"),
			loginId = LoginId("admin-${uniqueSuffix()}"),
			email = Email("${uniqueSuffix()}@example.com"),
			userName = "테스트 관리자",
			passwordHash = "hashed-password",
			createdAt = NOW,
		)
	InternalUserRepositoryAdapter(PersistenceTestSupport.dsl).save(user)
	return user
}

class InternalLoginAuditAdapterTest :
	FunSpec({
		val recorder = InternalLoginAuditRepositoryAdapter(PersistenceTestSupport.dsl)
		val projection = InternalLoginAuditProjectionAdapter(PersistenceTestSupport.dsl)

		test("append persists a SUCCESS entry linked to the user and findRecent joins the user name") {
			val user = savedInternalUser()
			val auditId = InternalLoginAuditId("ila_${uniqueSuffix()}")
			recorder.append(
				InternalLoginAudit(
					id = auditId,
					internalUserId = user.id,
					attemptedLoginId = user.loginId,
					outcome = InternalLoginOutcome.SUCCESS,
					clientIp = "203.0.113.7",
					occurredAt = NOW,
				),
			)

			val entry = projection.findRecent(500).first { it.auditId == auditId }

			entry.internalUserId shouldBe user.id
			entry.userName shouldBe "테스트 관리자"
			entry.outcome shouldBe InternalLoginOutcome.SUCCESS
			entry.clientIp shouldBe "203.0.113.7"
		}

		test("an attempt on an unknown loginId is stored with a null user and surfaces a null userName") {
			val auditId = InternalLoginAuditId("ila_${uniqueSuffix()}")
			val attempted = LoginId("ghost-${uniqueSuffix()}")
			recorder.append(
				InternalLoginAudit(
					id = auditId,
					internalUserId = null,
					attemptedLoginId = attempted,
					outcome = InternalLoginOutcome.INVALID_CREDENTIALS,
					clientIp = null,
					occurredAt = NOW,
				),
			)

			val entry = projection.findRecent(500).first { it.auditId == auditId }

			entry.internalUserId.shouldBeNull()
			entry.userName.shouldBeNull()
			entry.attemptedLoginId shouldBe attempted.value
			entry.clientIp.shouldBeNull()
		}

		test("findRecent orders by occurredAt descending") {
			val user = savedInternalUser()
			val older = InternalLoginAuditId("ila_${uniqueSuffix()}")
			val newer = InternalLoginAuditId("ila_${uniqueSuffix()}")
			recorder.append(audit(older, user.id, user.loginId, NOW.minusSeconds(3_600)))
			recorder.append(audit(newer, user.id, user.loginId, NOW))

			val ids = projection.findRecent(500).map { it.auditId }
			val olderIndex = ids.indexOf(older)
			val newerIndex = ids.indexOf(newer)

			(newerIndex >= 0 && olderIndex >= 0) shouldBe true
			(newerIndex < olderIndex) shouldBe true
		}
	})

private fun audit(
	id: InternalLoginAuditId,
	userId: InternalUserId,
	loginId: LoginId,
	occurredAt: Instant,
): InternalLoginAudit =
	InternalLoginAudit(
		id = id,
		internalUserId = userId,
		attemptedLoginId = loginId,
		outcome = InternalLoginOutcome.SUCCESS,
		clientIp = null,
		occurredAt = occurredAt,
	)
