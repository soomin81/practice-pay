package paytech.practice.pay.application.identity

/**
 * 도메인이 거부한 계정 상태 전이(예: 종료된 계정을 재개하려는 시도)를 나타낸다 —
 * inbound Adapter에서 `409 Conflict`로 매핑한다.
 *
 * **도메인의 `IllegalStateException`을 그대로 흘려보내지 않고 이 타입으로 바꾸는 이유**는,
 * inbound Adapter가 `IllegalStateException`을 통째로 409에 매핑하면 `checkNotNull`
 * (세션이 가리키는 사용자가 DB에 없음)처럼 **500이 맞는 진짜 예상 못 한 오류까지 409로
 * 가려지기** 때문이다. Use Case가 도메인 전이 호출 **한 줄만** 감싸서 이 예외로 바꾼다
 * (`apps:api-payment`가 같은 이유로 `IllegalStateException`→409 매핑을 체크아웃 경로에만
 * 좁힌 것과 같은 판단 — 거기는 경로로, 여기는 예외 타입으로 좁혔다).
 */
class InvalidMerchantUserTransitionException(
	message: String,
	cause: Throwable? = null,
) : RuntimeException(message, cause)
