package paytech.practice.pay.application.identity

/**
 * [ChangeInternalUserStatusUseCase]가 수행할 계정 상태 전이다. 각 값은
 * [InternalUser][paytech.practice.pay.domain.identity.InternalUser]의 전이 메서드 하나에
 * 대응한다(가맹점 쪽 [MerchantUserStatusAction]과 같은 모양).
 *
 * `LOCKED`/`unlock`은 여기 없다 — 그건 운영자가 누르는 관리 동작이 아니라 로그인 실패
 * 누적으로 시스템이 거는 상태다(`AuthenticateInternalUserUseCase`의 몫).
 */
enum class InternalUserStatusAction {
	/** `ACTIVE` → `SUSPENDED`. */
	SUSPEND,

	/** `SUSPENDED` → `ACTIVE`. */
	REACTIVATE,

	/** (`ACTIVE`|`SUSPENDED`) → `TERMINATED`. 되돌릴 수 없다. */
	TERMINATE,
}
