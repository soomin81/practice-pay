package paytech.practice.pay.infra.persistence.jooq.identity

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.identity.AccountStatus
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.InternalUserRole
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

		test("save inserts a new InternalUser and findById round-trips it") {
			val id = InternalUserId("iu_${uniqueSuffix()}")
			val user =
				InternalUser.bootstrap(
					id = id,
					loginId = LoginId("admin-${uniqueSuffix()}"),
					email = Email("${uniqueSuffix()}@example.com"),
					userName = "테스트 관리자",
					passwordHash = "hashed-password",
					createdAt = NOW,
				)

			adapter.save(user)
			val found = adapter.findById(id)

			found.shouldNotBeNull()
			found.id shouldBe id
		}

		test("findById returns null when no such id exists") {
			adapter.findById(InternalUserId("iu_no-such-id")).shouldBeNull()
		}

		test("save persists a changed role (the UPDATE must include role_code)") {
			// MerchantUserRepositoryAdapter가 실제로 겪은 버그와 같은 자리다 — role이 val이던
			// 시절 UPDATE에 role_code가 없었고, changeRole이 생기면 API는 200인데 DB는 옛 역할로
			// 남는 조용한 유실이 된다. 여기서는 재현 전에 막았고 이 테스트가 그것을 고정한다.
			val bootstrapped =
				InternalUser.bootstrap(
					id = InternalUserId("iu_${uniqueSuffix()}"),
					loginId = LoginId("boot-${uniqueSuffix()}"),
					email = Email("${uniqueSuffix()}@example.com"),
					userName = "부트스트랩 관리자",
					passwordHash = "hashed-password",
					createdAt = NOW,
				)
			val operator =
				InternalUser
					.invite(
						id = InternalUserId("iu_${uniqueSuffix()}"),
						loginId = LoginId("op-${uniqueSuffix()}"),
						email = Email("${uniqueSuffix()}@example.com"),
						userName = "테스트 운영자",
						role = InternalUserRole.OPERATOR,
						createdByInternalUserId = bootstrapped.id,
						createdAt = NOW,
					).apply { activate("hashed-password", NOW) }
			adapter.save(bootstrapped)
			adapter.save(operator)

			operator.changeRole(InternalUserRole.VIEWER, NOW.plusSeconds(60))
			adapter.save(operator)

			adapter.findById(operator.id)!!.role shouldBe InternalUserRole.VIEWER
		}

		test("countActiveSuperAdmins counts only ACTIVE SUPER_ADMINs") {
			// "최소 하나의 활성 SUPER_ADMIN" 불변식이 기대는 집계다. 이 테스트는 DB를 다른
			// 테스트와 공유하므로 절대 개수가 아니라 **증분**을 단언한다.
			val before = adapter.countActiveSuperAdmins()

			val activeSuperAdmin =
				InternalUser.bootstrap(
					id = InternalUserId("iu_${uniqueSuffix()}"),
					loginId = LoginId("boot-${uniqueSuffix()}"),
					email = Email("${uniqueSuffix()}@example.com"),
					userName = "활성 관리자",
					passwordHash = "hashed-password",
					createdAt = NOW,
				)
			adapter.save(activeSuperAdmin)
			adapter.countActiveSuperAdmins() shouldBe before + 1

			// INVITED인 OPERATOR는 세지 않는다(역할·상태 둘 다 조건이다).
			adapter.save(
				InternalUser.invite(
					id = InternalUserId("iu_${uniqueSuffix()}"),
					loginId = LoginId("op-${uniqueSuffix()}"),
					email = Email("${uniqueSuffix()}@example.com"),
					userName = "초대된 운영자",
					role = InternalUserRole.OPERATOR,
					createdByInternalUserId = activeSuperAdmin.id,
					createdAt = NOW,
				),
			)
			adapter.countActiveSuperAdmins() shouldBe before + 1

			// 정지되면 활성 SUPER_ADMIN에서 빠진다.
			activeSuperAdmin.suspend(NOW.plusSeconds(60))
			adapter.save(activeSuperAdmin)
			adapter.countActiveSuperAdmins() shouldBe before
		}
	})
