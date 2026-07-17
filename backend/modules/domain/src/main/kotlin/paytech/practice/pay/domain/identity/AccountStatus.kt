package paytech.practice.pay.domain.identity

/**
 * [InternalUser]와 [MerchantUser]가 공통으로 쓰는 계정 상태다.
 *
 * 활성화: `INVITED → ACTIVE`
 * 잠금과 해제: `ACTIVE → LOCKED → ACTIVE`
 * 운영 중지: `ACTIVE → SUSPENDED → ACTIVE`
 * 종료: (`ACTIVE` 또는 `SUSPENDED`) → `TERMINATED`
 *
 * `TERMINATED`는 종료 상태이며 재활성화하지 않는다.
 *
 * @see docs/architecture/identity-access-api-key.md
 * @see docs/domain/state-transitions.md
 */
enum class AccountStatus {
	INVITED,
	ACTIVE,
	LOCKED,
	SUSPENDED,
	TERMINATED,
}
