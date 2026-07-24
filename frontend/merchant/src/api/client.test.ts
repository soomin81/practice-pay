import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { merchantApi, MerchantApiError } from './client'

/** fetch 응답을 흉내 내는 최소 객체(Response 전역에 의존하지 않는다). */
function fakeResponse(status: number, body: unknown = {}) {
	return {
		ok: status >= 200 && status < 300,
		status,
		json: async () => body,
	}
}

describe('merchantApi client', () => {
	beforeEach(() => {
		// CSRF 토큰 쿠키가 이미 있는 상태(부팅 시 useMe가 받아 둔 상황).
		document.cookie = 'XSRF-TOKEN=tok-123'
	})

	afterEach(() => {
		vi.unstubAllGlobals()
		document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
	})

	it('me()는 401을 오류가 아니라 null(로그아웃)로 바꾼다', async () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(fakeResponse(401, { message: '인증이 필요합니다.' })))
		await expect(merchantApi.me()).resolves.toBeNull()
	})

	it('me() 성공은 사용자 정보를 그대로 돌려준다', async () => {
		const identity = { merchantUserId: 'mu_1', merchantId: 'mrc_1', loginId: 'owner01', role: 'OWNER' }
		vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(fakeResponse(200, identity)))
		await expect(merchantApi.me()).resolves.toEqual(identity)
	})

	it('상태 변경 요청은 X-XSRF-TOKEN 헤더와 credentials: include를 싣는다', async () => {
		const fetchMock = vi.fn().mockResolvedValueOnce(fakeResponse(201, {}))
		vi.stubGlobal('fetch', fetchMock)

		await merchantApi.issueApiKey({ keyName: 'k', scopes: ['PAYMENT_READ'] })

		const [, init] = fetchMock.mock.calls[0]
		expect(init.method).toBe('POST')
		expect(init.credentials).toBe('include')
		expect(init.headers['X-XSRF-TOKEN']).toBe('tok-123')
	})

	it('GET 조회에는 CSRF 헤더를 붙이지 않는다', async () => {
		const fetchMock = vi.fn().mockResolvedValueOnce(fakeResponse(200, { apiKeys: [] }))
		vi.stubGlobal('fetch', fetchMock)

		await merchantApi.listApiKeys()

		const [, init] = fetchMock.mock.calls[0]
		expect(init.credentials).toBe('include')
		expect(init.headers['X-XSRF-TOKEN']).toBeUndefined()
	})

	it('실패 응답은 status를 담은 MerchantApiError로 던진다(403/401 구분)', async () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(fakeResponse(403, { message: '권한이 없습니다.' })))
		const error = await merchantApi.listApiKeys().catch((caught: unknown) => caught)
		expect(error).toBeInstanceOf(MerchantApiError)
		expect((error as MerchantApiError).status).toBe(403)
		expect((error as MerchantApiError).isForbidden).toBe(true)
	})

	it('204(로그아웃)는 본문 없이 통과한다', async () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(fakeResponse(204)))
		await expect(merchantApi.logout()).resolves.toBeUndefined()
	})
})
