package paytech.practice.pay.domain.blockchain

/**
 * [BlockchainTransaction]의 상태를 표현한다.
 *
 * 정상 흐름: `SUBMITTED → DETECTED → CONFIRMING → CONFIRMED`
 *
 * 예외 상태는 `FAILED`와 `REORGED`다. `REORGED`는 **블록에 들어간 것을 이미 확인했는데
 * 그 거래가 체인에서 사라진 경우**이며, [BlockchainTransaction.markReorged]로만 도달한다
 * (`DETECTED`/`CONFIRMING`에서만 — `CONFIRMED` 이후의 reorg는 정산까지 되돌려야 해서
 * MVP 범위 밖이다. `docs/domain/state-transitions.md` 참고).
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
