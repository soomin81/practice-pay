package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.apikey.ApiEnvironment
import paytech.practice.pay.domain.apikey.ApiKeyPrefix
import paytech.practice.pay.domain.apikey.ApiKeyScope
import paytech.practice.pay.domain.apikey.ApiKeyStatus
import paytech.practice.pay.domain.apikey.MerchantApiKeyId
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Instant

/**
 * 가맹점 API Key 목록 조회를 위한 전용 jOOQ Projection Outbound Port다.
 *
 * [MerchantListProjection]과 같은 이유로 [MerchantApiKeyRepository](Command
 * Repository)에 `findAll`류 메서드를 추가하지 않는다(`docs/architecture/persistence-jooq.md`의
 * "복잡한 조회는 전용 jOOQ Projection을 사용한다"). [MerchantApiKeySummary]는
 * `secretHash`를 포함하지 않는다 — 목록 화면이 원문은커녕 해시조차 보여줄 이유가
 * 없다(`docs/architecture/identity-access-api-key.md`의 "6.4 저장 정책": "저장
 * 금지: 전체 API Key 원문... 로그의 Authorization Header 원문"과 같은 정신 —
 * 응답에 실을 값이면 로그에도 남을 수 있다는 뜻이라, 애초에 값 자체를 옮기지
 * 않는다).
 */
fun interface MerchantApiKeyListProjection {
	/** 주어진 가맹점의 모든 API Key를 최신 발급순(`created_at DESC`)으로 돌려준다. */
	fun findByMerchantId(merchantId: MerchantId): List<MerchantApiKeySummary>
}

/** [MerchantApiKeyListProjection]이 돌려주는 목록 조회 전용 읽기 모델이다. */
data class MerchantApiKeySummary(
	val merchantApiKeyId: MerchantApiKeyId,
	val keyName: String,
	val environment: ApiEnvironment,
	val keyPrefix: ApiKeyPrefix,
	val scopes: Set<ApiKeyScope>,
	val status: ApiKeyStatus,
	val createdAt: Instant,
	val lastUsedAt: Instant?,
	val revokedAt: Instant?,
)
