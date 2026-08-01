import type {
	AcceptInvitationRequest,
	AcceptInvitationResponse,
	ChangeUserRoleResponse,
	ChangeUserStatusResponse,
	InviteSubAccountRequest,
	MerchantUserRole,
	MerchantUserStatusAction,
	ResendInvitationResponse,
	RevokeInvitationResponse,
	InviteSubAccountResponse,
	IssueApiKeyRequest,
	IssueApiKeyResponse,
	ListApiKeysResponse,
	ListMerchantUsersResponse,
	ListPaymentsResponse,
	ListSettlementReceivablesResponse,
	SettlementFilters,
	PaymentListFilters,
	LoginRequest,
	LoginResponse,
	MeResponse,
	RevokeApiKeyResponse,
} from './types'

import { ConsoleApiError, createDownload, createRequest } from './http'

const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8083'

/**
 * 가맹점 콘솔 API가 돌려주는 오류다. 공통 동작은 [ConsoleApiError]에 있고(admin 콘솔도 같은
 * 모양), 이 앱은 이름만 자기 것으로 갖는다 — 화면 코드가 `instanceof MerchantApiError`로
 * 분기할 때 어느 앱의 오류인지 드러나게 하기 위해서다(payment의 `CheckoutApiError`에 대응).
 */
export class MerchantApiError extends ConsoleApiError {
	constructor(status: number, message: string, options?: ErrorOptions) {
		super(status, message, options)
		this.name = 'MerchantApiError'
	}
}

/**
 * 공통 HTTP 계층(`http.ts`)에 이 앱의 설정을 주입해 만든 요청 함수다 — 세션 쿠키·CSRF·오류
 * 변환은 전부 거기서 처리한다(payment와 결정적으로 다른 지점은 세션 쿠키를 실어 보낸다는 것).
 * CSRF 부트스트랩은 `GET /merchant/me`를 쓴다.
 */
const downloadConfig = {
	baseUrl: BASE_URL,
	csrfBootstrapPath: '/merchant/me',
	createError: (status: number, message: string, options?: ErrorOptions) => new MerchantApiError(status, message, options),
}

/** 파일 다운로드 전용 요청. JSON이 아니라 Blob과 응답 헤더를 읽는다(`http.ts` 참고). */
const download = createDownload(downloadConfig)

const request = createRequest({
	baseUrl: BASE_URL,
	csrfBootstrapPath: '/merchant/me',
	createError: (status, message, options) => new MerchantApiError(status, message, options),
})

export const merchantApi = {
	/**
	 * 현재 세션 사용자를 조회한다. 미인증(401)은 오류가 아니라 "로그아웃 상태"이므로
	 * `null`로 바꿔 돌려준다 — 이 GET이 CSRF 토큰 쿠키 발급도 겸한다.
	 */
	me: async (): Promise<MeResponse | null> => {
		try {
			return await request<MeResponse>('/merchant/me')
		} catch (error) {
			if (error instanceof MerchantApiError && error.isUnauthorized) {
				return null
			}
			throw error
		}
	},

	login: (body: LoginRequest) =>
		request<LoginResponse>('/merchant/login', { method: 'POST', body: JSON.stringify(body) }),

	logout: () => request<void>('/merchant/logout', { method: 'POST' }),

	listApiKeys: () => request<ListApiKeysResponse>('/merchant/api-keys'),

	issueApiKey: (body: IssueApiKeyRequest) =>
		request<IssueApiKeyResponse>('/merchant/api-keys', { method: 'POST', body: JSON.stringify(body) }),

	revokeApiKey: (merchantApiKeyId: string) =>
		request<RevokeApiKeyResponse>(`/merchant/api-keys/${encodeURIComponent(merchantApiKeyId)}`, { method: 'DELETE' }),

	/**
	 * 결제 내역(**자기 가맹점만**, 생성 시각 최신순). 인증된 가맹점 사용자 전원(VIEWER 포함)이
	 * 조회할 수 있다. 조회 범위는 세션의 가맹점으로 서버가 고정하므로 `merchantId`를 보내지
	 * 않는다(`docs/architecture/merchant-console-api.md`의 4.1).
	 */
	listPayments: (filters: PaymentListFilters = {}) =>
		request<ListPaymentsResponse>(`/merchant/payments${paymentQueryString(filters)}`),

	/**
	 * 현재 필터에 걸린 **자기 가맹점** 결제를 `.xlsx`로 받는다. 페이징 파라미터는 보내지
	 * 않는다 — 내보내기는 조건 전체가 대상이다(서버가 최대 10,000행에서 자르고, 잘렸으면
	 * 응답의 `truncated`로 알려준다).
	 */
	exportPayments: (filters: PaymentListFilters = {}) =>
		download(`/merchant/payments/export${paymentQueryString({ ...filters, page: undefined, size: undefined })}`),

	/** 정산 채권(정산 예정일 최신순). 응답의 totalNetAmount는 필터 전체의 합계다. */
	listSettlementReceivables: (filters: SettlementFilters = {}) =>
		request<ListSettlementReceivablesResponse>(`/merchant/settlement-receivables${settlementQueryString(filters)}`),

	listMerchantUsers: () => request<ListMerchantUsersResponse>('/merchant/merchant-users'),

	inviteSubAccount: (body: InviteSubAccountRequest) =>
		request<InviteSubAccountResponse>('/merchant/merchant-users', { method: 'POST', body: JSON.stringify(body) }),

	/**
	 * 초대 수락(계정 활성화). **비인증 경로이고 백엔드가 CSRF 예외로 두고 있다** —
	 * 자격증명이 세션 쿠키가 아니라 본문의 초대 Token 자체이기 때문이다
	 * (`docs/architecture/merchant-console-api.md` 2절). `request()`가 POST에 CSRF
	 * 헤더를 실어 보내지만 서버가 무시하므로 특별히 분기하지 않는다.
	 */
	acceptInvitation: (body: AcceptInvitationRequest) =>
		request<AcceptInvitationResponse>('/merchant/account-invitations/accept', {
			method: 'POST',
			body: JSON.stringify(body),
		}),

	/** 정지·재개·종료. 세 경로가 요청·응답 형태를 공유해서 하나로 다룬다. */
	changeMerchantUserStatus: (merchantUserId: string, action: MerchantUserStatusAction) =>
		request<ChangeUserStatusResponse>(
			`/merchant/merchant-users/${encodeURIComponent(merchantUserId)}/${action}`,
			{ method: 'POST' },
		),

	changeMerchantUserRole: (merchantUserId: string, role: MerchantUserRole) =>
		request<ChangeUserRoleResponse>(`/merchant/merchant-users/${encodeURIComponent(merchantUserId)}/role`, {
			method: 'POST',
			body: JSON.stringify({ role }),
		}),

	/** 새 초대 Token을 발급한다 — **이전 초대 링크는 이 시점에 무효가 된다.** */
	resendInvitation: (merchantUserId: string) =>
		request<ResendInvitationResponse>(
			`/merchant/merchant-users/${encodeURIComponent(merchantUserId)}/invitation/resend`,
			{ method: 'POST' },
		),

	/** 초대 Token만 무효화한다. 계정은 INVITED로 남는다(종료와 분리된 동작). */
	revokeInvitation: (merchantUserId: string) =>
		request<RevokeInvitationResponse>(
			`/merchant/merchant-users/${encodeURIComponent(merchantUserId)}/invitation/revoke`,
			{ method: 'POST' },
		),
}

/**
 * 결제 내역 필터를 쿼리스트링으로 만든다. **값이 없거나 빈 문자열인 항목은 넣지 않는다** —
 * 서버가 빈 값을 "필터 없음"으로 처리하긴 하지만, URL에 남으면 캐시 키가 불필요하게 갈린다.
 * `page`/`size`는 0도 유효한 값이라 `undefined`만 걸러낸다.
 */
export function paymentQueryString(filters: PaymentListFilters): string {
	const params = new URLSearchParams()
	if (filters.status) params.set('status', filters.status)
	if (filters.from) params.set('from', filters.from)
	if (filters.to) params.set('to', filters.to)
	if (filters.page !== undefined) params.set('page', String(filters.page))
	if (filters.size !== undefined) params.set('size', String(filters.size))
	const query = params.toString()
	return query ? `?${query}` : ''
}

/**
 * 정산 채권 필터를 쿼리스트링으로 만든다. 빈 값은 넣지 않는다(캐시 키가 불필요하게 갈린다).
 * `page`/`size`는 0도 유효한 값이라 `undefined`만 걸러낸다.
 */
export function settlementQueryString(filters: SettlementFilters): string {
	const params = new URLSearchParams()

	if (filters.status) params.set('status', filters.status)
	if (filters.eligibleFrom) params.set('eligibleFrom', filters.eligibleFrom)
	if (filters.eligibleTo) params.set('eligibleTo', filters.eligibleTo)
	if (filters.page !== undefined) params.set('page', String(filters.page))
	if (filters.size !== undefined) params.set('size', String(filters.size))
	const query = params.toString()
	return query ? `?${query}` : ''
}
