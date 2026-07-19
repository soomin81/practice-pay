package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.merchant.MerchantStatus
import java.time.Instant

/**
 * 가맹점 목록 조회를 위한 전용 jOOQ Projection Outbound Port다.
 *
 * `docs/architecture/persistence-jooq.md`의 "Command Repository는 Aggregate를
 * 저장하고 복원한다. 복잡한 조회는 전용 jOOQ Projection을 사용한다"는 원칙을
 * 따른다 — [MerchantRepository]에 `findAll` 같은 메서드를 추가하지 않는 이유다.
 * 화면용 목록은 `Merchant` Aggregate 전체(낙관적 잠금 `version` 포함)를 복원할
 * 필요가 없고, [MerchantSummary]처럼 목적에 맞게 좁힌 값만 있으면 된다 —
 * 이 프로젝트에서 이 구분을 실제로 적용한 첫 사례다.
 */
fun interface MerchantListProjection {
	/**
	 * 등록된 모든 가맹점을 최신 등록순(`created_at DESC`)으로 돌려준다.
	 *
	 * MVP는 페이지네이션·필터링을 지원하지 않는다(알려진 단순화) — 가맹점 수가
	 * 많아지면 그때 `Pageable` 등을 도입한다.
	 */
	fun findAll(): List<MerchantSummary>
}

/** [MerchantListProjection]이 돌려주는 목록 조회 전용 읽기 모델이다. */
data class MerchantSummary(
	val merchantId: MerchantId,
	val merchantCode: MerchantCode,
	val merchantName: String,
	val status: MerchantStatus,
	val createdAt: Instant,
)
