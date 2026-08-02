package paytech.practice.pay.api.admin.web

import java.time.Instant

/**
 * 정산 보류 해제·취소 요청 본문이다(`docs/architecture/admin-console-api.md` 4.6).
 *
 * **두 동작이 같은 본문을 쓴다** — 필요한 것이 "왜 했는지" 하나뿐이라 타입을 나눌 이유가 없다.
 *
 * @property note 왜 풀었는지/취소했는지. **필수다** — 자동 경로가 없는 전이라 실행한 사람
 *말고는 이유를 아는 곳이 없다. 공백이면 `400`이다(검증은 Use Case가 한다 — 도메인 규칙에
 * 가까운 제약이라 Bean Validation으로 컨트롤러에 두지 않는다).
 */
data class SettlementHoldActionRequest(
	val note: String = "",
)

/**
 * @property status **실제로 돌아간 상태**다. 해제는 요청이 목표 상태를 정하지 않으므로
 * (서버가 `exchangeOrderId` 유무로 고른다) 화면은 이 값을 보고 알려줘야 한다 — 매도 전이면
 * `PENDING`, 매도가 끝났으면 `READY`다. 취소는 언제나 `CANCELLED`다.
 */
data class SettlementHoldActionResponse(
	val settlementReceivableId: String,
	val status: String,
)

/** `GET /admin/settlement-receivables/{id}/hold-history`의 응답이다. */
data class ListSettlementHoldHistoryResponse(
	val history: List<SettlementHoldAuditResponse>,
)

/**
 * 보류 이력 한 줄이다.
 *
 * @property action `HELD`/`RELEASED`/`CANCELLED`. **채권 상태와 값이 겹쳐 보이지만 다른 축이다** —
 * 상태는 "지금 어디에 있나"이고 이건 "무엇을 했나"라, `RELEASED`에 대응하는 상태가 없다.
 * @property reasonCode `HELD`일 때의 사유 코드. 해제·취소에는 `null`이다.
 * @property note 실행자가 남긴 메모. 해제·취소에는 반드시 있다.
 */
data class SettlementHoldAuditResponse(
	val auditId: String,
	val internalUserId: String,
	val internalUserName: String,
	val action: String,
	val reasonCode: String?,
	val note: String?,
	val occurredAt: Instant,
)
