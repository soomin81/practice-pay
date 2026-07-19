package paytech.practice.pay.infra.persistence.jooq.apikey

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.MerchantApiKeyRepository
import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT
import paytech.practice.pay.dbcore.jooq.tables.MerchantApiKey.Companion.MERCHANT_API_KEY
import paytech.practice.pay.dbcore.jooq.tables.MerchantApiKeyScope.Companion.MERCHANT_API_KEY_SCOPE
import paytech.practice.pay.dbcore.jooq.tables.MerchantUser.Companion.MERCHANT_USER
import paytech.practice.pay.dbcore.jooq.tables.records.MerchantApiKeyRecord
import paytech.practice.pay.domain.apikey.ApiEnvironment
import paytech.practice.pay.domain.apikey.ApiKeyPrefix
import paytech.practice.pay.domain.apikey.ApiKeyScope
import paytech.practice.pay.domain.apikey.ApiKeyStatus
import paytech.practice.pay.domain.apikey.MerchantApiKey
import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant
import paytech.practice.pay.infra.persistence.jooq.toUtcLocalDateTime

/**
 * jOOQ로 [MerchantApiKeyRepository] Port를 구현한다.
 *
 * 이 프로젝트의 다른 Repository Adapter와 달리, [MerchantApiKey.scopes]가
 * 자식 테이블(`merchant_api_key_scope`, 복합 PK, 자기 생명주기가 없는 값
 * 컬렉션 — `MerchantApiKey`의 KDoc 참고)에 저장된다. 도메인에 Scope를
 * 바꾸는 메서드가 없으므로(발급 시 정해지면 끝) Scope 행은 최초 INSERT
 * 때만 쓰고, UPDATE 경로(`revoke`/`expire`/`recordUsage`)는 건드리지 않는다.
 *
 * `save`의 낙관적 잠금 한계는 [paytech.practice.pay.infra.persistence.jooq.payment.PaymentRepositoryAdapter]와
 * 동일하다.
 */
@Repository
class MerchantApiKeyRepositoryAdapter(
	private val dsl: DSLContext,
) : MerchantApiKeyRepository {
	override fun save(merchantApiKey: MerchantApiKey) {
		val existing =
			dsl
				.selectFrom(MERCHANT_API_KEY)
				.where(MERCHANT_API_KEY.MERCHANT_API_KEY_ID.eq(merchantApiKey.id.value))
				.fetchOne()

		if (existing == null) {
			val record =
				dsl
					.newRecord(MERCHANT_API_KEY)
					.apply {
						fillFrom(merchantApiKey)
						version = 0L
					}
			record.insert()
			merchantApiKey.scopes.forEach { scope ->
				dsl
					.newRecord(MERCHANT_API_KEY_SCOPE)
					.apply {
						merchantApiKeySeq = record.merchantApiKeySeq
						scopeCode = scope.name
						createdAt = merchantApiKey.createdAt.toUtcLocalDateTime()
					}.insert()
			}
		} else {
			dsl
				.update(MERCHANT_API_KEY)
				.set(MERCHANT_API_KEY.API_KEY_STATUS, merchantApiKey.status.name)
				.set(MERCHANT_API_KEY.LAST_USED_AT, merchantApiKey.lastUsedAt?.toUtcLocalDateTime())
				.set(
					MERCHANT_API_KEY.REVOKED_BY_MERCHANT_USER_SEQ,
					merchantApiKey.revokedByMerchantUserId?.let { resolveMerchantUserSeq(it) },
				).set(MERCHANT_API_KEY.REVOKED_AT, merchantApiKey.revokedAt?.toUtcLocalDateTime())
				.set(MERCHANT_API_KEY.UPDATED_AT, merchantApiKey.updatedAt.toUtcLocalDateTime())
				.set(MERCHANT_API_KEY.VERSION, (existing.version ?: 0L) + 1)
				.where(MERCHANT_API_KEY.MERCHANT_API_KEY_SEQ.eq(existing.merchantApiKeySeq))
				.and(MERCHANT_API_KEY.VERSION.eq(existing.version))
				.execute()
				.also { updatedRows ->
					check(updatedRows == 1) {
						"MerchantApiKey(${merchantApiKey.id.value}) 저장에 실패했습니다 — " +
							"동시에 변경된 것으로 보입니다(예상 version=${existing.version})."
					}
				}
		}
	}

	override fun findByPrefix(keyPrefix: ApiKeyPrefix): MerchantApiKey? =
		dsl
			.selectFrom(MERCHANT_API_KEY)
			.where(MERCHANT_API_KEY.KEY_PREFIX.eq(keyPrefix.value))
			.fetchOne()
			?.toDomain()

	override fun findById(merchantApiKeyId: MerchantApiKeyId): MerchantApiKey? =
		dsl
			.selectFrom(MERCHANT_API_KEY)
			.where(MERCHANT_API_KEY.MERCHANT_API_KEY_ID.eq(merchantApiKeyId.value))
			.fetchOne()
			?.toDomain()

	private fun resolveMerchantSeq(merchantId: MerchantId): Long =
		dsl
			.select(MERCHANT.MERCHANT_SEQ)
			.from(MERCHANT)
			.where(MERCHANT.MERCHANT_ID.eq(merchantId.value))
			.fetchOne(MERCHANT.MERCHANT_SEQ)
			?: error("Merchant(${merchantId.value})를 찾을 수 없습니다.")

	private fun resolveMerchantId(merchantSeq: Long): MerchantId =
		dsl
			.select(MERCHANT.MERCHANT_ID)
			.from(MERCHANT)
			.where(MERCHANT.MERCHANT_SEQ.eq(merchantSeq))
			.fetchOne(MERCHANT.MERCHANT_ID)
			?.let { MerchantId(it) }
			?: error("Merchant(seq=$merchantSeq)를 찾을 수 없습니다.")

	private fun resolveMerchantUserSeq(merchantUserId: MerchantUserId): Long =
		dsl
			.select(MERCHANT_USER.MERCHANT_USER_SEQ)
			.from(MERCHANT_USER)
			.where(MERCHANT_USER.MERCHANT_USER_ID.eq(merchantUserId.value))
			.fetchOne(MERCHANT_USER.MERCHANT_USER_SEQ)
			?: error("MerchantUser(${merchantUserId.value})를 찾을 수 없습니다.")

	private fun resolveMerchantUserId(merchantUserSeq: Long): MerchantUserId =
		dsl
			.select(MERCHANT_USER.MERCHANT_USER_ID)
			.from(MERCHANT_USER)
			.where(MERCHANT_USER.MERCHANT_USER_SEQ.eq(merchantUserSeq))
			.fetchOne(MERCHANT_USER.MERCHANT_USER_ID)
			?.let { MerchantUserId(it) }
			?: error("MerchantUser(seq=$merchantUserSeq)를 찾을 수 없습니다.")

	private fun findScopes(merchantApiKeySeq: Long): Set<ApiKeyScope> =
		dsl
			.select(MERCHANT_API_KEY_SCOPE.SCOPE_CODE)
			.from(MERCHANT_API_KEY_SCOPE)
			.where(MERCHANT_API_KEY_SCOPE.MERCHANT_API_KEY_SEQ.eq(merchantApiKeySeq))
			.fetch(MERCHANT_API_KEY_SCOPE.SCOPE_CODE)
			.filterNotNull()
			.map { ApiKeyScope.valueOf(it) }
			.toSet()

	private fun MerchantApiKeyRecord.fillFrom(merchantApiKey: MerchantApiKey) {
		merchantApiKeyId = merchantApiKey.id.value
		merchantSeq = resolveMerchantSeq(merchantApiKey.merchantId)
		keyName = merchantApiKey.keyName
		apiEnvironment = merchantApiKey.environment.name
		keyPrefix = merchantApiKey.keyPrefix.value
		secretHash = merchantApiKey.secretHash
		hashAlgorithm = merchantApiKey.hashAlgorithm
		apiKeyStatus = merchantApiKey.status.name
		expiresAt = merchantApiKey.expiresAt?.toUtcLocalDateTime()
		lastUsedAt = merchantApiKey.lastUsedAt?.toUtcLocalDateTime()
		createdByMerchantUserSeq = resolveMerchantUserSeq(merchantApiKey.createdByMerchantUserId)
		revokedByMerchantUserSeq = merchantApiKey.revokedByMerchantUserId?.let { resolveMerchantUserSeq(it) }
		revokedAt = merchantApiKey.revokedAt?.toUtcLocalDateTime()
		createdAt = merchantApiKey.createdAt.toUtcLocalDateTime()
		updatedAt = merchantApiKey.updatedAt.toUtcLocalDateTime()
	}

	private fun MerchantApiKeyRecord.toDomain(): MerchantApiKey =
		MerchantApiKey.reconstitute(
			id = MerchantApiKeyId(merchantApiKeyId!!),
			merchantId = resolveMerchantId(merchantSeq!!),
			keyName = keyName!!,
			environment = ApiEnvironment.valueOf(apiEnvironment!!),
			keyPrefix = ApiKeyPrefix(keyPrefix!!),
			secretHash = secretHash!!,
			hashAlgorithm = hashAlgorithm!!,
			scopes = findScopes(merchantApiKeySeq!!),
			createdByMerchantUserId = resolveMerchantUserId(createdByMerchantUserSeq!!),
			createdAt = createdAt!!.toUtcInstant(),
			status = ApiKeyStatus.valueOf(apiKeyStatus!!),
			expiresAt = expiresAt?.toUtcInstant(),
			lastUsedAt = lastUsedAt?.toUtcInstant(),
			revokedByMerchantUserId = revokedByMerchantUserSeq?.let { resolveMerchantUserId(it) },
			revokedAt = revokedAt?.toUtcInstant(),
			updatedAt = updatedAt!!.toUtcInstant(),
		)
}
