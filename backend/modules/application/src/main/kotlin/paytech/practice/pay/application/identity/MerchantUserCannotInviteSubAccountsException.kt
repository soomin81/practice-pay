package paytech.practice.pay.application.identity

/**
 * 발급을 요청한 `MerchantUser`가 [MerchantUser.canInviteSubAccounts]를 만족하지
 * 못할 때(`ACTIVE`가 아니거나 역할이 `OWNER`/`ADMIN`이 아닐 때) 던진다.
 *
 * `POST /merchant/merchant-users`의 `SecurityConfig` 인가 규칙(`hasAnyRole("OWNER",
 * "ADMIN")`)이 역할만 정적으로 걸러내는 것과 달리, 이 예외는 [InviteMerchantSubAccountUseCase]가
 * 요청자의 `MerchantUser`를 다시 읽어 **지금 이 순간의 상태**(예: 세션이 살아있는
 * 동안 계정이 `SUSPENDED`된 경우)까지 확인한 결과다.
 */
class MerchantUserCannotInviteSubAccountsException(
	message: String,
) : RuntimeException(message)
