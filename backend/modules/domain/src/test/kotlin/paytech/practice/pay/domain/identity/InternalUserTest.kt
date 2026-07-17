package paytech.practice.pay.domain.identity

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

private val CREATED_AT: Instant = Instant.parse("2026-07-17T00:00:00Z")

private fun bootstrapSuperAdmin(): InternalUser =
	InternalUser.bootstrap(
		id = InternalUserId("iu_test_001"),
		loginId = LoginId("super_admin"),
		email = Email("super_admin@example.com"),
		userName = "최초 관리자",
		passwordHash = "hashed-password",
		createdAt = CREATED_AT,
	)

private fun invitedOperator(): InternalUser =
	InternalUser.invite(
		id = InternalUserId("iu_test_002"),
		loginId = LoginId("operator01"),
		email = Email("operator01@example.com"),
		userName = "운영자",
		role = InternalUserRole.OPERATOR,
		createdByInternalUserId = InternalUserId("iu_test_001"),
		createdAt = CREATED_AT,
	)

class InternalUserTest :
	FunSpec({

		test("bootstrap creates an ACTIVE SUPER_ADMIN with no creator") {
			val superAdmin = bootstrapSuperAdmin()

			superAdmin.status shouldBe AccountStatus.ACTIVE
			superAdmin.role shouldBe InternalUserRole.SUPER_ADMIN
			superAdmin.createdByInternalUserId.shouldBeNull()
			superAdmin.passwordHash shouldBe "hashed-password"
		}

		test("invite creates an INVITED account with no password and a creator") {
			val operator = invitedOperator()

			operator.status shouldBe AccountStatus.INVITED
			operator.passwordHash.shouldBeNull()
			operator.createdByInternalUserId shouldBe InternalUserId("iu_test_001")
		}

		test("activate moves INVITED to ACTIVE and sets the password hash") {
			val operator = invitedOperator()
			val activatedAt = CREATED_AT.plusSeconds(1)

			operator.activate("hashed-password", activatedAt)

			operator.status shouldBe AccountStatus.ACTIVE
			operator.passwordHash shouldBe "hashed-password"
			operator.activatedAt shouldBe activatedAt
		}

		test("activate fails when not INVITED") {
			val superAdmin = bootstrapSuperAdmin()

			shouldThrow<IllegalStateException> { superAdmin.activate("x", CREATED_AT.plusSeconds(1)) }
		}

		test("recordFailedLogin increments the failure count") {
			val superAdmin = bootstrapSuperAdmin()

			superAdmin.recordFailedLogin(CREATED_AT.plusSeconds(1))
			superAdmin.recordFailedLogin(CREATED_AT.plusSeconds(2))

			superAdmin.failedLoginCount shouldBe 2
		}

		test("recordSuccessfulLogin resets the failure count and records lastLoginAt") {
			val superAdmin = bootstrapSuperAdmin()
			superAdmin.recordFailedLogin(CREATED_AT.plusSeconds(1))
			val loginAt = CREATED_AT.plusSeconds(2)

			superAdmin.recordSuccessfulLogin(loginAt)

			superAdmin.failedLoginCount shouldBe 0
			superAdmin.lastLoginAt shouldBe loginAt
		}

		test("lock moves ACTIVE to LOCKED") {
			val superAdmin = bootstrapSuperAdmin()
			val lockedUntil = CREATED_AT.plusSeconds(600)

			superAdmin.lock(lockedUntil, CREATED_AT.plusSeconds(1))

			superAdmin.status shouldBe AccountStatus.LOCKED
			superAdmin.lockedUntil shouldBe lockedUntil
		}

		test("unlock moves LOCKED back to ACTIVE and clears the failure count") {
			val superAdmin = bootstrapSuperAdmin()
			superAdmin.recordFailedLogin(CREATED_AT.plusSeconds(1))
			superAdmin.lock(CREATED_AT.plusSeconds(600), CREATED_AT.plusSeconds(1))

			superAdmin.unlock(CREATED_AT.plusSeconds(2))

			superAdmin.status shouldBe AccountStatus.ACTIVE
			superAdmin.failedLoginCount shouldBe 0
			superAdmin.lockedUntil.shouldBeNull()
		}

		test("suspend and reactivate move between ACTIVE and SUSPENDED") {
			val superAdmin = bootstrapSuperAdmin()

			superAdmin.suspend(CREATED_AT.plusSeconds(1))
			superAdmin.status shouldBe AccountStatus.SUSPENDED

			superAdmin.reactivate(CREATED_AT.plusSeconds(2))
			superAdmin.status shouldBe AccountStatus.ACTIVE
		}

		test("terminate moves ACTIVE or SUSPENDED to TERMINATED") {
			val fromActive = bootstrapSuperAdmin()
			fromActive.terminate(CREATED_AT.plusSeconds(1))
			fromActive.status shouldBe AccountStatus.TERMINATED

			val fromSuspended = bootstrapSuperAdmin()
			fromSuspended.suspend(CREATED_AT.plusSeconds(1))
			fromSuspended.terminate(CREATED_AT.plusSeconds(2))
			fromSuspended.status shouldBe AccountStatus.TERMINATED
		}

		test("every transition fails once TERMINATED") {
			val superAdmin = bootstrapSuperAdmin()
			superAdmin.terminate(CREATED_AT.plusSeconds(1))

			shouldThrow<IllegalStateException> { superAdmin.suspend(CREATED_AT.plusSeconds(2)) }
			shouldThrow<IllegalStateException> { superAdmin.lock(CREATED_AT.plusSeconds(600), CREATED_AT.plusSeconds(2)) }
			shouldThrow<IllegalStateException> { superAdmin.terminate(CREATED_AT.plusSeconds(2)) }
		}

		test("reconstitute rejects ACTIVE without a passwordHash") {
			shouldThrow<IllegalArgumentException> {
				InternalUser.reconstitute(
					id = InternalUserId("iu_test_003"),
					loginId = LoginId("broken"),
					email = Email("broken@example.com"),
					userName = "broken",
					role = InternalUserRole.VIEWER,
					createdByInternalUserId = InternalUserId("iu_test_001"),
					createdAt = CREATED_AT,
					status = AccountStatus.ACTIVE,
					passwordHash = null,
					failedLoginCount = 0,
					lockedUntil = null,
					passwordChangedAt = null,
					lastLoginAt = null,
					invitedAt = CREATED_AT,
					activatedAt = null,
					terminatedAt = null,
					updatedAt = CREATED_AT,
				)
			}
		}
	})
