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
import paytech.practice.pay.domain.identity.MerchantUser
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.identity.MerchantUserRole
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")

/** `merchant_user.invited_by_internal_user_seq`의 FK 제약을 만족시키기 위해 실제 InternalUser 행을 심는다. */
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

class MerchantUserRepositoryAdapterTest :
	FunSpec({
		val adapter = MerchantUserRepositoryAdapter(PersistenceTestSupport.dsl)

		test("save inserts a new MerchantUser and findByMerchantIdAndLoginId round-trips it") {
			val merchantId = MerchantId(insertTestMerchant())
			val loginId = LoginId("owner-${uniqueSuffix()}")
			val user =
				MerchantUser.inviteInitialOwner(
					id = MerchantUserId("mu_${uniqueSuffix()}"),
					merchantId = merchantId,
					loginId = loginId,
					email = Email("${uniqueSuffix()}@example.com"),
					userName = "테스트 오너",
					invitedByInternalUserId = insertTestInternalUser(),
					createdAt = NOW,
				)

			adapter.save(user)
			val found = adapter.findByMerchantIdAndLoginId(merchantId, loginId)

			found.shouldNotBeNull()
			found.id shouldBe user.id
			found.status shouldBe AccountStatus.INVITED
		}

		test("save persists an activation on an existing MerchantUser") {
			val merchantId = MerchantId(insertTestMerchant())
			val loginId = LoginId("owner-${uniqueSuffix()}")
			val user =
				MerchantUser.inviteInitialOwner(
					id = MerchantUserId("mu_${uniqueSuffix()}"),
					merchantId = merchantId,
					loginId = loginId,
					email = Email("${uniqueSuffix()}@example.com"),
					userName = "테스트 오너",
					invitedByInternalUserId = insertTestInternalUser(),
					createdAt = NOW,
				)
			adapter.save(user)

			user.activate("hashed-password", NOW.plusSeconds(1))
			adapter.save(user)

			val found = adapter.findByMerchantIdAndLoginId(merchantId, loginId)
			found.shouldNotBeNull()
			found.status shouldBe AccountStatus.ACTIVE
		}

		test("findByMerchantIdAndLoginId returns null when no such login id exists for the merchant") {
			val merchantId = MerchantId(insertTestMerchant())

			adapter.findByMerchantIdAndLoginId(merchantId, LoginId("no-such-login-id")).shouldBeNull()
		}

		test("save inserts a new MerchantUser and findByMerchantIdAndEmail round-trips it") {
			val merchantId = MerchantId(insertTestMerchant())
			val email = Email("${uniqueSuffix()}@example.com")
			val user =
				MerchantUser.inviteInitialOwner(
					id = MerchantUserId("mu_${uniqueSuffix()}"),
					merchantId = merchantId,
					loginId = LoginId("owner-${uniqueSuffix()}"),
					email = email,
					userName = "테스트 오너",
					invitedByInternalUserId = insertTestInternalUser(),
					createdAt = NOW,
				)
			adapter.save(user)

			val found = adapter.findByMerchantIdAndEmail(merchantId, email)

			found.shouldNotBeNull()
			found.id shouldBe user.id
		}

		test("findByMerchantIdAndEmail returns null when no such email exists for the merchant") {
			val merchantId = MerchantId(insertTestMerchant())

			adapter.findByMerchantIdAndEmail(merchantId, Email("no-such-email@example.com")).shouldBeNull()
		}

		test("save inserts a new MerchantUser and findById round-trips it, resolving merchantId from the row") {
			val merchantId = MerchantId(insertTestMerchant())
			val id = MerchantUserId("mu_${uniqueSuffix()}")
			val user =
				MerchantUser.inviteInitialOwner(
					id = id,
					merchantId = merchantId,
					loginId = LoginId("owner-${uniqueSuffix()}"),
					email = Email("${uniqueSuffix()}@example.com"),
					userName = "테스트 오너",
					invitedByInternalUserId = insertTestInternalUser(),
					createdAt = NOW,
				)
			adapter.save(user)

			val found = adapter.findById(id)

			found.shouldNotBeNull()
			found.id shouldBe id
			found.merchantId shouldBe merchantId
		}

		test("findById returns null when no such id exists") {
			adapter.findById(MerchantUserId("mu_no-such-id")).shouldBeNull()
		}

		test("countActiveOwners counts only ACTIVE OWNERs of that merchant") {
			// "최소 하나의 활성 OWNER를 유지한다" 불변식이 기대는 집계다.
			val merchantId = MerchantId(insertTestMerchant())
			val internalUserId = insertTestInternalUser()

			fun owner(activated: Boolean) =
				MerchantUser
					.inviteInitialOwner(
						id = MerchantUserId("mu_${uniqueSuffix()}"),
						merchantId = merchantId,
						loginId = LoginId("owner-${uniqueSuffix()}"),
						email = Email("${uniqueSuffix()}@example.com"),
						userName = "테스트 오너",
						invitedByInternalUserId = internalUserId,
						createdAt = NOW,
					).apply { if (activated) activate("hashed-password", NOW) }

			val activeOwner = owner(activated = true)
			adapter.save(activeOwner)
			adapter.save(owner(activated = false)) // INVITED — 세지 않는다
			adapter.save(
				MerchantUser
					.inviteSubAccount(
						id = MerchantUserId("mu_${uniqueSuffix()}"),
						merchantId = merchantId,
						loginId = LoginId("admin-${uniqueSuffix()}"),
						email = Email("${uniqueSuffix()}@example.com"),
						userName = "테스트 어드민",
						role = MerchantUserRole.ADMIN,
						invitedByMerchantUserId = activeOwner.id,
						createdAt = NOW,
					).apply { activate("hashed-password", NOW) }, // ACTIVE지만 OWNER가 아니다
			)

			adapter.countActiveOwners(merchantId) shouldBe 1

			// 정지되면 활성 OWNER에서 빠진다.
			activeOwner.suspend(NOW.plusSeconds(60))
			adapter.save(activeOwner)
			adapter.countActiveOwners(merchantId) shouldBe 0
		}

		test("save persists a changed role (regression: the UPDATE used to omit role_code)") {
			// 이 어댑터가 쓰일 당시 role이 val이라 UPDATE 목록에 role_code가 없었고,
			// changeRole()이 생기면서 조용한 데이터 유실이 됐다 — API는 새 역할을 돌려주는데
			// DB는 옛 역할 그대로였다. Mock을 쓰는 단위 테스트로는 잡히지 않는 층이라
			// 실물 검증에서 드러났고, 그 회귀를 여기 고정한다.
			val merchantId = MerchantId(insertTestMerchant())
			val owner =
				MerchantUser
					.inviteInitialOwner(
						id = MerchantUserId("mu_${uniqueSuffix()}"),
						merchantId = merchantId,
						loginId = LoginId("owner-${uniqueSuffix()}"),
						email = Email("${uniqueSuffix()}@example.com"),
						userName = "테스트 오너",
						invitedByInternalUserId = insertTestInternalUser(),
						createdAt = NOW,
					).apply { activate("hashed-password", NOW) }
			adapter.save(owner)

			owner.changeRole(MerchantUserRole.VIEWER, NOW.plusSeconds(60))
			adapter.save(owner)

			adapter.findById(owner.id)!!.role shouldBe MerchantUserRole.VIEWER
		}

		test("countActiveOwners returns 0 for an unknown merchant") {
			adapter.countActiveOwners(MerchantId("mrc_no_such_merchant")) shouldBe 0
		}
	})
