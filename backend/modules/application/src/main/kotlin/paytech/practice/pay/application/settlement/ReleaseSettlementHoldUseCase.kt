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
 * 내부 운영자가 **정산 보류를 푸는** Use Case다
 * (`POST /admin/settlement-receivables/{id}/release`).
 *
 * ## 막을 수만 있고 풀 수 없으면 막지 못한다
 *
 * 보류(`MarkTransactionReorgedUseCase`)는 가맹점에게 나갈 돈을 세우는 동작이라, 되돌릴 길이
 * 없으면 정작 눌러야 할 상황에서 운영자가 망설이게 된다 — 그러면 원래 목적(손실을 막는 것)이
 * 무너진다. 이 Use Case가 그 출구다(ADR-007의 "`HELD`는 막다른 길이 아니다").
 *
 * ## 돌아갈 상태는 요청이 정하지 않는다
 *
 * `SettlementReceivable.release`가 `exchangeOrderId` 유무로 `READY`/`PENDING`을 고른다 —
 * 화면이 정하게 두면 매도가 끝나지 않은 채권을 `READY`로 만들 수 있고, 그건 **근거 없는
 * 정산 금액**이 된다. 결과로 어디로 갔는지 알려준다.
 *
 * ## 메모가 필수다
 *
 * 자동 경로가 없는 전이라 "왜 풀었나"를 아는 곳이 실행한 사람뿐이다. 빈 값이면 거부한다 —
 * 보류 쪽은 사유 코드가 자동으로 붙는 것과 대비된다.
 *
 * `Payment`와 `ExchangeOrder`는 **건드리지 않는다**(ADR-007) — 정산을 어떻게 할지만 정하지,
 * 그때 무슨 일이 있었는지를 고쳐 쓰지 않는다.
 */
class ReleaseSettlementHoldUseCase(
	private val settlementReceivableRepository: SettlementReceivableRepository,
	private val settlementHoldAuditRepository: SettlementHoldAuditRepository,
	private val idGenerator: IdGenerator,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	fun execute(command: ReleaseSettlementHoldCommand): ReleaseSettlementHoldResult {
		require(command.note.isNotBlank()) { "해제 사유(note)는 공백일 수 없습니다." }

		val receivable =
			settlementReceivableRepository.findById(command.settlementReceivableId)
				?: throw SettlementReceivableNotFoundException(command.settlementReceivableId)

		if (receivable.status != SettlementReceivableStatus.HELD) {
			throw SettlementReceivableNotReleasableException(command.settlementReceivableId, receivable.status)
		}

		val now = clock.instant()
		receivable.release(now)

		val audit =
			SettlementHoldAudit(
				id = SettlementHoldAuditId(SettlementHoldAuditId.PREFIX + idGenerator.newId()),
				settlementReceivableId = receivable.id,
				internalUserId = command.actorInternalUserId,
				action = SettlementHoldAction.RELEASED,
				reasonCode = null,
				note = command.note,
				occurredAt = now,
			)

		// 상태만 바뀌고 이력이 빠지면 "누가 풀었나"에 영영 답할 수 없다 — 함께 저장한다.
		return transactionManager.runInTransaction {
			settlementReceivableRepository.save(receivable)
			settlementHoldAuditRepository.append(audit)
			ReleaseSettlementHoldResult(
				settlementReceivableId = receivable.id,
				status = receivable.status,
			)
		}
	}
}

/**
 * @property actorInternalUserId 실행한 내부 운영자. 요청 본문이 아니라 인증 주체에서 온다.
 * @property note 왜 풀었는지. 공백이면 거부한다.
 */
data class ReleaseSettlementHoldCommand(
	val settlementReceivableId: SettlementReceivableId,
	val actorInternalUserId: InternalUserId,
	val note: String,
)

/**
 * @property status 해제 후 **실제로 돌아간 상태**다. 요청이 정하지 않으므로 화면은 이 값을
 * 보고 알려줘야 한다 — 매도 전이면 `PENDING`, 매도가 끝났으면 `READY`다.
 */
data class ReleaseSettlementHoldResult(
	val settlementReceivableId: SettlementReceivableId,
	val status: SettlementReceivableStatus,
)
