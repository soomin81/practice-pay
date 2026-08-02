package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.settlement.SettlementHoldAction
import paytech.practice.pay.domain.settlement.SettlementHoldAuditId
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import java.time.Instant

/**
 * 정산 채권 한 건의 **보류·해제·취소 이력** 조회를 위한 전용 jOOQ Projection Outbound Port다.
 *
 * 로그인 감사가 "최근 전체"를 훑는 것과 달리 여기는 언제나 **채권 하나**로 좁힌다 — 이
 * 이력을 보는 자리가 "이 채권을 풀어도 되나"를 판단하는 화면이라서다.
 */
fun interface SettlementHoldAuditProjection {
	/** 해당 채권의 이력을 최신순(`occurred_at DESC`)으로 돌려준다. 없으면 빈 리스트다. */
	fun findByReceivableId(settlementReceivableId: SettlementReceivableId): List<SettlementHoldAuditEntry>
}

/**
 * [SettlementHoldAuditProjection]이 돌려주는 조회 전용 읽기 모델이다.
 *
 * @property internalUserName 실행한 운영자의 이름. **`null`이 아니다** — 주체가 NOT NULL이라
 * JOIN이 반드시 매칭된다(로그인 감사가 "알 수 없는 계정"을 위해 nullable인 것과 다르다).
 * @property reasonCode [SettlementHoldAction.HELD]일 때의 사유 코드.
 * @property note 실행자가 남긴 메모. 해제·취소에는 반드시 있다.
 */
data class SettlementHoldAuditEntry(
	val auditId: SettlementHoldAuditId,
	val internalUserId: InternalUserId,
	val internalUserName: String,
	val action: SettlementHoldAction,
	val reasonCode: String?,
	val note: String?,
	val occurredAt: Instant,
)
