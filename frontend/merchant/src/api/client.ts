import type {
	AcceptInvitationRequest,
	AcceptInvitationResponse,
	ChangeUserRoleResponse,
	ChangeUserStatusResponse,
	InviteSubAccountRequest,
	MerchantUserRole,
	MerchantUserStatusAction,
	InviteSubAccountResponse,
	IssueApiKeyRequest,
	IssueApiKeyResponse,
	ListApiKeysResponse,
	ListMerchantUsersResponse,
	LoginRequest,
	LoginResponse,
	MeResponse,
	RevokeApiKeyResponse,
} from './types'

const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8083'

/**
 * 가맹점 콘솔 API가 돌려주는 오류다(payment의 `CheckoutApiError`에 대응).
 *
 * **[status]를 그대로 들고 다니는 것이 핵심이다** — 세션 쿠키 인증이라 프론트는
 * `401`(로그아웃)과 `403`(CSRF 실패/권한 없음)을 구분해서 다뤄야 한다. 상태 코드를
 * 뭉개면 그 구분이 사라진다. 오류 응답 본문은 스펙에 없어(MockMvc가 오류 디스패치를
 * 재현 못 함) 이 형태만 손으로 적는다(`docs/architecture/merchant-console-api.md` 5절).
 */
export class MerchantApiError extends Error {
	// 생성자 파라미터 프로퍼티를 쓰지 않는다 — 이 프로젝트 tsconfig의 `erasableSyntaxOnly`가
	// 그 문법을 금지한다(payment의 client.ts와 같은 이유).
	readonly status: number

	constructor(status: number, message: string, options?: ErrorOptions) {
		super(message, options)
		this.name = 'MerchantApiError'
		this.status = status
	}

	/** 미인증 — 로그인 화면으로 보낸다. */
	get isUnauthorized(): boolean {
		return this.status === 401
	}

	/** 권한 없음 또는 CSRF 토큰 문제. */
	get isForbidden(): boolean {
		return this.status === 403
	}

	/** 이미 사용 중인 값 등(예: loginId 중복). */
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
 * 상태 변경 요청에 실을 CSRF 헤더를 만든다.
 *
 * 백엔드는 `XSRF-TOKEN` 쿠키를 내리고 그 값을 `X-XSRF-TOKEN` 헤더로 되돌려받아야
 * 통과시킨다(Spring Security 6 SPA 레시피). 쿠키는 안전한 GET(`GET /merchant/me`)
 * 응답에 실려 오는데, 앱 부팅 시 `useMe`가 그 GET을 먼저 하므로 보통은 이미 있다.
 * 혹시 없으면 여기서 한 번 GET을 쳐서 받아온다(첫 로그인 직행 같은 경우).
 */
async function csrfHeader(): Promise<Record<string, string>> {
	let token = readCookie('XSRF-TOKEN')
	if (!token) {
		try {
			await fetch(`${BASE_URL}/merchant/me`, { credentials: 'include' })
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
			// 세션 쿠키를 교차 출처로 실어 보낸다 — payment와 결정적으로 다른 지점이다.
			credentials: 'include',
			headers: { 'Content-Type': 'application/json', ...csrf, ...init?.headers },
		})
	} catch (cause) {
		// 네트워크 자체 실패(서버가 안 떠 있거나 CORS로 막힘). 상태 코드가 없으므로 0.
		throw new MerchantApiError(0, '콘솔 서버에 연결하지 못했습니다.', { cause })
	}

	if (!response.ok) {
		throw new MerchantApiError(response.status, await readErrorMessage(response))
	}

	// 204(로그아웃 등)는 본문이 없다.
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
}
