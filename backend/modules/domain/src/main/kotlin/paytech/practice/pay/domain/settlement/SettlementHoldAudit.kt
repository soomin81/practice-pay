package paytech.practice.pay.domain.settlement

import paytech.practice.pay.domain.identity.InternalUserId
import java.time.Instant

/**
 * 정산 채권의 보류·해제·취소 한 번을 남기는 **불변 감사 기록**이다(`settlement_hold_audit` 테이블).
 *
 * 상태 전이가 없는 append-only 스냅샷이라 다른 애그리게이트와 달리 `private` 생성자 +
 * `create`/`reconstitute` 팩토리를 두지 않고 공개 생성자를 가진 평범한 `data class`로 둔다 —
 * `InternalLoginAudit`/`PaymentQuote`가 같은 이유로 그렇게 돼 있다(`backend/CLAUDE.md`의
 * "도메인 코드 컨벤션").
 *
 * **`SettlementReceivable.holdReasonCode`와 역할이 다르다.** 그쪽은 "지금 왜 막혀 있나"에만
 * 답하는 현재 상태 필드라 해제하면 지워진다 — 막혔던 사실 자체는 여기에만 남는다
 * (`docs/domain/state-transitions.md`).
 *
 * @property internalUserId 실행한 내부 운영자. **`null`이 될 수 없다** — 로그인 감사가
 * "없는 계정으로의 시도" 때문에 주체 없는 행을 허용한 것과 달리, 이 행위는 인증된 운영자만
 * 실행할 수 있다.
 * @property reasonCode [SettlementHoldAction.HELD]일 때의 사유 코드. 해제·취소에는 없다.
 * @property note 사람이 남기는 자유 메모. **해제·취소에서는 Use Case가 필수로 요구한다** —
 * 자동 경로가 없는 전이라 실행한 사람 말고는 이유를 아는 곳이 없다.
 */
data class SettlementHoldAudit(
	val id: SettlementHoldAuditId,
	val settlementReceivableId: SettlementReceivableId,
	val internalUserId: InternalUserId,
	val action: SettlementHoldAction,
	val reasonCode: String?,
	val note: String?,
	val occurredAt: Instant,
)
