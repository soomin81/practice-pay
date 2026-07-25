package paytech.practice.pay.application.identity

/**
 * [ChangeMerchantUserStatusUseCase]가 수행할 계정 상태 전이다. 각 값은
 * [MerchantUser][paytech.practice.pay.domain.identity.MerchantUser]의 전이 메서드
 * 하나에 대응한다(`suspend()`/`reactivate()`/`terminate()`).
 *
 * `LOCKED`/`unlock`은 여기 없다 — 그건 운영자가 누르는 관리 동작이 아니라 로그인 실패
 * 누적으로 시스템이 거는 상태다(`AuthenticateMerchantUserUseCase`의 몫).
 */
enum class MerchantUserStatusAction {
	/** `ACTIVE` → `SUSPENDED`. */
	SUSPEND,

	/** `SUSPENDED` → `ACTIVE`. */
	REACTIVATE,

	/** (`ACTIVE`|`SUSPENDED`) → `TERMINATED`. 되돌릴 수 없다. */
	TERMINATE,
}
