package paytech.practice.pay.application.identity

/**
 * 도메인이 거부한 내부 운영자 상태 전이(예: 종료된 계정을 재개하려는 시도)를 나타낸다 —
 * inbound Adapter에서 `409 Conflict`로 매핑한다.
 *
 * **도메인의 `IllegalStateException`을 그대로 흘려보내지 않는 이유**는 가맹점 쪽
 * [InvalidMerchantUserTransitionException]과 같다: `IllegalStateException`을 통째로 409에
 * 매핑하면 `checkNotNull`(세션이 가리키는 사용자가 DB에 없음)처럼 **500이 맞는 오류까지
 * 409로 가려진다.** Use Case가 도메인 전이 호출 **한 줄만** 감싸서 이 예외로 바꾼다.
 */
class InvalidInternalUserTransitionException(
	message: String,
	cause: Throwable? = null,
) : RuntimeException(message, cause)
