package paytech.practice.pay.application.payment

import paytech.practice.pay.application.port.outbound.BlockchainTransactionRepository
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.SettlementHoldAuditRepository
import paytech.practice.pay.application.port.outbound.SettlementReceivableRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.blockchain.BlockchainTransactionId
import paytech.practice.pay.domain.blockchain.BlockchainTransactionStatus
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.settlement.SettlementHoldAction
import paytech.practice.pay.domain.settlement.SettlementHoldAudit
import paytech.practice.pay.domain.settlement.SettlementHoldAuditId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import java.time.Clock

/**
 * 내부 운영자가 **확정된 입금이 체인 재구성으로 사라졌다**고 표시하는 Use Case다
 * (`POST /admin/blockchain-transactions/{id}/mark-reorged`).
 *
 * ## 되돌리지 않고 정산을 막는다
 *
 * 두 가지만 바뀐다:
 * 1. `BlockchainTransaction`: `CONFIRMED → REORGED` — 그 입금이 체인에 없다는 사실.
 * 2. `SettlementReceivable`: `READY → HELD` — **가맹점에게 지급되지 않게 막는다.**
 *
 * **`Payment`는 `SUCCEEDED`로, `ExchangeOrder`는 `COMPLETED`로 그대로 둔다.** 그때 12
 * Confirm을 확인하고 승인했고 그 승인에 근거해 매도했으므로, 뒤늦게 상태를 뒤집으면 그
 * 이력이 사라져 매도가 왜 일어났는지 설명할 수 없게 된다. 목적은 "성공을 지우는 것"이
 * 아니라 **돈이 나가지 않게 하는 것**이고, 그건 `HELD`로 달성된다 — MVP의 종착점이
 * `SettlementReceivable = READY`이므로(ADR-005) 그 앞을 막으면 손실 경로가 닫힌다.
 *
 * 전체 근거와 **그 대가**(결제 목록에는 여전히 "결제 완료"로 남는다)는
 * `docs/decisions/ADR-007-onchain-irreversibility.md`의 "확정 이후의 `REORGED`" 절에 있다.
 *
 * ## 둘은 반드시 함께 일어난다
 *
 * 거래만 `REORGED`로 바꾸고 정산을 두면 **아무 돈도 막지 못한다** — 표시만 바뀌고 지급은
 * 그대로 나간다. 그래서 한 트랜잭션에서 처리한다.
 *
 * ## 정산 채권이 없을 수도 있다
 *
 * 확정 직후 매도 Worker가 아직 돌지 않았으면 채권이 없다. 그때는 거래만 `REORGED`로
 * 표시하고 끝낸다 — **그러면 매도 Worker가 이 결제를 집어 채권을 만들 수 있다는 뜻이라,
 * 이 경우가 오히려 위험하다.** 화면이 그 사실을 알려야 한다([RedeliverWebhookResult]처럼
 * 결과에 담아 돌려준다).
 */
class MarkTransactionReorgedUseCase(
	private val blockchainTransactionRepository: BlockchainTransactionRepository,
	private val settlementReceivableRepository: SettlementReceivableRepository,
	private val settlementHoldAuditRepository: SettlementHoldAuditRepository,
	private val idGenerator: IdGenerator,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	fun execute(command: MarkTransactionReorgedCommand): MarkTransactionReorgedResult {
		val transaction =
			blockchainTransactionRepository.findById(command.blockchainTransactionId)
				?: throw BlockchainTransactionNotFoundException(command.blockchainTransactionId)

		// 확정된 거래만 대상이다. 확정 전이면 자동 경로(Confirm 폴링)가 유예를 두고 판단하므로
		// 사람이 끼어들 이유가 없고, 이미 REORGED면 할 일이 없다.
		if (transaction.status != BlockchainTransactionStatus.CONFIRMED) {
			throw TransactionNotReorgeableException(command.blockchainTransactionId, transaction.status)
		}

		val now = clock.instant()
		transaction.markReorgedAfterConfirmation(now)

		// 이미 HELD/CANCELLED면 다시 막을 것이 없다 — 상태 전이가 예외를 던지므로 미리 거른다.
		val receivable =
			settlementReceivableRepository
				.findByPaymentId(transaction.paymentId)
				?.takeIf { it.status == SettlementReceivableStatus.PENDING || it.status == SettlementReceivableStatus.READY }

		receivable?.hold(HOLD_REASON_CODE, now)

		// 이 보류도 사람이 실행한 것이라 이력에 남는다 — 나중에 해제와 나란히 읽혀야
		// "막았다가 풀었다"가 한 줄로 이어진다.
		val audit =
			receivable?.let {
				SettlementHoldAudit(
					id = SettlementHoldAuditId(SettlementHoldAuditId.PREFIX + idGenerator.newId()),
					settlementReceivableId = it.id,
					internalUserId = command.actorInternalUserId,
					action = SettlementHoldAction.HELD,
					reasonCode = HOLD_REASON_CODE,
					note = null,
					occurredAt = now,
				)
			}

		return transactionManager.runInTransaction {
			blockchainTransactionRepository.save(transaction)
			receivable?.let { settlementReceivableRepository.save(it) }
			audit?.let { settlementHoldAuditRepository.append(it) }
			MarkTransactionReorgedResult(
				blockchainTransactionId = transaction.id,
				paymentId = transaction.paymentId,
				settlementHeld = receivable != null,
			)
		}
	}

	companion object {
		/**
		 * `settlement_receivable.hold_reason_code`에 남는 값이다. 사람이 나중에 "왜 막혔나"를
		 * 물을 때 이 코드 하나로 답이 되어야 해서 상태가 아니라 **원인**을 적는다.
		 */
		private const val HOLD_REASON_CODE = "TRANSACTION_REORGED"
	}
}

/**
 * @property actorInternalUserId 실행한 내부 운영자. 요청 본문이 아니라 인증 주체에서 온다 —
 * 이 값이 `settlement_hold_audit`에 남아 나중에 "누가 막았나"에 답한다.
 */
data class MarkTransactionReorgedCommand(
	val blockchainTransactionId: BlockchainTransactionId,
	val actorInternalUserId: InternalUserId,
)

/**
 * 확정 이후 reorg 표시 결과다.
 *
 * @property settlementHeld 딸린 정산 채권을 실제로 막았는지. **`false`면 아직 채권이 없다는
 * 뜻이고, 그건 매도 Worker가 이 결제를 집어 채권을 만들 수 있다는 뜻이라 더 위험하다** —
 * 화면이 그 사실을 반드시 알려야 한다.
 */
data class MarkTransactionReorgedResult(
	val blockchainTransactionId: BlockchainTransactionId,
	val paymentId: PaymentId,
	val settlementHeld: Boolean,
)
