import type {
	AcceptInvitationRequest,
	AcceptInvitationResponse,
	ChangeInternalUserRoleResponse,
	ChangeInternalUserStatusResponse,
	ChangeMerchantUserRoleResponse,
	ChangeMerchantUserStatusResponse,
	InternalUserRole,
	InternalUserStatusAction,
	IssueInternalUserRequest,
	IssueInternalUserResponse,
	ListInternalUsersResponse,
	ListLoginAuditResponse,
	ListMerchantLoginAuditResponse,
	ListMerchantUsersResponse,
	ListMerchantsResponse,
	ListPaymentsResponse,
	PaymentDetailResponse,
	ListSettlementReceivablesResponse,
	SettlementFilters,
	PaymentListFilters,
	MerchantUserRole,
	MerchantUserStatusAction,
	LoginRequest,
	LoginResponse,
	MeResponse,
	RegisterMerchantRequest,
	RegisterMerchantResponse,
} from './types'

import { ConsoleApiError, createDownload, createRequest } from './http'

const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8082'

/**
 * 내부 운영자 콘솔 API가 돌려주는 오류다. 공통 동작은 [ConsoleApiError]에 있고(merchant
 * 콘솔도 같은 모양), 이 앱은 이름만 자기 것으로 갖는다 — 화면 코드가 `instanceof
 * AdminApiError`로 분기할 때 어느 앱의 오류인지 드러나게 하기 위해서다.
 */
export class AdminApiError extends ConsoleApiError {
	constructor(status: number, message: string, options?: ErrorOptions) {
		super(status, message, options)
		this.name = 'AdminApiError'
	}
}

/**
 * 공통 HTTP 계층(`http.ts`)에 이 앱의 설정을 주입해 만든 요청 함수다 — 세션 쿠키·CSRF·오류
 * 변환은 전부 거기서 처리한다. CSRF 부트스트랩은 `GET /admin/me`를 쓴다(그 응답에 백엔드가
 * `XSRF-TOKEN` 쿠키를 실어 준다).
 */
const downloadConfig = {
	baseUrl: BASE_URL,
	csrfBootstrapPath: '/admin/me',
	createError: (status: number, message: string, options?: ErrorOptions) => new AdminApiError(status, message, options),
}

/** 파일 다운로드 전용 요청. JSON이 아니라 Blob과 응답 헤더를 읽는다(`http.ts` 참고). */
const download = createDownload(downloadConfig)

const request = createRequest({
	baseUrl: BASE_URL,
	csrfBootstrapPath: '/admin/me',
	createError: (status, message, options) => new AdminApiError(status, message, options),
})

export const adminApi = {
	/**
	 * 현재 세션 사용자를 조회한다. 미인증(401)은 오류가 아니라 "로그아웃 상태"이므로
	 * `null`로 바꿔 돌려준다 — 이 GET이 CSRF 토큰 쿠키 발급도 겸한다.
	 */
	me: async (): Promise<MeResponse | null> => {
		try {
			return await request<MeResponse>('/admin/me')
		} catch (error) {
			if (error instanceof AdminApiError && error.isUnauthorized) {
				return null
			}
			throw error
		}
	},

	login: (body: LoginRequest) => request<LoginResponse>('/admin/login', { method: 'POST', body: JSON.stringify(body) }),

	logout: () => request<void>('/admin/logout', { method: 'POST' }),

	listMerchants: () => request<ListMerchantsResponse>('/admin/merchants'),

	registerMerchant: (body: RegisterMerchantRequest) =>
		request<RegisterMerchantResponse>('/admin/merchants', { method: 'POST', body: JSON.stringify(body) }),

	/**
	 * 결제 내역(전 가맹점, 생성 시각 최신순). 인증된 내부 사용자 전원이 조회할 수 있다.
	 *
	 * **빈 값은 파라미터에서 아예 뺀다** — 서버도 빈 문자열을 "필터 없음"으로 처리하지만,
	 * 쿼리스트링에 `status=`가 남으면 react-query 캐시 키가 갈려 같은 조회가 두 번 나간다.
	 */
	listPayments: (filters: PaymentListFilters = {}) =>
		request<ListPaymentsResponse>(`/admin/payments${paymentQueryString(filters)}`),

	/** 결제 한 건의 전체 맥락. 없으면 404(AdminApiError.status로 분기한다). */
	getPaymentDetail: (paymentId: string) =>
		request<PaymentDetailResponse>(`/admin/payments/${encodeURIComponent(paymentId)}`),

	/**
	 * 현재 필터에 걸린 결제를 `.xlsx`로 받는다. **페이징 파라미터는 보내지 않는다** —
	 * 내보내기는 페이지가 아니라 조건 전체가 대상이다(서버가 최대 10,000행에서 자르고,
	 * 잘렸으면 응답의 `truncated`로 알려준다).
	 */
	exportPayments: (filters: PaymentListFilters = {}) =>
		download(`/admin/payments/export${paymentQueryString({ ...filters, page: undefined, size: undefined })}`),

	/** 정산 채권(정산 예정일 최신순). 응답의 totalNetAmount는 필터 전체의 합계다. */
	listSettlementReceivables: (filters: SettlementFilters = {}) =>
		request<ListSettlementReceivablesResponse>(`/admin/settlement-receivables${settlementQueryString(filters)}`),

	listInternalUsers: () => request<ListInternalUsersResponse>('/admin/internal-users'),

	/** 로그인 감사 로그(최근 시도, 최신순). SUPER_ADMIN 전용 — 서버도 403으로 막는다. */
	listLoginAudit: () => request<ListLoginAuditResponse>('/admin/login-audit'),

	/** 가맹점 로그인 감사 로그(전 가맹점, 최신순). SUPER_ADMIN/OPERATOR 전용 — 서버도 403으로 막는다. */
	listMerchantLoginAudit: () => request<ListMerchantLoginAuditResponse>('/admin/merchant-login-audit'),

	issueInternalUser: (body: IssueInternalUserRequest) =>
		request<IssueInternalUserResponse>('/admin/internal-users', { method: 'POST', body: JSON.stringify(body) }),

	/**
	 * 내부 운영자 초대 수락. **비인증 경로이고 백엔드가 CSRF 예외로 둔다**(자격증명이 세션
	 * 쿠키가 아니라 본문의 Token 자체다). 가맹점 사용자 초대는 이 엔드포인트가 아니라
	 * api-merchant의 같은 경로로 간다.
	 */
	acceptInvitation: (body: AcceptInvitationRequest) =>
		request<AcceptInvitationResponse>('/admin/account-invitations/accept', {
			method: 'POST',
			body: JSON.stringify(body),
		}),

	/** 정지·재개·종료. 세 경로가 요청·응답 형태를 공유해서 하나로 다룬다. */
	changeInternalUserStatus: (internalUserId: string, action: InternalUserStatusAction) =>
		request<ChangeInternalUserStatusResponse>(
			`/admin/internal-users/${encodeURIComponent(internalUserId)}/${action}`,
			{ method: 'POST' },
		),

	changeInternalUserRole: (internalUserId: string, role: InternalUserRole) =>
		request<ChangeInternalUserRoleResponse>(`/admin/internal-users/${encodeURIComponent(internalUserId)}/role`, {
			method: 'POST',
			body: JSON.stringify({ role }),
		}),

	/** 어느 가맹점의 사용자 명부를 조회한다(VIEWER 포함 인증된 내부 사용자 전원). */
	listMerchantUsers: (merchantId: string) =>
		request<ListMerchantUsersResponse>(`/admin/merchants/${encodeURIComponent(merchantId)}/users`),

	/** 가맹점 사용자 정지·재개·종료. 세 경로가 요청·응답 형태를 공유해서 하나로 다룬다. */
	changeMerchantUserStatus: (merchantId: string, merchantUserId: string, action: MerchantUserStatusAction) =>
		request<ChangeMerchantUserStatusResponse>(
			`/admin/merchants/${encodeURIComponent(merchantId)}/users/${encodeURIComponent(merchantUserId)}/${action}`,
			{ method: 'POST' },
		),

	changeMerchantUserRole: (merchantId: string, merchantUserId: string, role: MerchantUserRole) =>
		request<ChangeMerchantUserRoleResponse>(
			`/admin/merchants/${encodeURIComponent(merchantId)}/users/${encodeURIComponent(merchantUserId)}/role`,
			{ method: 'POST', body: JSON.stringify({ role }) },
		),
}

/**
 * 결제 내역 필터를 쿼리스트링으로 만든다. **값이 없거나 빈 문자열인 항목은 넣지 않는다** —
 * 서버가 빈 값을 "필터 없음"으로 처리하긴 하지만, URL에 남으면 캐시 키가 불필요하게 갈린다.
 * `page`/`size`는 0도 유효한 값이라 `undefined`만 걸러낸다.
 */
export function paymentQueryString(filters: PaymentListFilters): string {
	const params = new URLSearchParams()
	if (filters.merchantId) params.set('merchantId', filters.merchantId)
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
	if (filters.merchantId) params.set('merchantId', filters.merchantId)
	if (filters.status) params.set('status', filters.status)
	if (filters.eligibleFrom) params.set('eligibleFrom', filters.eligibleFrom)
	if (filters.eligibleTo) params.set('eligibleTo', filters.eligibleTo)
	if (filters.page !== undefined) params.set('page', String(filters.page))
	if (filters.size !== undefined) params.set('size', String(filters.size))
	const query = params.toString()
	return query ? `?${query}` : ''
}
