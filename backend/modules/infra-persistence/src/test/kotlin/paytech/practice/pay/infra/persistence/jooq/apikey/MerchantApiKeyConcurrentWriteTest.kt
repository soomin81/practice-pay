package paytech.practice.pay.infra.persistence.jooq.apikey

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import paytech.practice.pay.domain.apikey.ApiEnvironment
import paytech.practice.pay.domain.apikey.ApiKeyPrefix
import paytech.practice.pay.domain.apikey.ApiKeyScope
import paytech.practice.pay.domain.apikey.ApiKeyStatus
import paytech.practice.pay.domain.apikey.MerchantApiKey
import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import paytech.practice.pay.domain.identity.Email
import paytech.practice.pay.domain.identity.InternalUser
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.LoginId
import paytech.practice.pay.domain.identity.MerchantUser
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.infra.persistence.jooq.PersistenceTestSupport
import paytech.practice.pay.infra.persistence.jooq.TransactionManagerAdapter
import paytech.practice.pay.infra.persistence.jooq.identity.InternalUserRepositoryAdapter
import paytech.practice.pay.infra.persistence.jooq.identity.MerchantUserRepositoryAdapter
import paytech.practice.pay.infra.persistence.jooq.insertTestMerchant
import paytech.practice.pay.infra.persistence.jooq.uniqueSuffix
import java.time.Instant
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val NOW: Instant = Instant.parse("2026-07-17T00:00:00Z")

/**
 * **폐기가 되돌려지지 않는지** 고정한다 — 이 슬라이스에서 가장 보안에 가까운 회귀 테스트다.
 *
 * API Key 인증은 **매 결제 API 요청마다** `recordUsage()` 후 저장하는데, 그 UPDATE는 상태
 * 컬럼까지 자기 사본 값으로 쓴다. 그래서 관리자가 폐기(`ACTIVE` → `REVOKED`)하는 사이 in-flight
 * 인증이 저장되면 상태가 `ACTIVE`로 되돌아갈 수 있다 — 잠그지 않으면 폐기된 Key가 계속 통한다.
 *
 * `findByPrefixForUpdate` + 트랜잭션이면 두 흐름이 직렬화돼 **어느 순서로 겹치든 최종 상태는
 * `REVOKED`**다: 폐기가 먼저면 인증은 그 뒤에 사용 기록만 남기려다 이미 폐기된 Key를 보고,
 * 인증이 먼저면 폐기가 그 뒤에 적용된다.
 */
class MerchantApiKeyConcurrentWriteTest :
	FunSpec({
		val adapter = MerchantApiKeyRepositoryAdapter(PersistenceTestSupport.dsl)
		val transactionManager = TransactionManagerAdapter(PersistenceTestSupport.transactionManager)

		test("a concurrent authentication must not undo a revocation") {
			val merchantId = MerchantId(insertTestMerchant())
			val ownerId = savedOwner(merchantId)
			val prefix = ApiKeyPrefix("sk_test_${uniqueSuffix()}")
			adapter.save(
				MerchantApiKey.create(
					id = MerchantApiKeyId("mak_${uniqueSuffix()}"),
					merchantId = merchantId,
					keyName = "테스트 Key",
					environment = ApiEnvironment.TEST,
					keyPrefix = prefix,
					secretHash = "hashed-secret",
					hashAlgorithm = "HMAC-SHA256",
					scopes = setOf(ApiKeyScope.PAYMENT_CREATE),
					createdByMerchantUserId = ownerId,
					expiresAt = null,
					createdAt = NOW,
				),
			)

			val barrier = CyclicBarrier(2)
			val pool = Executors.newFixedThreadPool(2)

			fun lockedWrite(mutate: (MerchantApiKey) -> Unit): Runnable =
				Runnable {
					transactionManager.runInTransaction {
						// 잠금을 잡기 전에 두 스레드를 맞춘다(잠금 이후면 서로를 기다린다).
						barrier.await(10, TimeUnit.SECONDS)
						val key = adapter.findByPrefixForUpdate(prefix)!!
						runCatching { mutate(key) }.onSuccess { adapter.save(key) }
					}
				}

			// 인증(사용 기록)과 관리자의 폐기가 같은 순간에 같은 Key를 집는다.
			val authenticating = pool.submit(lockedWrite { it.recordUsage(NOW.plusSeconds(1)) })
			val revoking = pool.submit(lockedWrite { it.revoke(ownerId, NOW.plusSeconds(2)) })
			authenticating.get(30, TimeUnit.SECONDS)
			revoking.get(30, TimeUnit.SECONDS)
			pool.shutdown()

			adapter.findByPrefix(prefix)!!.status shouldBe ApiKeyStatus.REVOKED
		}
	})

/** API Key 발급자 FK를 만족시킬 최소 가맹점 OWNER 하나. */
private fun savedOwner(merchantId: MerchantId): MerchantUserId {
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
	val owner =
		MerchantUser.inviteInitialOwner(
			id = MerchantUserId("mu_${uniqueSuffix()}"),
			merchantId = merchantId,
			loginId = LoginId("owner-${uniqueSuffix()}"),
			email = Email("${uniqueSuffix()}@example.com"),
			userName = "테스트 오너",
			invitedByInternalUserId = internalUserId,
			createdAt = NOW,
		)
	MerchantUserRepositoryAdapter(PersistenceTestSupport.dsl).save(owner)
	return owner.id
}
