package paytech.practice.pay.application.payment

import paytech.practice.pay.domain.blockchain.BlockchainTransactionId
import paytech.practice.pay.domain.blockchain.BlockchainTransactionStatus

/**
 * `CONFIRMED`가 아닌 거래를 확정 이후 reorg로 표시하려 할 때다 — 호출부가 `409`로 옮긴다.
 *
 * **현재 상태를 문구에 담는다.** 확정 전이면 자동 경로(Confirm 폴링)가 유예를 두고 판단하므로
 * 사람이 끼어들 이유가 없고, 이미 `REORGED`면 할 일이 없다 — 어느 쪽인지 알아야 운영자가
 * 다음 행동을 정한다(`WebhookDeliveryNotRedeliverableException`과 같은 판단).
 */
class TransactionNotReorgeableException(
	val blockchainTransactionId: BlockchainTransactionId,
	val status: BlockchainTransactionStatus,
) : RuntimeException("확정된 거래만 체인 재구성으로 표시할 수 있습니다. 현재 상태: $status")
