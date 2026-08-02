package paytech.practice.pay.application.settlement

import paytech.practice.pay.application.port.outbound.SettlementHoldAuditEntry
import paytech.practice.pay.application.port.outbound.SettlementHoldAuditProjection
import paytech.practice.pay.application.port.outbound.SettlementReceivableRepository
import paytech.practice.pay.domain.settlement.SettlementReceivableId

/**
 * 정산 채권 한 건의 **보류·해제·취소 이력**을 최신순으로 돌려주는 Use Case다
 * (`GET /admin/settlement-receivables/{id}/hold-history`).
 *
 * **요청자를 받지 않는다** — 인가는 전적으로 `SecurityConfig`가 진다(`ListInternalLoginAuditUseCase`와
 * 같은 관행). 이쪽은 조회라 `VIEWER`에게도 열려 있다 — **이력을 읽는 것과 상태를 바꾸는 것은
 * 다른 권한이다.**
 *
 * **없는 채권이면 빈 리스트가 아니라 404다.** 이력이 비어 있는 것("아직 아무 일도 없었다")과
 * 채권 자체가 없는 것은 다른 사실이고, 둘을 같은 응답으로 뭉개면 잘못된 ID로 조회한 운영자가
 * "이 채권은 손댄 적이 없다"고 읽는다.
 */
class ListSettlementHoldHistoryUseCase(
	private val settlementReceivableRepository: SettlementReceivableRepository,
	private val settlementHoldAuditProjection: SettlementHoldAuditProjection,
) {
	fun execute(settlementReceivableId: SettlementReceivableId): ListSettlementHoldHistoryResult {
		settlementReceivableRepository.findById(settlementReceivableId)
			?: throw SettlementReceivableNotFoundException(settlementReceivableId)

		return ListSettlementHoldHistoryResult(
			entries = settlementHoldAuditProjection.findByReceivableId(settlementReceivableId),
		)
	}
}

data class ListSettlementHoldHistoryResult(
	val entries: List<SettlementHoldAuditEntry>,
)
