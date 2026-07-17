package paytech.practice.pay.domain.apikey

import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.merchant.MerchantId
import java.time.Instant

/**
 * 가맹점 API Key(MerchantApiKey) Aggregate Root다.
 *
 * 가맹점 서버가 스테이블코인 결제 시스템의 결제 API를 호출할 때 쓰는 서버 간
 * 인증 자격증명이며, `TEST`/`LIVE` 환경 구분, Secret Hash 검증 정보, 발급·폐기와
 * 마지막 사용 시각, API 호출 [scopes]를 관리한다
 * (`docs/architecture/identity-access-api-key.md`). 소유 주체는 사용자 계정이
 * 아니라 [Merchant][MerchantId]다 — 발급자·폐기자는 [MerchantUserId]로 감사
 * 정보만 남긴다. DB의 `merchant_api_key_scope` 테이블은 [scopes]로 표현한다 —
 * 자기 생명주기가 없는 이 Aggregate 소속의 값 컬렉션이라 별도 Aggregate로
 * 두지 않는다.
 *
 * API Key 원문(Secret)은 도메인 계층 어디에서도 다루지 않는다 — [secretHash]는
 * 이미 서버 측 Pepper와 함께 해시된 값이며, 해시 계산과 원문 최초 1회 표시는
 * 애플리케이션/어댑터 계층의 책임이다(`docs/architecture/persistence-jooq.md`의
 * "인증 정보 저장 규칙" 참고).
 *
 * 인스턴스는 [create]로 새로 만들거나 [reconstitute]로 저장된 값을 복원해서 얻는다.
 *
 * @see docs/domain/domain-model.md
 * @see docs/domain/state-transitions.md
 */
class MerchantApiKey private constructor(
	val id: MerchantApiKeyId,
	val merchantId: MerchantId,
	val keyName: String,
	val environment: ApiEnvironment,
	val keyPrefix: ApiKeyPrefix,
	val secretHash: String,
	val hashAlgorithm: String,
	val scopes: Set<ApiKeyScope>,
	val createdByMerchantUserId: MerchantUserId,
	val createdAt: Instant,
	status: ApiKeyStatus,
	expiresAt: Instant?,
	lastUsedAt: Instant?,
	revokedByMerchantUserId: MerchantUserId?,
	revokedAt: Instant?,
	updatedAt: Instant,
) {
	var status: ApiKeyStatus = status
		private set

	var expiresAt: Instant? = expiresAt
		private set

	var lastUsedAt: Instant? = lastUsedAt
		private set

	var revokedByMerchantUserId: MerchantUserId? = revokedByMerchantUserId
		private set

	/** Key가 `REVOKED`로 폐기된 시각. `REVOKED` 상태에서는 항상 값이 있다. */
	var revokedAt: Instant? = revokedAt
		private set

	var updatedAt: Instant = updatedAt
		private set

	init {
		require(keyName.isNotBlank()) { "keyName은 공백일 수 없습니다." }
		require(hashAlgorithm.isNotBlank()) { "hashAlgorithm은 공백일 수 없습니다." }
		require(secretHash.isNotBlank()) { "secretHash는 공백일 수 없습니다." }
		require(status != ApiKeyStatus.REVOKED || revokedAt != null) {
			"REVOKED 상태는 revokedAt이 반드시 있어야 합니다."
		}
	}

	/** `ACTIVE` 상태에서만 호출을 허용한다. */
	fun isUsable(): Boolean = status == ApiKeyStatus.ACTIVE

	/** 이 Key가 주어진 [scope]로 API를 호출할 수 있는지 확인한다. */
	fun hasScope(scope: ApiKeyScope): Boolean = scope in scopes

	/** 마지막 사용 시각을 기록한다. */
	fun recordUsage(usedAt: Instant) {
		check(isUsable()) { "ACTIVE 상태가 아니면 사용할 수 없습니다: 현재 상태=$status" }
		lastUsedAt = usedAt
		updatedAt = usedAt
	}

	/** `ACTIVE` → `REVOKED`. 관리자에 의한 즉시 폐기. */
	fun revoke(
		revokedByMerchantUserId: MerchantUserId,
		revokedAt: Instant,
	) {
		checkTransition(status == ApiKeyStatus.ACTIVE, ApiKeyStatus.REVOKED)
		status = ApiKeyStatus.REVOKED
		this.revokedByMerchantUserId = revokedByMerchantUserId
		this.revokedAt = revokedAt
		updatedAt = revokedAt
	}

	/** `ACTIVE` → `EXPIRED`. 만료 시각 경과. */
	fun expire(changedAt: Instant) {
		checkTransition(status == ApiKeyStatus.ACTIVE, ApiKeyStatus.EXPIRED)
		status = ApiKeyStatus.EXPIRED
		updatedAt = changedAt
	}

	private fun checkTransition(
		allowed: Boolean,
		target: ApiKeyStatus,
	) {
		check(allowed) { "MerchantApiKey 상태를 $status 에서 $target (으)로 전이할 수 없습니다." }
	}

	companion object {
		/** 새 API Key를 `ACTIVE` 상태로 발급한다. */
		fun create(
			id: MerchantApiKeyId,
			merchantId: MerchantId,
			keyName: String,
			environment: ApiEnvironment,
			keyPrefix: ApiKeyPrefix,
			secretHash: String,
			hashAlgorithm: String,
			scopes: Set<ApiKeyScope>,
			createdByMerchantUserId: MerchantUserId,
			expiresAt: Instant?,
			createdAt: Instant,
		): MerchantApiKey =
			MerchantApiKey(
				id = id,
				merchantId = merchantId,
				keyName = keyName,
				environment = environment,
				keyPrefix = keyPrefix,
				secretHash = secretHash,
				hashAlgorithm = hashAlgorithm,
				scopes = scopes,
				createdByMerchantUserId = createdByMerchantUserId,
				createdAt = createdAt,
				status = ApiKeyStatus.ACTIVE,
				expiresAt = expiresAt,
				lastUsedAt = null,
				revokedByMerchantUserId = null,
				revokedAt = null,
				updatedAt = createdAt,
			)

		/** 영속 계층에 저장되어 있던 값으로 Aggregate를 복원한다. */
		fun reconstitute(
			id: MerchantApiKeyId,
			merchantId: MerchantId,
			keyName: String,
			environment: ApiEnvironment,
			keyPrefix: ApiKeyPrefix,
			secretHash: String,
			hashAlgorithm: String,
			scopes: Set<ApiKeyScope>,
			createdByMerchantUserId: MerchantUserId,
			createdAt: Instant,
			status: ApiKeyStatus,
			expiresAt: Instant?,
			lastUsedAt: Instant?,
			revokedByMerchantUserId: MerchantUserId?,
			revokedAt: Instant?,
			updatedAt: Instant,
		): MerchantApiKey =
			MerchantApiKey(
				id = id,
				merchantId = merchantId,
				keyName = keyName,
				environment = environment,
				keyPrefix = keyPrefix,
				secretHash = secretHash,
				hashAlgorithm = hashAlgorithm,
				scopes = scopes,
				createdByMerchantUserId = createdByMerchantUserId,
				createdAt = createdAt,
				status = status,
				expiresAt = expiresAt,
				lastUsedAt = lastUsedAt,
				revokedByMerchantUserId = revokedByMerchantUserId,
				revokedAt = revokedAt,
				updatedAt = updatedAt,
			)
	}
}
