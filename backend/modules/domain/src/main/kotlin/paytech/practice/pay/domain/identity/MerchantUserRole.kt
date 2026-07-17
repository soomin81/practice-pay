package paytech.practice.pay.domain.identity

/**
 * [MerchantUser]의 MVP 역할을 표현한다.
 *
 * - `OWNER`: 가맹점 최고 관리자. 하위 계정과 API Key를 관리한다. 가맹점 등록
 *   트랜잭션에서만 생성된다([MerchantUser.inviteInitialOwner]) — 하위 계정
 *   발급으로는 만들 수 없다.
 * - `ADMIN`: 가맹점 운영 관리. 하위 계정과 API Key를 관리하지만 `OWNER`를
 *   발급하거나 기존 `OWNER`의 권한을 변경할 수 없다.
 * - `VIEWER`: 조회 전용. 계정과 API Key를 관리할 수 없다.
 *
 * @see docs/architecture/identity-access-api-key.md
 */
enum class MerchantUserRole {
	OWNER,
	ADMIN,
	VIEWER,
}
