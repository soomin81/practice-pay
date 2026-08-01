package paytech.practice.pay.domain.payment

/**
 * [Payment]가 `FAILED`로 전이하는 원인을 표현한다.
 *
 * `CONFIRMING → SUCCEEDED` 전이 조건(`docs/domain/state-transitions.md`)이 검증에
 * 실패하는 경우들을 값으로 나열한다.
 *
 * **실패가 곧 "돈이 오지 않았다"는 아니다** — 온체인 전송은 되돌릴 수 없어서 값마다 자금
 * 위치가 다르다. 특히 [TOKEN_CONTRACT_NOT_ALLOWED]와 [AMOUNT_INSUFFICIENT]는 자금이 이미
 * PG 수취 지갑에 들어온 경우다(ADR-007).
 */
enum class PaymentFailureReason {
	/** 온체인 거래의 Network 또는 Chain ID가 기대값과 다르다. */
	NETWORK_MISMATCH,

	/** 토큰 Contract 주소가 허용 목록에 없다 — **자금은 수취 지갑에 들어온 상태다**(ADR-007). */
	TOKEN_CONTRACT_NOT_ALLOWED,

	/** 온체인 거래의 수취 지갑이 Payment의 receivingWallet과 다르다. */
	RECEIVING_WALLET_MISMATCH,

	/** 전송된 금액이 결제 금액에 미달한다 — **부족분이지만 자금은 수취 지갑에 들어온 상태다**(ADR-007). */
	AMOUNT_INSUFFICIENT,

	/** 온체인 거래의 Receipt가 실패했다. */
	TRANSACTION_RECEIPT_FAILED,

	/** 이미 다른 결제에 사용된 Transaction Hash다. */
	DUPLICATE_TRANSACTION_HASH,
}
