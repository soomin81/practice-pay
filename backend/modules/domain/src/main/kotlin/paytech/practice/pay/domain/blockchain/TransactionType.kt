package paytech.practice.pay.domain.blockchain

/**
 * [BlockchainTransaction]이 어떤 목적의 온체인 전송인지를 표현한다.
 *
 * DB의 `transaction_type` 컬럼과 대응한다. `(payment_seq, transaction_type)` 조합이
 * Unique라 결제당 `PAYMENT` 거래 하나, `REFUND` 거래 하나만 가질 수 있다
 * (`docs/database/database-design.md` 참고). MVP는 환불을 지원하지 않지만
 * (ADR-001), 스키마가 이미 값을 나열해 두고 있어 enum도 동일하게 맞춘다.
 */
enum class TransactionType {
	PAYMENT,
	REFUND,
}
