import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '@/test-utils'
import { WebhookPage } from '@/console/WebhookPage'

const SECRET = 'whsec_AbCdEfGhIjKlMnOpQrStUvWxYz'

function settings(overrides: Record<string, unknown> = {}) {
	return {
		webhookUrl: 'https://merchant.example.com/webhooks',
		signingSecret: SECRET,
		secretVersion: 1,
		...overrides,
	}
}

function fakeResponse(body: unknown) {
	return { ok: true, status: 200, json: async () => body }
}

afterEach(() => {
	vi.unstubAllGlobals()
	vi.restoreAllMocks()
})

describe('WebhookPage', () => {
	it('가려진 서명 비밀을 "보기"로 드러낸다', async () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fakeResponse(settings())))

		renderWithRouter(<WebhookPage />)

		const secret = await screen.findByTestId('signing-secret')
		// 기본 상태에서는 값이 화면에 없다 — 어깨너머로 새는 것까지 막지는 못하므로 가려 둔다.
		expect(secret).not.toHaveTextContent(SECRET)

		await userEvent.click(screen.getByRole('button', { name: '보기' }))

		expect(await screen.findByTestId('signing-secret')).toHaveTextContent(SECRET)
	})

	it('현재 세대를 함께 보여준다', async () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fakeResponse(settings({ secretVersion: 3 }))))

		renderWithRouter(<WebhookPage />)

		expect(await screen.findByText('현재 세대: 3')).toBeInTheDocument()
	})

	it('수신 URL을 저장하면 PUT으로 보낸다', async () => {
		const fetchMock = vi.fn().mockResolvedValue(fakeResponse(settings()))
		vi.stubGlobal('fetch', fetchMock)

		renderWithRouter(<WebhookPage />)

		const input = await screen.findByLabelText('Webhook 수신 URL')
		await userEvent.clear(input)
		await userEvent.type(input, 'https://example.com/hooks')
		await userEvent.click(screen.getByRole('button', { name: '저장' }))

		await vi.waitFor(() => {
			const put = fetchMock.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === 'PUT')
			expect(put).toBeDefined()
			expect(JSON.parse(String((put![1] as RequestInit).body))).toEqual({ webhookUrl: 'https://example.com/hooks' })
		})
	})

	/**
	 * 입력란을 비우는 것이 곧 해제다 — 빈 문자열을 그대로 보내면 서버의 URL 형식
	 * 검증에 걸려 400이 나고, 그러면 해제할 방법이 사라진다.
	 */
	it('빈 입력은 해제(null)로 보낸다', async () => {
		const fetchMock = vi.fn().mockResolvedValue(fakeResponse(settings({ webhookUrl: null })))
		vi.stubGlobal('fetch', fetchMock)

		renderWithRouter(<WebhookPage />)

		await userEvent.clear(await screen.findByLabelText('Webhook 수신 URL'))
		await userEvent.click(screen.getByRole('button', { name: '저장' }))

		await vi.waitFor(() => {
			const put = fetchMock.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === 'PUT')
			expect(put).toBeDefined()
			expect(JSON.parse(String((put![1] as RequestInit).body))).toEqual({ webhookUrl: null })
		})
	})

	/**
	 * **교체는 되돌릴 수 없다.** 한 번의 클릭으로 그동안 오던 Webhook이 전부 거부되기
	 * 시작하므로, 확인 절차 없이 실행되면 안 된다.
	 */
	it('확인 절차 없이는 비밀을 교체하지 않는다', async () => {
		const fetchMock = vi.fn().mockResolvedValue(fakeResponse(settings()))
		vi.stubGlobal('fetch', fetchMock)

		renderWithRouter(<WebhookPage />)

		await userEvent.click(await screen.findByRole('button', { name: '비밀 교체' }))

		expect(screen.getByText('비밀을 교체하면 되돌릴 수 없습니다')).toBeInTheDocument()
		expect(fetchMock.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === 'POST')).toBe(false)
	})

	it('확인하면 교체하고 새 세대를 보여준다', async () => {
		const fetchMock = vi.fn().mockImplementation((_url: string, init?: RequestInit) => {
			if (init?.method === 'POST') {
				return Promise.resolve(fakeResponse(settings({ signingSecret: 'whsec_NEW', secretVersion: 2 })))
			}
			return Promise.resolve(fakeResponse(settings()))
		})
		vi.stubGlobal('fetch', fetchMock)

		renderWithRouter(<WebhookPage />)

		await userEvent.click(await screen.findByRole('button', { name: '비밀 교체' }))
		await userEvent.click(screen.getByRole('button', { name: '교체합니다' }))

		expect(await screen.findByText('현재 세대: 2')).toBeInTheDocument()
		// 교체 직후에는 드러내 준다 — 곧바로 서버에 옮겨 적어야 하는 값이다.
		expect(await screen.findByTestId('signing-secret')).toHaveTextContent('whsec_NEW')
	})
})
