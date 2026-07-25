import type {
	AcceptInvitationRequest,
	AcceptInvitationResponse,
	IssueInternalUserRequest,
	IssueInternalUserResponse,
	ListInternalUsersResponse,
	ListMerchantsResponse,
	LoginRequest,
	LoginResponse,
	MeResponse,
	RegisterMerchantRequest,
	RegisterMerchantResponse,
} from './types'

const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8082'

/**
 * 내부 운영자 콘솔 API가 돌려주는 오류다(merchant 앱의 `MerchantApiError`와 같은 모양).
 *
 * **[status]를 그대로 들고 다니는 것이 핵심이다** — 세션 쿠키 인증이라 `401`(로그아웃)과
 * `403`(CSRF 실패/권한 없음)을 구분해서 다뤄야 한다. 오류 응답 본문은 스펙에 없어
 * (MockMvc가 오류 디스패치를 재현 못 함) 이 형태만 손으로 적는다.
 */
export class AdminApiError extends Error {
	// 생성자 파라미터 프로퍼티를 쓰지 않는다 — tsconfig의 `erasableSyntaxOnly`가 금지한다.
	readonly status: number

	constructor(status: number, message: string, options?: ErrorOptions) {
		super(message, options)
		this.name = 'AdminApiError'
		this.status = status
	}

	get isUnauthorized(): boolean {
		return this.status === 401
	}

	get isForbidden(): boolean {
		return this.status === 403
	}

	/** 이미 사용 중인 가맹점 코드/로그인 아이디 등. */
	get isConflict(): boolean {
		return this.status === 409
	}
}

const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

function readCookie(name: string): string | null {
	const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
	return match ? decodeURIComponent(match[1]) : null
}

/**
 * 상태 변경 요청에 실을 CSRF 헤더를 만든다. 백엔드가 `XSRF-TOKEN` 쿠키를 내리고 그 값을
 * `X-XSRF-TOKEN` 헤더로 되돌려받아야 통과시킨다(Spring Security 6 SPA 레시피).
 * 쿠키는 안전한 GET(`GET /admin/me`) 응답에 실려 오는데, 앱 부팅 시 `useMe`가 그 GET을
 * 먼저 하므로 보통은 이미 있다. 없으면 여기서 한 번 받아온다.
 */
async function csrfHeader(): Promise<Record<string, string>> {
	let token = readCookie('XSRF-TOKEN')
	if (!token) {
		try {
			await fetch(`${BASE_URL}/admin/me`, { credentials: 'include' })
		} catch {
			// 아래에서 토큰이 여전히 없으면 헤더 없이 보내고, 서버가 403으로 알려준다.
		}
		token = readCookie('XSRF-TOKEN')
	}
	return token ? { 'X-XSRF-TOKEN': token } : {}
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
	const method = (init?.method ?? 'GET').toUpperCase()
	const csrf = MUTATING_METHODS.has(method) ? await csrfHeader() : {}

	let response: Response
	try {
		response = await fetch(`${BASE_URL}${path}`, {
			...init,
			// 세션 쿠키를 교차 출처로 실어 보낸다.
			credentials: 'include',
			headers: { 'Content-Type': 'application/json', ...csrf, ...init?.headers },
		})
	} catch (cause) {
		throw new AdminApiError(0, '콘솔 서버에 연결하지 못했습니다.', { cause })
	}

	if (!response.ok) {
		throw new AdminApiError(response.status, await readErrorMessage(response))
	}

	// 204(로그아웃)는 본문이 없다.
	if (response.status === 204) {
		return undefined as T
	}
	return (await response.json()) as T
}

async function readErrorMessage(response: Response): Promise<string> {
	try {
		const body = (await response.json()) as { message?: string }
		return body.message ?? `요청이 실패했습니다 (HTTP ${response.status})`
	} catch {
		return `요청이 실패했습니다 (HTTP ${response.status})`
	}
}

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

	listInternalUsers: () => request<ListInternalUsersResponse>('/admin/internal-users'),

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
}
