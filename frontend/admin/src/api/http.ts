/**
 * 세션 쿠키 SPA가 공유하는 HTTP 계층이다 — `client.ts`가 이걸 조립해서 엔드포인트 함수만
 * 남긴다(원래 `client.ts` 하나에 오류 타입·CSRF·fetch 래퍼가 엔드포인트와 뒤섞여 있었다).
 *
 * **merchant 콘솔의 같은 파일과 거의 동일하다** — 두 앱이 워크스페이스를 쓰지 않는 독립
 * 프로젝트라(`frontend/CLAUDE.md`) 지금은 각 앱에 복제해 둔다. 세 번째 소비자가 생기거나
 * 워크스페이스를 도입하면 `frontend/packages/`로 승격한다.
 */

/** 상태 변경 메서드 — 이 메서드에만 CSRF 토큰을 실어 보낸다. */
const MUTATING_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

function readCookie(name: string): string | null {
	const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`))
	return match ? decodeURIComponent(match[1]) : null
}

async function readErrorMessage(response: Response): Promise<string> {
	try {
		const body = (await response.json()) as { message?: string }
		return body.message ?? `요청이 실패했습니다 (HTTP ${response.status})`
	} catch {
		return `요청이 실패했습니다 (HTTP ${response.status})`
	}
}

/**
 * 콘솔 API가 돌려주는 오류의 공통 형태다.
 *
 * **[status]를 그대로 들고 다니는 것이 핵심이다** — 세션 쿠키 인증이라 `401`(로그아웃)과
 * `403`(CSRF 실패/권한 없음)을 구분해서 다뤄야 한다. 오류 응답 본문은 OpenAPI 스펙에 없어
 * (MockMvc가 오류 디스패치를 재현 못 함) 이 형태만 손으로 적는다.
 *
 * 각 앱은 이 클래스를 그대로 쓰지 않고 자기 이름의 하위 클래스를 노출한다(`AdminApiError`) —
 * `instanceof`로 분기하는 화면 코드가 어느 앱의 오류인지 드러나게 하기 위해서다.
 */
export class ConsoleApiError extends Error {
	// 생성자 파라미터 프로퍼티를 쓰지 않는다 — tsconfig의 `erasableSyntaxOnly`가 금지한다.
	readonly status: number

	constructor(status: number, message: string, options?: ErrorOptions) {
		super(message, options)
		this.name = 'ConsoleApiError'
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

	/** 이미 사용 중인 값 등(예: 가맹점 코드·로그인 아이디 중복). */
	get isConflict(): boolean {
		return this.status === 409
	}
}

/** [createRequest]가 앱마다 다르게 받는 설정이다. */
export interface HttpConfig {
	/** API 서버 주소(예: `http://localhost:8082`). */
	baseUrl: string
	/**
	 * `XSRF-TOKEN` 쿠키가 아직 없을 때 한 번 쳐 볼 안전한 GET 경로(예: `/admin/me`).
	 * 백엔드가 그 응답에 쿠키를 실어 준다.
	 */
	csrfBootstrapPath: string
	/** 상태 코드와 메시지로 앱 고유 오류를 만든다(`AdminApiError` 등). */
	createError: (status: number, message: string, options?: ErrorOptions) => ConsoleApiError
}

/**
 * 앱 설정을 받아 `request` 함수를 만든다.
 *
 * 공통 동작: 세션 쿠키를 교차 출처로 실어 보내고(`credentials: 'include'`), 상태 변경
 * 요청에는 `XSRF-TOKEN` 쿠키를 `X-XSRF-TOKEN` 헤더로 되돌려주며(Spring Security 6 SPA
 * 레시피), 네트워크 실패는 status `0`, 실패 응답은 그 상태 코드로 오류를 던진다.
 * `204`(로그아웃 등)는 본문이 없어 `undefined`를 돌려준다.
 */
export function createRequest(config: HttpConfig) {
	async function csrfHeader(): Promise<Record<string, string>> {
		let token = readCookie('XSRF-TOKEN')
		if (!token) {
			try {
				await fetch(`${config.baseUrl}${config.csrfBootstrapPath}`, { credentials: 'include' })
			} catch {
				// 아래에서 토큰이 여전히 없으면 헤더 없이 보내고, 서버가 403으로 알려준다.
			}
			token = readCookie('XSRF-TOKEN')
		}
		return token ? { 'X-XSRF-TOKEN': token } : {}
	}

	return async function request<T>(path: string, init?: RequestInit): Promise<T> {
		const method = (init?.method ?? 'GET').toUpperCase()
		const csrf = MUTATING_METHODS.has(method) ? await csrfHeader() : {}

		let response: Response
		try {
			response = await fetch(`${config.baseUrl}${path}`, {
				...init,
				credentials: 'include',
				headers: { 'Content-Type': 'application/json', ...csrf, ...init?.headers },
			})
		} catch (cause) {
			// 네트워크 자체 실패(서버가 안 떠 있거나 CORS로 막힘). 상태 코드가 없으므로 0.
			throw config.createError(0, '콘솔 서버에 연결하지 못했습니다.', { cause })
		}

		if (!response.ok) {
			throw config.createError(response.status, await readErrorMessage(response))
		}

		if (response.status === 204) {
			return undefined as T
		}
		return (await response.json()) as T
	}
}

/** 서버가 내려준 파일 하나. [truncated]가 `true`면 상한에 걸려 **일부만 담긴 파일**이다. */
export type DownloadedFile = {
	blob: Blob
	fileName: string | null
	truncated: boolean
}

/**
 * 파일 다운로드용 요청을 만든다. [createRequest]와 나눈 이유는 응답 처리 방식이 아예 달라서다 —
 * JSON이 아니라 `Blob`을 읽고, 본문이 아니라 **응답 헤더**에서 메타데이터를 가져온다.
 *
 * **`<a href>`로 바로 받지 않고 `fetch`를 거치는 이유** 둘:
 *  1. 세션 쿠키를 실으려면 `credentials: 'include'`가 필요하다.
 *  2. **잘림 여부가 헤더에만 있다.** 링크로 받으면 브라우저가 파일만 저장하고 헤더는
 *     화면 코드에 닿지 않아, 사용자가 일부만 담긴 파일을 그냥 받아가게 된다.
 *
 * 두 헤더 모두 백엔드 CORS의 `exposedHeaders`에 있어야 읽힌다 — 교차 출처에서는 기본적으로
 * 몇 개의 표준 헤더만 JS에 노출된다.
 */
export function createDownload(config: HttpConfig) {
	return async function download(path: string): Promise<DownloadedFile> {
		let response: Response
		try {
			response = await fetch(`${config.baseUrl}${path}`, { credentials: 'include' })
		} catch (cause) {
			throw config.createError(0, '콘솔 서버에 연결하지 못했습니다.', { cause })
		}

		if (!response.ok) {
			throw config.createError(response.status, await readErrorMessage(response))
		}

		return {
			blob: await response.blob(),
			fileName: fileNameFrom(response.headers.get('Content-Disposition')),
			truncated: response.headers.get('X-Export-Truncated') === 'true',
		}
	}
}

/** `attachment; filename="payments-20260801-153000.xlsx"`에서 이름만 꺼낸다. */
function fileNameFrom(contentDisposition: string | null): string | null {
	const match = contentDisposition?.match(/filename="?([^";]+)"?/)
	return match ? match[1] : null
}

/**
 * 받은 파일을 브라우저에 저장시킨다. 임시 object URL은 **반드시 해제한다** — 안 하면
 * 탭이 살아 있는 동안 blob이 메모리에 남는다.
 */
export function saveFile(file: DownloadedFile, fallbackName: string): void {
	const url = URL.createObjectURL(file.blob)
	try {
		const anchor = document.createElement('a')
		anchor.href = url
		anchor.download = file.fileName ?? fallbackName
		document.body.appendChild(anchor)
		anchor.click()
		anchor.remove()
	} finally {
		URL.revokeObjectURL(url)
	}
}
