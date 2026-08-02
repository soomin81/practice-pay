package paytech.practice.pay.application.settlement

import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.SettlementHoldAuditRepository
import paytech.practice.pay.application.port.outbound.SettlementReceivableRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.settlement.SettlementHoldAction
import paytech.practice.pay.domain.settlement.SettlementHoldAudit
import paytech.practice.pay.domain.settlement.SettlementHoldAuditId
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import java.time.Clock

/**
 * 내부 운영자가 **정산 채권을 취소하는** Use Case다
 * (`POST /admin/settlement-receivables/{id}/cancel`).
 *
 * 보류([ReleaseSettlementHoldUseCase])의 반대쪽 출구다 — 그 돈을 정산하지 **않기로 확정**할 때
 * 쓴다. `CANCELLED`는 종료 상태라 되돌릴 수 없다(ADR-007).
 *
 * ## 도메인은 `HELD`가 아니어도 허용하지만 화면은 `HELD`에만 버튼을 둔다
 *
 * `SettlementReceivable.cancel`은 `PENDING`/`READY`에서도 되는데, 그건 "취소는 언제든 가능한
 * 종결"이라는 도메인의 사실이다. 그럼에도 화면이 `HELD` 행에만 버튼을 그리는 이유는,
 * **막지도 않은 채권을 목록에서 곧장 끝낼 수 있게 두면** 실수 한 번의 대가가 너무 커서다
 * (`docs/architecture/admin-console-api.md` 4.6). 서버는 그 UX 제약을 강제하지 않는다 —
 * 도메인이 허용하는 것을 애플리케이션이 다시 좁히면 나중에 정당한 경로가 생겼을 때 두 곳을
 * 고쳐야 한다.
 *
 * 메모는 [ReleaseSettlementHoldUseCase]와 같은 이유로 필수다.
 */
class CancelSettlementReceivableUseCase(
	private val settlementReceivableRepository: SettlementReceivableRepository,
	private val settlementHoldAuditRepository: SettlementHoldAuditRepository,
	private val idGenerator: IdGenerator,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	fun execute(command: CancelSettlementReceivableCommand): CancelSettlementReceivableResult {
		require(command.note.isNotBlank()) { "취소 사유(note)는 공백일 수 없습니다." }

		val receivable =
			settlementReceivableRepository.findById(command.settlementReceivableId)
				?: throw SettlementReceivableNotFoundException(command.settlementReceivableId)

		if (receivable.status == SettlementReceivableStatus.CANCELLED) {
			throw SettlementReceivableNotCancellableException(command.settlementReceivableId, receivable.status)
		}

		val now = clock.instant()
		receivable.cancel(now)

		val audit =
			SettlementHoldAudit(
				id = SettlementHoldAuditId(SettlementHoldAuditId.PREFIX + idGenerator.newId()),
				settlementReceivableId = receivable.id,
				internalUserId = command.actorInternalUserId,
				action = SettlementHoldAction.CANCELLED,
				reasonCode = null,
				note = command.note,
				occurredAt = now,
			)

		return transactionManager.runInTransaction {
			settlementReceivableRepository.save(receivable)
			settlementHoldAuditRepository.append(audit)
			CancelSettlementReceivableResult(
				settlementReceivableId = receivable.id,
				status = receivable.status,
			)
		}
	}
}

/**
 * @property actorInternalUserId 실행한 내부 운영자. 요청 본문이 아니라 인증 주체에서 온다.
 * @property note 왜 취소했는지. 공백이면 거부한다.
 */
data class CancelSettlementReceivableCommand(
	val settlementReceivableId: SettlementReceivableId,
	val actorInternalUserId: InternalUserId,
	val note: String,
)

data class CancelSettlementReceivableResult(
	val settlementReceivableId: SettlementReceivableId,
	val status: SettlementReceivableStatus,
)
