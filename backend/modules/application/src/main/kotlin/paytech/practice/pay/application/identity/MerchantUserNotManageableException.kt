package paytech.practice.pay.application.identity

/**
 * 요청자가 그 대상 계정을 관리할 수 없을 때 던진다 — inbound Adapter에서 `403`으로
 * 매핑한다. 두 경우를 덮는다:
 *
 * 1. **자기 자신을 대상으로 삼았다.** 스스로를 정지·종료·강등하면 복구 수단이 사라지고,
 *    특히 마지막 OWNER가 자신을 강등하면 그 가맹점을 아무도 관리할 수 없게 된다.
 *    `docs/`에 명시적 규칙이 없어 **추론한 판단**이다.
 * 2. **`ADMIN`이 `OWNER`를 대상으로 삼았다.** `docs/architecture/identity-access-api-key.md`의
 *    "4.4"가 "`ADMIN`은 기존 OWNER의 권한을 변경할 수 없다"고 규정한 것을, 같은 취지로
 *    정지·종료까지 확장한 **추론**이다(권한만 못 바꾸고 정지는 할 수 있다면 규칙이
 *    무의미해진다).
 *
 * 대상이 다른 가맹점 소속이라 관리할 수 없는 경우는 이 예외가 아니라 "없음"(404)으로
 * 취급한다 — 남의 가맹점 사용자의 존재 여부를 알려주지 않기 위해서다.
 */
class MerchantUserNotManageableException(
	message: String,
) : RuntimeException(message)
