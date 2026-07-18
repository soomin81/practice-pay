package paytech.practice.pay.application.payment

import paytech.practice.pay.domain.blockchain.TransactionHash

/**
 * 제출된 [TransactionHash]가 이미 **다른** Payment의 `BlockchainTransaction`에 쓰이고
 * 있을 때 던진다 — `uk_blockchain_network_hash` Unique 제약과 대응한다
 * (`docs/domain/glossary.md`의 Transaction Hash 정의).
 *
 * 같은 Payment가 같은 Hash를 다시 제출한 경우(중복 요청)는 이 예외가 아니라
 * 기존 결과를 그대로 돌려준다 — [SubmitPaymentTransactionUseCase]의 멱등성 체크 참고.
 */
class DuplicateTransactionHashException(
	transactionHash: TransactionHash,
) : RuntimeException("이미 다른 결제에 사용된 Transaction Hash입니다: ${transactionHash.value}")
