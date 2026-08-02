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

export type ResendInvitationResponse = JsonResponse<'merchant-resend-invitation', 200>
export type RevokeInvitationResponse = JsonResponse<'merchant-revoke-invitation', 200>

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

export type ListPaymentsResponse = JsonResponse<'merchant-payments', 200>
export type PaymentSummary = ListPaymentsResponse['payments'][number]

/** 결제 상태(`docs/domain/state-transitions.md`). 화면 필터의 선택지이기도 하다. */
export type PaymentStatus = 'CREATED' | 'READY' | 'PROCESSING' | 'CONFIRMING' | 'SUCCEEDED' | 'EXPIRED' | 'FAILED'

export const PAYMENT_STATUSES: readonly PaymentStatus[] = [
	'CREATED',
	'READY',
	'PROCESSING',
	'CONFIRMING',
	'SUCCEEDED',
	'EXPIRED',
	'FAILED',
]

/**
 * 결제 내역 조회 필터. 서버의 쿼리 파라미터와 1:1로 대응한다.
 *
 * **`merchantId`가 없다** — 조회 범위는 세션의 가맹점으로 서버가 고정한다
 * (`docs/architecture/merchant-console-api.md`의 4.1). 내부 운영자 콘솔의 같은 타입에만 있다.
 */
export type PaymentListFilters = {
	status?: PaymentStatus | ''
	from?: string
	to?: string
	page?: number
	size?: number
}

export type ListSettlementReceivablesResponse = JsonResponse<'merchant-settlement-receivables', 200>
export type SettlementReceivableSummary = ListSettlementReceivablesResponse['settlementReceivables'][number]

/** 정산 채권 상태(`docs/domain/state-transitions.md`). MVP의 종착점은 `READY`다. */
export type SettlementReceivableStatus = 'PENDING' | 'READY' | 'ASSIGNED' | 'SETTLED' | 'HELD' | 'CANCELLED'

/**
 * 화면 필터의 선택지. `ASSIGNED`/`SETTLED`는 가맹점 단위 집계 정산이 생겨야 의미가 있는
 * 상태라(ADR-005) MVP에서는 나올 수 없지만, 서버가 돌려줄 수 있는 값이므로 목록에는 둔다.
 */
export const SETTLEMENT_RECEIVABLE_STATUSES: readonly SettlementReceivableStatus[] = [
	'PENDING',
	'READY',
	'ASSIGNED',
	'SETTLED',
	'HELD',
	'CANCELLED',
]

/**
 * 정산 채권 조회 필터. 기간이 **정산 예정일 기준 날짜**(`YYYY-MM-DD`)라 결제 내역과 다르다 —
 * 정산에서 묻는 질문이 "언제 정산되나"이고, 날짜라 시간대 경계 문제가 없다.
 */
export type SettlementFilters = {

	status?: SettlementReceivableStatus | ''
	eligibleFrom?: string
	eligibleTo?: string
	page?: number
	size?: number
}

export type PaymentDetailResponse = JsonResponse<'merchant-payment-detail', 200>

/** Webhook 설정 응답. `signingSecret`이 들어 있어 화면에서 다룰 때 주의한다. */
export type MerchantWebhookSettingsResponse = JsonResponse<'merchant-get-webhook', 200>

export type UpdateWebhookUrlRequest = JsonRequest<'merchant-update-webhook-url'>
