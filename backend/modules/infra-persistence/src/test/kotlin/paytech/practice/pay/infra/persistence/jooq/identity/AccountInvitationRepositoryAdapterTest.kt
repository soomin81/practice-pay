package paytech.practice.pay.infra.persistence.jooq.identity

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import paytech.practice.pay.dbcore.jooq.tables.AccountInvitation.Companion.ACCOUNT_INVITATION
import paytech.practice.pay.domain.identity.AccountInvitation
import paytech.practice.pay.domain.identity.AccountInvitationId
import paytech.practice.pay.domain.identity.AccountInvitationStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime
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

class AccountInvitationRepositoryAdapterTest :
	FunSpec({
		val adapter = AccountInvitationRepositoryAdapter(PersistenceTestSupport.dsl)

		test("save inserts a new PENDING AccountInvitation") {
			val internalUser = savedInternalUser()
			val tokenHash = "hashed-token-${uniqueSuffix()}"
			val invitation =
				AccountInvitation.forInternalUser(
					id = AccountInvitationId("ai_${uniqueSuffix()}"),
					internalUserId = internalUser.id,
					tokenHash = tokenHash,
					expiresAt = NOW.plusSeconds(604_800),
					createdAt = NOW,
				)

			adapter.save(invitation)

			val row =
				PersistenceTestSupport.dsl
					.selectFrom(ACCOUNT_INVITATION)
					.where(ACCOUNT_INVITATION.ACCOUNT_INVITATION_ID.eq(invitation.id.value))
					.fetchOne()

			row?.invitationStatus shouldBe "PENDING"
			row?.tokenHash shouldBe tokenHash
			row?.acceptedAt shouldBe null
		}

		test("save persists an accepted AccountInvitation via the update path") {
			val internalUser = savedInternalUser()
			val invitation =
				AccountInvitation.forInternalUser(
					id = AccountInvitationId("ai_${uniqueSuffix()}"),
					internalUserId = internalUser.id,
					tokenHash = "hashed-token-${uniqueSuffix()}",
					expiresAt = NOW.plusSeconds(604_800),
					createdAt = NOW,
				)
			adapter.save(invitation)

			invitation.accept(NOW.plusSeconds(60))
			adapter.save(invitation)

			val row =
				PersistenceTestSupport.dsl
					.selectFrom(ACCOUNT_INVITATION)
					.where(ACCOUNT_INVITATION.ACCOUNT_INVITATION_ID.eq(invitation.id.value))
					.fetchOne()

			row?.invitationStatus shouldBe AccountInvitationStatus.ACCEPTED.name
			row?.acceptedAt shouldBe NOW.plusSeconds(60).toUtcLocalDateTime()
		}
	})
