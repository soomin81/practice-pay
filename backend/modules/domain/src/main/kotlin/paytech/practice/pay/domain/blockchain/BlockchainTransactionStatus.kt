package paytech.practice.pay.domain.blockchain

/**
 * [BlockchainTransaction]의 상태를 표현한다.
 *
 * 정상 흐름: `SUBMITTED → DETECTED → CONFIRMING → CONFIRMED`
 *
 * 예외 상태는 `FAILED`다. `REORGED`는 향후 블록 재구성(reorg) 대응을 위해 스키마가
 * 이미 예약해 둔 값이며, 현재는 어떤 전이 메서드도 이 상태로 보내지 않는다
 * (`docs/domain/state-transitions.md` 참고).
 *
 * `CONFIRMED`, `FAILED`, `REORGED`는 종료 상태다.
 *
 * @see docs/domain/state-transitions.md
 */
enum class BlockchainTransactionStatus {
	SUBMITTED,
	DETECTED,
	CONFIRMING,
	CONFIRMED,
	FAILED,
	REORGED,
}
