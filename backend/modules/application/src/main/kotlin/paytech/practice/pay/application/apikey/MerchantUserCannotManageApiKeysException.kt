package paytech.practice.pay.application.apikey

/**
 * 발급/폐기를 요청한 `MerchantUser`가 [paytech.practice.pay.domain.identity.MerchantUser.canManageApiKeys]를
 * 만족하지 못할 때(`ACTIVE`가 아니거나 역할이 `OWNER`/`ADMIN`이 아닐 때) 던진다.
 *
 * `paytech.practice.pay.application.identity.MerchantUserCannotInviteSubAccountsException`과
 * 같은 철학이다 — 정적 역할 검사(`SecurityConfig`)만으로는 세션이 살아있는 동안의
 * 상태 변화(예: `SUSPENDED`)를 잡을 수 없어서, Use Case가 요청자를 다시 읽어
 * 동적으로 재확인한다.
 */
class MerchantUserCannotManageApiKeysException(
	message: String,
) : RuntimeException(message)
