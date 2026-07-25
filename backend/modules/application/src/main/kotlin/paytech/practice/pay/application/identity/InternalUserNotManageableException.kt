package paytech.practice.pay.application.identity

/**
 * 요청자가 그 대상 계정을 관리할 수 없을 때 던진다 — inbound Adapter에서 `403`으로
 * 매핑한다.
 *
 * 지금은 **자기 자신을 대상으로 삼은 경우** 하나다. 스스로를 정지·종료·강등하면 복구
 * 수단이 사라지고, 특히 마지막 SUPER_ADMIN이 자신을 강등하면 아무도 내부 계정을 관리할
 * 수 없게 된다. `docs/`에 명시적 규칙이 없어 **추론한 판단**이다(가맹점 쪽
 * [MerchantUserNotManageableException]과 같은 이유).
 *
 * 가맹점 쪽에 있던 "ADMIN이 OWNER를 건드릴 수 없다"에 대응하는 규칙은 여기 없다 —
 * 이 API는 `SecurityConfig`가 `SUPER_ADMIN`에게만 열어 두므로 요청자 역할이 하나뿐이다.
 */
class InternalUserNotManageableException(
	message: String,
) : RuntimeException(message)
