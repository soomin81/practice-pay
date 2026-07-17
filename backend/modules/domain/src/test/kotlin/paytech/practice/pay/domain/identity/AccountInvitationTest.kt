package paytech.practice.pay.domain.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.temporal.ChronoUnit

private val CREATED_AT: Instant = Instant.parse("2026-07-17T00:00:00Z")
private val EXPIRES_AT: Instant = CREATED_AT.plus(1, ChronoUnit.DAYS)

private fun newInternalUserInvitation(): AccountInvitation = AccountInvitation.forInternalUser(
	id = AccountInvitationId("inv_test_001"),
	internalUserId = InternalUserId("iu_test_001"),
	tokenHash = "hashed-token",
	expiresAt = EXPIRES_AT,
	createdAt = CREATED_AT,
)

class AccountInvitationTest : FunSpec({

	test("forInternalUser creates a PENDING invitation targeting only an internal user") {
		val invitation = newInternalUserInvitation()

		invitation.status shouldBe AccountInvitationStatus.PENDING
		invitation.accountType shouldBe InvitationAccountType.INTERNAL_USER
		invitation.internalUserId shouldBe InternalUserId("iu_test_001")
		invitation.merchantUserId.shouldBeNull()
	}

	test("forMerchantUser creates a PENDING invitation targeting only a merchant user") {
		val invitation = AccountInvitation.forMerchantUser(
			id = AccountInvitationId("inv_test_002"),
			merchantUserId = MerchantUserId("mu_test_001"),
			tokenHash = "hashed-token",
			expiresAt = EXPIRES_AT,
			createdAt = CREATED_AT,
		)

		invitation.accountType shouldBe InvitationAccountType.MERCHANT_USER
		invitation.merchantUserId shouldBe MerchantUserId("mu_test_001")
		invitation.internalUserId.shouldBeNull()
	}

	test("create rejects a blank tokenHash") {
		shouldThrow<IllegalArgumentException> {
			AccountInvitation.forInternalUser(
				id = AccountInvitationId("inv_test_003"),
				internalUserId = InternalUserId("iu_test_001"),
				tokenHash = "   ",
				expiresAt = EXPIRES_AT,
				createdAt = CREATED_AT,
			)
		}
	}

	test("accept moves PENDING to ACCEPTED and records acceptedAt") {
		val invitation = newInternalUserInvitation()
		val acceptedAt = CREATED_AT.plusSeconds(1)

		invitation.accept(acceptedAt)

		invitation.status shouldBe AccountInvitationStatus.ACCEPTED
		invitation.acceptedAt shouldBe acceptedAt
	}

	test("accept fails when not PENDING") {
		val invitation = newInternalUserInvitation()
		invitation.accept(CREATED_AT.plusSeconds(1))

		shouldThrow<IllegalStateException> { invitation.accept(CREATED_AT.plusSeconds(2)) }
	}

	test("expire moves PENDING to EXPIRED") {
		val invitation = newInternalUserInvitation()

		invitation.expire()

		invitation.status shouldBe AccountInvitationStatus.EXPIRED
	}

	test("revoke moves PENDING to REVOKED") {
		val invitation = newInternalUserInvitation()

		invitation.revoke()

		invitation.status shouldBe AccountInvitationStatus.REVOKED
	}

	test("expire and revoke fail once ACCEPTED") {
		val invitation = newInternalUserInvitation()
		invitation.accept(CREATED_AT.plusSeconds(1))

		shouldThrow<IllegalStateException> { invitation.expire() }
		shouldThrow<IllegalStateException> { invitation.revoke() }
	}

	test("reconstitute rejects an account type/target mismatch") {
		shouldThrow<IllegalArgumentException> {
			AccountInvitation.reconstitute(
				id = AccountInvitationId("inv_test_004"),
				accountType = InvitationAccountType.INTERNAL_USER,
				internalUserId = null,
				merchantUserId = MerchantUserId("mu_test_001"),
				tokenHash = "hashed-token",
				expiresAt = EXPIRES_AT,
				createdAt = CREATED_AT,
				status = AccountInvitationStatus.PENDING,
				acceptedAt = null,
			)
		}
	}

	test("reconstitute rejects ACCEPTED without acceptedAt") {
		shouldThrow<IllegalArgumentException> {
			AccountInvitation.reconstitute(
				id = AccountInvitationId("inv_test_005"),
				accountType = InvitationAccountType.INTERNAL_USER,
				internalUserId = InternalUserId("iu_test_001"),
				merchantUserId = null,
				tokenHash = "hashed-token",
				expiresAt = EXPIRES_AT,
				createdAt = CREATED_AT,
				status = AccountInvitationStatus.ACCEPTED,
				acceptedAt = null,
			)
		}
	}
})
