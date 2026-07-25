package paytech.practice.pay.application.identity

/**
 * 마지막 활성 `SUPER_ADMIN`을 활성 SUPER_ADMIN 집합에서 제외시키려는 요청을 거부한다
 * (`docs/architecture/identity-access-api-key.md`의 "3.3": "내부 운영자에는 최소 하나의
 * 활성 `SUPER_ADMIN`이 항상 존재해야 한다").
 *
 * 정지·종료·역할 강등 셋 다 이 예외를 던질 수 있다 — 마지막 SUPER_ADMIN이 사라지면
 * **아무도 내부 계정을 발급할 수 없는 상태로 굳는다**(발급이 SUPER_ADMIN 전용이고, 내부
 * 운영자 위에는 개입해 줄 주체가 없다 — 복구는 Bootstrap 같은 운영 절차뿐이다). 가맹점
 * 쪽 [LastActiveOwnerException]과 같은 성격이며 inbound Adapter에서 `409`로 매핑한다.
 */
class LastActiveSuperAdminException(
	message: String,
) : RuntimeException(message)
