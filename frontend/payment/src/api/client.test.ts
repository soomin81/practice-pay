import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { CheckoutApiError, checkoutApi } from './client'

/**
 * API 클라이언트 테스트.
 *
 * **여기서 지키려는 것은 "상태 코드를 뭉개지 않는다"이다.** 계약
 * (`docs/architecture/checkout-api.md` 5절)이 만료를 굳이 `409`가 아니라 `410`으로
 * 분리한 이유가 프론트에서 만료 전용 화면을 그리기 위해서다 — 이 구분이 무너지면
 * 만료된 결제에 "다시 시도" 화면이 뜨는 식으로 조용히 잘못된 안내가 나간다.
 */

const BASE = 'http://localhost:8081'

function jsonResponse(status: number, body: unknown): Response {
	return new Response(JSON.stringify(body), {
		status,
		headers: { 'Content-Type': 'application/json' },
	})
}

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
	fetchMock = vi.fn()
	vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
	vi.unstubAllGlobals()
})

describe('상태 코드 분류', () => {
	test.each([
		[404, 'isNotFound'],
		[409, 'isConflict'],
		[410, 'isExpired'],
	] as const)('%i는 %s로 분류된다', async (status, flag) => {
		fetchMock.mockResolvedValue(jsonResponse(status, { message: '서버 메시지' }))

		const error = await checkoutApi.getSession('cs_1').catch((cause: unknown) => cause)

		expect(error).toBeInstanceOf(CheckoutApiError)
		const apiError = error as CheckoutApiError
		expect(apiError.status).toBe(status)
		expect(apiError[flag]).toBe(true)
	})

	test('410(만료)과 409(충돌)는 서로 섞이지 않는다', async () => {
		fetchMock.mockResolvedValue(jsonResponse(410, { message: '만료' }))
		const expired = (await checkoutApi.getSession('cs_1').catch((c: unknown) => c)) as CheckoutApiError

		fetchMock.mockResolvedValue(jsonResponse(409, { message: '충돌' }))
		const conflict = (await checkoutApi.getSession('cs_1').catch((c: unknown) => c)) as CheckoutApiError

		expect(expired.isExpired).toBe(true)
		expect(expired.isConflict).toBe(false)
		expect(conflict.isConflict).toBe(true)
		expect(conflict.isExpired).toBe(false)
	})

	test('네트워크 자체가 실패하면 status 0으로 구분한다', async () => {
		// 백엔드가 안 떠 있거나 CORS로 막힌 경우다 — 상태 코드가 존재하지 않는다.
		fetchMock.mockRejectedValue(new TypeError('Failed to fetch'))

		const error = (await checkoutApi.getSession('cs_1').catch((c: unknown) => c)) as CheckoutApiError

		expect(error).toBeInstanceOf(CheckoutApiError)
		expect(error.status).toBe(0)
		expect(error.isNotFound).toBe(false)
		expect(error.isExpired).toBe(false)
		expect(error.message).toBe('결제 서버에 연결하지 못했습니다.')
	})
})

describe('오류 메시지', () => {
	test('서버가 준 message를 그대로 쓴다', async () => {
		fetchMock.mockResolvedValue(jsonResponse(400, { message: '지갑 주소 형식이 올바르지 않습니다' }))

		const error = (await checkoutApi.getSession('cs_1').catch((c: unknown) => c)) as CheckoutApiError

		expect(error.message).toBe('지갑 주소 형식이 올바르지 않습니다')
	})

	test('본문이 JSON이 아니어도 터지지 않는다', async () => {
		// 컨테이너가 내려주는 HTML 오류 페이지 같은 경우다.
		fetchMock.mockResolvedValue(new Response('<html>500</html>', { status: 500 }))

		const error = (await checkoutApi.getSession('cs_1').catch((c: unknown) => c)) as CheckoutApiError

		expect(error.status).toBe(500)
		expect(error.message).toContain('500')
	})

	test('message 필드가 없으면 상태 코드로 안내한다', async () => {
		fetchMock.mockResolvedValue(jsonResponse(503, {}))

		const error = (await checkoutApi.getSession('cs_1').catch((c: unknown) => c)) as CheckoutApiError

		expect(error.message).toContain('503')
	})
})

describe('요청 구성', () => {
	test('세션 조회는 GET으로 경로를 만든다', async () => {
		fetchMock.mockResolvedValue(jsonResponse(200, { checkoutSessionId: 'cs_1' }))

		await checkoutApi.getSession('cs_1')

		expect(fetchMock).toHaveBeenCalledWith(`${BASE}/checkout/sessions/cs_1`, expect.anything())
	})

	test('세션 식별자를 URL 인코딩한다', async () => {
		fetchMock.mockResolvedValue(jsonResponse(200, {}))

		await checkoutApi.getStatus('cs_1/../../admin')

		const [url] = fetchMock.mock.calls[0]
		// 인코딩하지 않으면 경로를 벗어나는 요청이 만들어진다.
		expect(url).not.toContain('/../')
		expect(url).toBe(`${BASE}/checkout/sessions/cs_1%2F..%2F..%2Fadmin/status`)
	})

	test('지갑 연결은 walletAddress를 본문에 담아 POST한다', async () => {
		fetchMock.mockResolvedValue(jsonResponse(200, {}))

		await checkoutApi.connectWallet('cs_1', '0xabc')

		const [url, init] = fetchMock.mock.calls[0]
		expect(url).toBe(`${BASE}/checkout/sessions/cs_1/wallet`)
		expect(init.method).toBe('POST')
		expect(JSON.parse(init.body)).toEqual({ walletAddress: '0xabc' })
		expect(init.headers['Content-Type']).toBe('application/json')
	})

	test('전송 제출은 transactionHash를 본문에 담아 POST한다', async () => {
		fetchMock.mockResolvedValue(jsonResponse(200, {}))

		await checkoutApi.submitTransaction('cs_1', '0xhash')

		const [url, init] = fetchMock.mock.calls[0]
		expect(url).toBe(`${BASE}/checkout/sessions/cs_1/transaction`)
		expect(JSON.parse(init.body)).toEqual({ transactionHash: '0xhash' })
	})

	test('취소는 본문 없이 POST한다', async () => {
		fetchMock.mockResolvedValue(jsonResponse(200, { checkoutSessionStatus: 'CANCELLED' }))

		await checkoutApi.cancel('cs_1')

		const [url, init] = fetchMock.mock.calls[0]
		expect(url).toBe(`${BASE}/checkout/sessions/cs_1/cancel`)
		expect(init.method).toBe('POST')
		expect(init.body).toBeUndefined()
	})

	test('성공 응답은 파싱해서 돌려준다', async () => {
		fetchMock.mockResolvedValue(jsonResponse(200, { checkoutSessionId: 'cs_1', checkoutSessionStatus: 'OPEN' }))

		const session = await checkoutApi.getSession('cs_1')

		expect(session).toMatchObject({ checkoutSessionId: 'cs_1', checkoutSessionStatus: 'OPEN' })
	})
})
