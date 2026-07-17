package paytech.practice.pay.domain.identity

/**
 * [InternalUser]의 MVP 역할을 표현한다.
 *
 * - `SUPER_ADMIN`: 내부 계정 발급, 권한 부여, 전체 관리. 내부 운영자 계정은
 *   `SUPER_ADMIN`만 발급할 수 있다.
 * - `OPERATOR`: 가맹점·결제·운영 업무.
 * - `VIEWER`: 조회 전용.
 *
 * @see docs/architecture/identity-access-api-key.md
 */
enum class InternalUserRole {
	SUPER_ADMIN,
	OPERATOR,
	VIEWER,
}
