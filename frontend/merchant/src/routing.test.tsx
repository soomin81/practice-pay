import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { renderWithRouter } from '@/test-utils'
import App from '@/App'

/** fetch 응답을 흉내 내는 최소 객체. */
function fakeResponse(status: number, body: unknown = {}) {
	return { ok: status >= 200 && status < 300, status, json: async () => body }
}

/** 모든 요청에 401(미인증)을 돌려준다 — 로그아웃 상태를 재현한다. */
function stubUnauthenticated() {
	vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fakeResponse(401, { message: '인증이 필요합니다.' })))
}

describe('라우팅', () => {
	afterEach(() => {
		vi.unstubAllGlobals()
	})

	it('미인증 상태에서 /accept-invitation은 로그인 화면으로 튕기지 않는다', async () => {
		// 이 슬라이스에서 가장 깨지기 쉬운 지점이다 — 초대받은 사람은 아직 로그인할 수
		// 없으므로, 이 경로가 인증 게이트에 걸리면 활성화 흐름 자체가 성립하지 않는다.
		stubUnauthenticated()

		renderWithRouter(<App />, { route: '/accept-invitation?token=raw-token' })

		expect(await screen.findByText('계정 활성화')).toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '로그인' })).not.toBeInTheDocument()
	})

	it('토큰 없이 /accept-invitation에 오면 안내를 보여준다', async () => {
		stubUnauthenticated()

		renderWithRouter(<App />, { route: '/accept-invitation' })

		expect(await screen.findByText(/초대 토큰이 없습니다/)).toBeInTheDocument()
	})

	it('미인증 상태에서 콘솔 경로는 로그인 화면을 보여준다', async () => {
		stubUnauthenticated()

		renderWithRouter(<App />, { route: '/' })

		await waitFor(() => {
			expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument()
		})
	})
})
