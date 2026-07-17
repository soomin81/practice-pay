package paytech.practice.pay.domain.exchange

/**
 * 거래소 주문의 매수/매도 방향을 표현한다.
 *
 * MVP는 항상 USDC를 KRW로 매도하는 `SELL`만 쓴다("거래소 USDC 매도 주문",
 * `docs/database/schema.sql`의 테이블 코멘트 참고). `BUY`는 스키마 CHECK 제약이
 * 이미 값을 나열해 두고 있어 enum도 동일하게 맞춘다.
 */
enum class OrderSide {
	BUY,
	SELL,
}
