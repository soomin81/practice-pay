package paytech.practice.pay.infra.persistence.jooq.identity

import io.kotest.core.spec.style.FunSpec
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

private val NOW: Instant = Instant.parse("2026-07-19T00:00:00Z")

class InternalUserListProjectionAdapterTest :
	FunSpec({
		val repositoryAdapter = InternalUserRepositoryAdapter(PersistenceTestSupport.dsl)
		val projectionAdapter = InternalUserListProjectionAdapter(PersistenceTestSupport.dsl)

		test("findAll returns summaries including INVITED accounts, newest first") {
			// 이 테스트는 다른 테스트가 심은 내부 사용자와 한 DB를 공유하므로, 전체 개수를
			// 단언하지 않고 이번에 만든 두 건의 상대 순서와 값만 확인한다.
			val bootstrapped =
				InternalUser
					.bootstrap(
						id = InternalUserId("iu_${uniqueSuffix()}"),
						loginId = LoginId("boot-${uniqueSuffix()}"),
						email = Email("${uniqueSuffix()}@example.com"),
						userName = "부트스트랩 관리자",
						passwordHash = "hashed-password",
						createdAt = NOW.minusSeconds(3_600),
					)
			val invited =
				InternalUser.invite(
					id = InternalUserId("iu_${uniqueSuffix()}"),
					loginId = LoginId("invited-${uniqueSuffix()}"),
					email = Email("${uniqueSuffix()}@example.com"),
					userName = "초대된 운영자",
					role = InternalUserRole.OPERATOR,
					createdByInternalUserId = bootstrapped.id,
					createdAt = NOW.minusSeconds(60),
				)
			repositoryAdapter.save(bootstrapped)
			repositoryAdapter.save(invited)

			val byId = projectionAdapter.findAll().associateBy { it.internalUserId }

			byId.getValue(bootstrapped.id).status shouldBe AccountStatus.ACTIVE
			byId.getValue(bootstrapped.id).role shouldBe InternalUserRole.SUPER_ADMIN
			byId.getValue(invited.id).status shouldBe AccountStatus.INVITED
			byId.getValue(invited.id).role shouldBe InternalUserRole.OPERATOR
			// 아직 로그인한 적이 없으면 null이다 — 화면이 "—"로 그린다.
			byId.getValue(invited.id).lastLoginAt shouldBe null

			// created_at 내림차순: 나중에 만든 invited가 bootstrapped보다 앞에 온다.
			val ids = projectionAdapter.findAll().map { it.internalUserId }
			ids.indexOf(invited.id) shouldBe ids.indexOf(bootstrapped.id) - 1
		}
	})
