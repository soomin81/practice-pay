import type { operations } from './schema'

/**
 * 생성된 스펙 타입에서 쓰기 좋은 이름만 뽑아 둔다(payment 앱의 types.ts와 같은 판단).
 *
 * `schema.d.ts`는 손대지 않는다(생성물) — 스펙이 바뀌면 `npm run gen:api`로 다시
 * 생성해 덮어쓴다. 여기 별칭은 그 생성물을 가리키기만 하므로, 백엔드가 필드를 바꾸면
 * 이 별칭을 쓰는 화면 코드에서 컴파일 에러가 난다. 그게 이 계층을 두는 이유다.
 */
// content-type 키의 정확한 문자열(`application/json` vs `application/json;charset=UTF-8`)에
// 얽매이지 않고 값만 뽑는다 — REST Docs가 요청은 charset을 붙여, 응답은 안 붙여 기록한다.
type ContentOf<T> = T extends { content: infer C } ? C[keyof C] : never

type JsonResponse<T extends keyof operations, S extends keyof operations[T]['responses']> =
	ContentOf<operations[T]['responses'][S]>

type JsonRequest<T extends keyof operations> = ContentOf<NonNullable<operations[T]['requestBody']>>

export type MeResponse = JsonResponse<'merchant-me', 200>

export type LoginRequest = JsonRequest<'merchant-login'>
export type LoginResponse = JsonResponse<'merchant-login', 200>

export type IssueApiKeyRequest = JsonRequest<'merchant-issue-api-key'>
export type IssueApiKeyResponse = JsonResponse<'merchant-issue-api-key', 201>

export type ListApiKeysResponse = JsonResponse<'merchant-list-api-keys', 200>
export type ApiKeySummary = ListApiKeysResponse['apiKeys'][number]

export type RevokeApiKeyResponse = JsonResponse<'merchant-revoke-api-key', 200>

export type InviteSubAccountRequest = JsonRequest<'merchant-invite-sub-account'>
export type InviteSubAccountResponse = JsonResponse<'merchant-invite-sub-account', 201>

export type ListMerchantUsersResponse = JsonResponse<'merchant-list-merchant-users', 200>
export type MerchantUserSummary = ListMerchantUsersResponse['merchantUsers'][number]

export type AcceptInvitationRequest = JsonRequest<'merchant-accept-invitation'>
export type AcceptInvitationResponse = JsonResponse<'merchant-accept-invitation', 200>

export type ChangeUserStatusResponse = JsonResponse<'merchant-suspend-user', 200>
export type ChangeUserRoleRequest = JsonRequest<'merchant-change-user-role'>
export type ChangeUserRoleResponse = JsonResponse<'merchant-change-user-role', 200>

/**
 * 계정 상태 액션. 백엔드의 세 경로(`/suspend`·`/reactivate`·`/terminate`)에 그대로
 * 대응한다 — 요청·응답 형태가 같아서 클라이언트도 하나로 다룬다.
 */
export type MerchantUserStatusAction = 'suspend' | 'reactivate' | 'terminate'

/** 계약(`docs/architecture/identity-access-api-key.md`)의 값들. 화면 분기·표시는 이 값들이 이끈다. */
export type MerchantUserRole = 'OWNER' | 'ADMIN' | 'VIEWER'
export type ApiKeyStatus = 'ACTIVE' | 'REVOKED' | 'EXPIRED'
export type ApiKeyScope = 'PAYMENT_CREATE' | 'PAYMENT_READ'

export type AccountStatus = 'INVITED' | 'ACTIVE' | 'LOCKED' | 'SUSPENDED' | 'TERMINATED'

/** MVP가 발급 가능한 Scope 목록. 발급 폼의 체크박스가 이 목록을 그린다. */
export const ISSUABLE_SCOPES: readonly ApiKeyScope[] = ['PAYMENT_CREATE', 'PAYMENT_READ']

/**
 * 하위 계정으로 발급 가능한 역할. **`OWNER`는 없다** — 하위 계정 발급 경로로는 OWNER를
 * 만들 수 없다(`MerchantUser.inviteSubAccount`의 도메인 규칙). 최초 OWNER는 내부
 * 운영자가 가맹점 등록 트랜잭션에서 만든다.
 */
export const INVITABLE_ROLES: readonly MerchantUserRole[] = ['ADMIN', 'VIEWER']
