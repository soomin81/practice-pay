import type {
	CancelResponse,
	CheckoutSession,
	CheckoutStatus,
	ConnectWalletResponse,
	SubmitCustomerResponse,
	SubmitTransactionResponse,
} from './types'

const BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081'

/**
 * 체크아웃 API가 돌려주는 오류다.
 *
 * **[status]를 그대로 들고 다니는 것이 핵심이다.** 계약
 * (`docs/architecture/checkout-api.md`의 5절)이 만료를 `409`가 아니라 `410`으로
 * 구분하는 이유가 바로 프론트에서 만료 전용 화면을 그리기 위해서다 — 상태 코드를
 * 뭉개면 그 구분이 사라진다.
 *
 * 오류 응답은 생성된 스펙에 없다(MockMvc가 컨테이너 오류 디스패치를 재현하지 못해
 * 잘못 문서화될 위험이 있어 백엔드가 의도적으로 뺐다). 그래서 이 형태만 손으로 적는다.
 */
export class CheckoutApiError extends Error {
	// 생성자 파라미터 프로퍼티(`constructor(readonly status: number)`)를 쓰지 않는다 —
	// 이 프로젝트의 tsconfig가 `erasableSyntaxOnly`를 켜 두어서(타입만 지우면 바로
	// 실행되는 문법만 허용) 그 문법이 금지된다.
	readonly status: number

	constructor(status: number, message: string, options?: ErrorOptions) {
		super(message, options)
		this.name = 'CheckoutApiError'
		this.status = status
	}

	/** 세션·견적이 만료됐다. 되돌릴 수 없으므로 만료 화면으로 간다. */
	get isExpired(): boolean {
		return this.status === 410
	}

	/** 지금 상태에서 허용되지 않는 요청(지갑 재연결, 제출 후 취소 등). */
	get isConflict(): boolean {
		return this.status === 409
	}

	get isNotFound(): boolean {
		return this.status === 404
	}
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
	let response: Response
	try {
		response = await fetch(`${BASE_URL}${path}`, {
			...init,
			headers: { 'Content-Type': 'application/json', ...init?.headers },
		})
	} catch (cause) {
		// 네트워크 자체가 실패한 경우다(백엔드가 안 떠 있거나 CORS로 막힘).
		// 상태 코드가 없으므로 0으로 구분한다.
		throw new CheckoutApiError(0, '결제 서버에 연결하지 못했습니다.', { cause })
	}

	if (!response.ok) {
		const message = await readErrorMessage(response)
		throw new CheckoutApiError(response.status, message)
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

export const checkoutApi = {
	getSession: (sessionId: string) => request<CheckoutSession>(`/checkout/sessions/${encodeURIComponent(sessionId)}`),

	getStatus: (sessionId: string) =>
		request<CheckoutStatus>(`/checkout/sessions/${encodeURIComponent(sessionId)}/status`),

	/**
	 * 구매자 정보(이름·이메일·휴대전화)를 보낸다 — 지갑 연결보다 앞선 단계다(계약 4.3).
	 *
	 * **응답에는 마스킹된 값만 온다.** 방금 입력한 본인에게 돌려주는 값이지만 서버가 원문을
	 * 싣지 않기로 했으므로, 확인 표시에는 그 마스킹 값을 그대로 쓴다.
	 */
	submitCustomer: (sessionId: string, customer: { name: string; email: string; phone: string }) =>
		request<SubmitCustomerResponse>(`/checkout/sessions/${encodeURIComponent(sessionId)}/customer`, {
			method: 'POST',
			body: JSON.stringify(customer),
		}),

	connectWallet: (sessionId: string, walletAddress: string) =>
		request<ConnectWalletResponse>(`/checkout/sessions/${encodeURIComponent(sessionId)}/wallet`, {
			method: 'POST',
			body: JSON.stringify({ walletAddress }),
		}),

	submitTransaction: (sessionId: string, transactionHash: string) =>
		request<SubmitTransactionResponse>(`/checkout/sessions/${encodeURIComponent(sessionId)}/transaction`, {
			method: 'POST',
			body: JSON.stringify({ transactionHash }),
		}),

	cancel: (sessionId: string) =>
		request<CancelResponse>(`/checkout/sessions/${encodeURIComponent(sessionId)}/cancel`, { method: 'POST' }),
}
