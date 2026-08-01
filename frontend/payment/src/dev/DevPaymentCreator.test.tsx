import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import { DevPaymentCreator } from './DevPaymentCreator'

/**
 * DEV 결제 생성 버튼의 **안전장치** 테스트.
 *
 * 지키려는 것 둘이다.
 *  1. API Key 설정이 없으면 기능이 꺼진다 — 코드에 기본값을 두지 않는다.
 *  2. **요청에 수취 지갑을 넣지 않는다.** 그 값은 PG가 수탁하는 지갑이라 백엔드
 *     설정에서만 온다(`docs/architecture/mvp-scope.md`의 "수취 지갑 귀속"). 여기서
 *     보내면 가맹점 역할이 자기 주소로 USDC를 받으면서 정산 채권까지 받게 된다.
 *     한때 이 자리에 USDC 토큰 Contract 주소가 기본값으로 하드코딩돼 있기도 했다.
 */

const API_KEY = 'sk_test_devkey01_dev-secret-value'

afterEach(() => {
	vi.unstubAllEnvs()
	vi.unstubAllGlobals()
})

test('API Key가 없으면 버튼을 띄우지 않고 무엇을 채워야 하는지 알려준다', () => {
	vi.stubEnv('VITE_DEV_API_KEY', '')

	render(<DevPaymentCreator onCreated={() => {}} />)

	expect(screen.queryByRole('button')).not.toBeInTheDocument()
	expect(screen.getByText(/VITE_DEV_API_KEY/)).toBeInTheDocument()
})

test('API Key가 있으면 버튼이 뜬다', () => {
	vi.stubEnv('VITE_DEV_API_KEY', API_KEY)

	render(<DevPaymentCreator onCreated={() => {}} />)

	expect(screen.getByRole('button', { name: '테스트 결제 생성' })).toBeInTheDocument()
})

test('결제 생성 요청에 수취 지갑을 담지 않는다', async () => {
	vi.stubEnv('VITE_DEV_API_KEY', API_KEY)

	const fetchMock = vi.fn().mockResolvedValue({
		ok: true,
		json: async () => ({ checkoutSessionId: 'cs_001' }),
	})
	vi.stubGlobal('fetch', fetchMock)

	const onCreated = vi.fn()
	render(<DevPaymentCreator onCreated={onCreated} />)
	await userEvent.click(screen.getByRole('button', { name: '테스트 결제 생성' }))

	await waitFor(() => expect(onCreated).toHaveBeenCalledWith('cs_001'))

	const body = JSON.parse(fetchMock.mock.calls[0][1].body as string) as Record<string, unknown>
	expect(body).not.toHaveProperty('receivingWallet')
	expect(body.network).toBe('BASE_SEPOLIA')
})
