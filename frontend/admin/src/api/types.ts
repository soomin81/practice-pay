import type { operations } from './schema'

/**
 * 생성된 스펙 타입에서 쓰기 좋은 이름만 뽑아 둔다(merchant 앱의 types.ts와 같은 판단).
 * `schema.d.ts`는 생성물이라 손대지 않는다 — 백엔드가 필드를 바꾸면 이 별칭을 쓰는 화면
 * 코드에서 컴파일 에러가 나는 것이 이 계층의 목적이다.
 */
type ContentOf<T> = T extends { content: infer C } ? C[keyof C] : never

type JsonResponse<T extends keyof operations, S extends keyof operations[T]['responses']> =
	ContentOf<operations[T]['responses'][S]>

type JsonRequest<T extends keyof operations> = ContentOf<NonNullable<operations[T]['requestBody']>>

export type MeResponse = JsonResponse<'admin-me', 200>

export type LoginRequest = JsonRequest<'admin-login'>
export type LoginResponse = JsonResponse<'admin-login', 200>

export type ListMerchantsResponse = JsonResponse<'admin-list-merchants', 200>
export type MerchantSummary = ListMerchantsResponse['merchants'][number]

export type RegisterMerchantRequest = JsonRequest<'admin-register-merchant'>
export type RegisterMerchantResponse = JsonResponse<'admin-register-merchant', 201>

export type ListInternalUsersResponse = JsonResponse<'admin-list-internal-users', 200>
export type InternalUserSummary = ListInternalUsersResponse['internalUsers'][number]

export type IssueInternalUserRequest = JsonRequest<'admin-issue-internal-user'>
export type IssueInternalUserResponse = JsonResponse<'admin-issue-internal-user', 201>

export type AcceptInvitationRequest = JsonRequest<'admin-accept-invitation'>
export type AcceptInvitationResponse = JsonResponse<'admin-accept-invitation', 200>

export type ChangeInternalUserStatusResponse = JsonResponse<'admin-change-internal-user-status', 200>
export type ChangeInternalUserRoleResponse = JsonResponse<'admin-change-internal-user-role', 200>

export type ListMerchantUsersResponse = JsonResponse<'admin-list-merchant-users', 200>
export type MerchantUserSummary = ListMerchantUsersResponse['merchantUsers'][number]

export type ListLoginAuditResponse = JsonResponse<'admin-login-audit', 200>
export type LoginAuditEntry = ListLoginAuditResponse['entries'][number]
/** 로그인 감사 결과. 백엔드 `InternalLoginOutcome`/`MerchantLoginOutcome`과 대응한다. */
export type LoginOutcome = 'SUCCESS' | 'INVALID_CREDENTIALS' | 'LOCKED'

export type ListMerchantLoginAuditResponse = JsonResponse<'admin-merchant-login-audit', 200>
export type MerchantLoginAuditEntry = ListMerchantLoginAuditResponse['entries'][number]
export type ChangeMerchantUserStatusResponse = JsonResponse<'admin-change-merchant-user-status', 200>
export type ChangeMerchantUserRoleResponse = JsonResponse<'admin-change-merchant-user-role', 200>

/**
 * 계정 상태 액션. 백엔드의 세 경로(`/suspend`·`/reactivate`·`/terminate`)에 그대로
 * 대응한다 — 요청·응답 형태가 같아서 클라이언트도 하나로 다룬다(merchant 앱과 같은 모양).
 */
export type InternalUserStatusAction = 'suspend' | 'reactivate' | 'terminate'

/** 계약(`docs/architecture/identity-access-api-key.md`의 "3.2")의 내부 운영자 역할. */
export type InternalUserRole = 'SUPER_ADMIN' | 'OPERATOR' | 'VIEWER'

/**
 * 콘솔에서 발급할 수 있는 내부 운영자 역할. **`SUPER_ADMIN`은 없다** — `docs/`의 "3.3"이
 * "최초 SUPER_ADMIN은 배포 초기화 명령, 안전한 운영 절차 또는 별도 Bootstrap 과정으로
 * 생성한다"고 규정한다(가맹점 쪽에서 `OWNER` 승격을 막은 것과 같은 결). 백엔드는 이 제약을
 * 강제하지 않으므로 화면에서만 제한한다 — 그 사실을 IMPLEMENTATION-NOTES에 남겼다.
 */
export const ISSUABLE_INTERNAL_ROLES: readonly InternalUserRole[] = ['OPERATOR', 'VIEWER']

/** 내부 직원 관리(명부·발급)는 SUPER_ADMIN 전용이다 — 서버도 403으로 막는다. */
export function canManageInternalUsers(role: string): boolean {
	return role === 'SUPER_ADMIN'
}

/** 계약(`docs/architecture/identity-access-api-key.md`의 "4.2")의 가맹점 사용자 역할. */
export type MerchantUserRole = 'OWNER' | 'ADMIN' | 'VIEWER'

/**
 * 내부 운영자가 가맹점 사용자 역할을 바꿀 때 고를 수 있는 값. **`OWNER`는 없다** —
 * `OWNER` 승격은 도메인(`MerchantUser.changeRole`)이 막고(최초 OWNER는 가맹점 등록
 * 트랜잭션에서만 생성), 서버도 400을 낸다. 가맹점 콘솔의 `INVITABLE_ROLES`와 같은 결이다.
 */
export const INVITABLE_MERCHANT_ROLES: readonly MerchantUserRole[] = ['ADMIN', 'VIEWER']

/** 가맹점 사용자 계정 상태 액션. 백엔드의 세 경로(`/suspend`·`/reactivate`·`/terminate`)에 대응한다. */
export type MerchantUserStatusAction = 'suspend' | 'reactivate' | 'terminate'

/**
 * 가맹점 계정 관리(정지·재개·종료·역할 변경)를 할 수 있는 내부 역할. 서버도
 * `POST /admin/merchants/**`를 SUPER_ADMIN/OPERATOR로 좁힌다(가맹점 등록과 같은 스코핑) —
 * VIEWER는 조회만 하므로 액션 버튼을 아예 감춘다. 조회(GET)는 VIEWER 포함 전원이 가능하다.
 */
export function canManageMerchantAccounts(role: string): boolean {
	return role === 'SUPER_ADMIN' || role === 'OPERATOR'
}

/**
 * 가맹점을 등록할 수 있는 역할. `VIEWER`는 조회 전용이라 등록 폼을 보여주지 않는다 —
 * 서버도 403으로 막지만(SecurityConfig의 메서드 스코핑), 누를 수 있게 두고 거부하는 것보다
 * 감추는 편이 낫다.
 */
export function canRegisterMerchant(role: string): boolean {
	return role === 'SUPER_ADMIN' || role === 'OPERATOR'
}
