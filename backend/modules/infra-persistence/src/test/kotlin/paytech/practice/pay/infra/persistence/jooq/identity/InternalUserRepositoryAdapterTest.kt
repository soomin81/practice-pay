package paytech.practice.pay.infra.persistence.jooq.identity

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")

class InternalUserRepositoryAdapterTest :
	FunSpec({
		val adapter = InternalUserRepositoryAdapter(PersistenceTestSupport.dsl)

		test("save inserts a new InternalUser and findByLoginId round-trips it") {
			val loginId = LoginId("admin-${uniqueSuffix()}")
			val user =
				InternalUser.bootstrap(
					id = InternalUserId("iu_${uniqueSuffix()}"),
					loginId = loginId,
					email = Email("${uniqueSuffix()}@example.com"),
					userName = "테스트 관리자",
					passwordHash = "hashed-password",
					createdAt = NOW,
				)

			adapter.save(user)
			val found = adapter.findByLoginId(loginId)

			found.shouldNotBeNull()
			found.id shouldBe user.id
			found.status shouldBe AccountStatus.ACTIVE
			found.failedLoginCount shouldBe 0
		}

		test("save persists a recorded failed login on an existing InternalUser") {
			val loginId = LoginId("admin-${uniqueSuffix()}")
			val user =
				InternalUser.bootstrap(
					id = InternalUserId("iu_${uniqueSuffix()}"),
					loginId = loginId,
					email = Email("${uniqueSuffix()}@example.com"),
					userName = "테스트 관리자",
					passwordHash = "hashed-password",
					createdAt = NOW,
				)
			adapter.save(user)

			user.recordFailedLogin(NOW.plusSeconds(1))
			adapter.save(user)

			val found = adapter.findByLoginId(loginId)
			found.shouldNotBeNull()
			found.failedLoginCount shouldBe 1
		}

		test("findByLoginId returns null when no such login id exists") {
			adapter.findByLoginId(LoginId("no-such-login-id")).shouldBeNull()
		}

		test("save inserts a new InternalUser and findByEmail round-trips it") {
			val email = Email("${uniqueSuffix()}@example.com")
			val user =
				InternalUser.bootstrap(
					id = InternalUserId("iu_${uniqueSuffix()}"),
					loginId = LoginId("admin-${uniqueSuffix()}"),
					email = email,
					userName = "테스트 관리자",
					passwordHash = "hashed-password",
					createdAt = NOW,
				)

			adapter.save(user)
			val found = adapter.findByEmail(email)

			found.shouldNotBeNull()
			found.id shouldBe user.id
		}

		test("findByEmail returns null when no such email exists") {
			adapter.findByEmail(Email("no-such-${uniqueSuffix()}@example.com")).shouldBeNull()
		}
	})
