package paytech.practice.pay.application.payment

import paytech.practice.pay.domain.blockchain.BlockchainTransactionId

/**
 * [ConfirmBlockchainTransactionUseCase]의 입력이다.
 *
 * 이 Use Case는 이미 존재하는 `BlockchainTransaction`(`SUBMITTED`/`DETECTED`/
 * `CONFIRMING` 중 하나)의 현재 온체인 상태를 다시 확인하는 한 번의 폴링이다 —
 * `BlockchainTransaction`을 처음 만드는 것(고객 지갑이 제출한 Transaction Hash를
 * 받아 `SUBMITTED` 상태로 기록하는 것)은 이 Use Case의 책임이 아니다(별도
 * Use Case, 아직 없음).
 */
data class ConfirmBlockchainTransactionCommand(
	val blockchainTransactionId: BlockchainTransactionId,
)
