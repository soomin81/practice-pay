package paytech.practice.pay.application.identity

/**
 * 가맹점의 마지막 활성 `OWNER`를 활성 OWNER에서 제외시키려는 요청을 거부한다
 * (`docs/domain/domain-model.md`: "최소 하나의 활성 OWNER를 유지한다").
 *
 * 정지·종료·역할 강등 셋 다 이 예외를 던질 수 있다 — 마지막 OWNER가 사라지면 그
 * 가맹점은 계정도 API Key도 관리할 수 없는 상태로 남는다. inbound Adapter에서
 * `409 Conflict`로 매핑한다(권한 문제가 아니라 **지금 상태에서 허용되지 않는 요청**이다).
 */
class LastActiveOwnerException(
	message: String,
) : RuntimeException(message)
