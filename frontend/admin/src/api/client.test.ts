import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { adminApi, AdminApiError } from './client'

function fakeResponse(status: number, body: unknown = {}) {
	return { ok: status >= 200 && status < 300, status, json: async () => body }
}

describe('adminApi client', () => {
	beforeEach(() => {
		document.cookie = 'XSRF-TOKEN=tok-123'
	})

	afterEach(() => {
		vi.unstubAllGlobals()
		document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
	})

	it('me()는 401을 오류가 아니라 null(로그아웃)로 바꾼다', async () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(fakeResponse(401, { message: '인증이 필요합니다.' })))
		await expect(adminApi.me()).resolves.toBeNull()
	})

	it('상태 변경 요청은 X-XSRF-TOKEN과 credentials: include를 싣는다', async () => {
		const fetchMock = vi.fn().mockResolvedValueOnce(fakeResponse(201, {}))
		vi.stubGlobal('fetch', fetchMock)

		await adminApi.registerMerchant({
			merchantCode: 'X',
			merchantName: 'X',
			webhookUrl: null,
			ownerLoginId: 'x',
			ownerEmail: 'x@e.com',
			ownerUserName: 'X',
		})

		const [, init] = fetchMock.mock.calls[0]
		expect(init.method).toBe('POST')
		expect(init.credentials).toBe('include')
		expect(init.headers['X-XSRF-TOKEN']).toBe('tok-123')
	})

	it('GET 조회에는 CSRF 헤더를 붙이지 않는다', async () => {
		const fetchMock = vi.fn().mockResolvedValueOnce(fakeResponse(200, { merchants: [] }))
		vi.stubGlobal('fetch', fetchMock)

		await adminApi.listMerchants()

		const [, init] = fetchMock.mock.calls[0]
		expect(init.credentials).toBe('include')
		expect(init.headers['X-XSRF-TOKEN']).toBeUndefined()
	})

	it('실패 응답은 status를 담은 AdminApiError로 던진다', async () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(fakeResponse(403, { message: '권한이 없습니다.' })))
		const error = await adminApi.listMerchants().catch((caught: unknown) => caught)
		expect(error).toBeInstanceOf(AdminApiError)
		expect((error as AdminApiError).isForbidden).toBe(true)
	})

	it('204(로그아웃)는 본문 없이 통과한다', async () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(fakeResponse(204)))
		await expect(adminApi.logout()).resolves.toBeUndefined()
	})
})
