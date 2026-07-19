package paytech.practice.pay.infra.persistence.jooq.apikey

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import paytech.practice.pay.application.port.outbound.MerchantApiKeyListProjection
import paytech.practice.pay.application.port.outbound.MerchantApiKeySummary
import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT
import paytech.practice.pay.dbcore.jooq.tables.MerchantApiKey.Companion.MERCHANT_API_KEY
import paytech.practice.pay.dbcore.jooq.tables.MerchantApiKeyScope.Companion.MERCHANT_API_KEY_SCOPE
import paytech.practice.pay.domain.apikey.ApiEnvironment
import paytech.practice.pay.domain.apikey.ApiKeyPrefix
import paytech.practice.pay.domain.apikey.ApiKeyScope
import paytech.practice.pay.domain.apikey.ApiKeyStatus
import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.infra.persistence.jooq.toUtcInstant

/**
 * jOOQ로 [MerchantApiKeyListProjection] Port를 구현한다.
 *
 * [MerchantApiKeyRepositoryAdapter]와 같은 두 테이블(`merchant_api_key` +
 * `merchant_api_key_scope`)을 보지만, [MerchantApiKeyListProjection]의 KDoc에
 * 적힌 것과 같은 이유로 별도 클래스로 둔다 — `secretHash`/`hashAlgorithm`은
 * 애초에 조회하지도 않는다.
 *
 * **알려진 단순화: Scope 조회가 N+1이다.** 목록의 각 Key마다
 * `findScopes(merchantApiKeySeq)`를 따로 호출한다 — `MerchantApiKeyRepositoryAdapter`가
 * 단건 조회에서 이미 쓰는 것과 같은 방식을 그대로 재사용했다. 가맹점당 API
 * Key 개수가 많지 않은 MVP 데이터량에서는 문제없다고 판단했다(`PaymentRepository.findPendingExchangeSettlement`가
 * 크로스 애그리게이트 풀스캔을 허용한 것과 같은 성격의 트레이드오프,
 * `backend/CLAUDE.md`의 "Fake Exchange 매도 Use Case" 절 참고) — Key가 많아지면
 * `GROUP_CONCAT`이나 한 번의 Join 쿼리로 바꾼다.
 */
@Repository
class MerchantApiKeyListProjectionAdapter(
	private val dsl: DSLContext,
) : MerchantApiKeyListProjection {
	override fun findByMerchantId(merchantId: MerchantId): List<MerchantApiKeySummary> {
		val merchantSeq =
			dsl
				.select(MERCHANT.MERCHANT_SEQ)
				.from(MERCHANT)
				.where(MERCHANT.MERCHANT_ID.eq(merchantId.value))
				.fetchOne(MERCHANT.MERCHANT_SEQ)
				?: return emptyList()

		return dsl
			.select(
				MERCHANT_API_KEY.MERCHANT_API_KEY_SEQ,
				MERCHANT_API_KEY.MERCHANT_API_KEY_ID,
				MERCHANT_API_KEY.KEY_NAME,
				MERCHANT_API_KEY.API_ENVIRONMENT,
				MERCHANT_API_KEY.KEY_PREFIX,
				MERCHANT_API_KEY.API_KEY_STATUS,
				MERCHANT_API_KEY.CREATED_AT,
				MERCHANT_API_KEY.LAST_USED_AT,
				MERCHANT_API_KEY.REVOKED_AT,
			).from(MERCHANT_API_KEY)
			.where(MERCHANT_API_KEY.MERCHANT_SEQ.eq(merchantSeq))
			.orderBy(MERCHANT_API_KEY.CREATED_AT.desc())
			.fetch { record ->
				MerchantApiKeySummary(
					merchantApiKeyId = MerchantApiKeyId(record.get(MERCHANT_API_KEY.MERCHANT_API_KEY_ID)!!),
					keyName = record.get(MERCHANT_API_KEY.KEY_NAME)!!,
					environment = ApiEnvironment.valueOf(record.get(MERCHANT_API_KEY.API_ENVIRONMENT)!!),
					keyPrefix = ApiKeyPrefix(record.get(MERCHANT_API_KEY.KEY_PREFIX)!!),
					scopes = findScopes(record.get(MERCHANT_API_KEY.MERCHANT_API_KEY_SEQ)!!),
					status = ApiKeyStatus.valueOf(record.get(MERCHANT_API_KEY.API_KEY_STATUS)!!),
					createdAt = record.get(MERCHANT_API_KEY.CREATED_AT)!!.toUtcInstant(),
					lastUsedAt = record.get(MERCHANT_API_KEY.LAST_USED_AT)?.toUtcInstant(),
					revokedAt = record.get(MERCHANT_API_KEY.REVOKED_AT)?.toUtcInstant(),
				)
			}
	}

	private fun findScopes(merchantApiKeySeq: Long): Set<ApiKeyScope> =
		dsl
			.select(MERCHANT_API_KEY_SCOPE.SCOPE_CODE)
			.from(MERCHANT_API_KEY_SCOPE)
			.where(MERCHANT_API_KEY_SCOPE.MERCHANT_API_KEY_SEQ.eq(merchantApiKeySeq))
			.fetch(MERCHANT_API_KEY_SCOPE.SCOPE_CODE)
			.filterNotNull()
			.map { ApiKeyScope.valueOf(it) }
			.toSet()
}
