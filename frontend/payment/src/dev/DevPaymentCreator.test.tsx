import { render, screen } from '@testing-library/react'
import { afterEach, expect, test, vi } from 'vitest'
import { DevPaymentCreator } from './DevPaymentCreator'

/**
 * DEV 결제 생성 버튼의 **안전장치** 테스트.
 *
 * 이 버튼은 결제를 만들면서 "USDC를 받을 주소"를 정한다. 그 값에 기본값을 두면 그
 * 주소로 실제 테스트넷 USDC가 전송되므로, **설정이 없을 때 기능이 꺼지는 것**이
 * 동작 자체보다 중요하다. 실제로 한동안 USDC 토큰 Contract 주소가 기본값으로
 * 하드코딩돼 있었다 — 그리로 보낸 토큰은 되찾을 수 없다.
 */

const WALLET = '0xAbC1000000000000000000000000000000000001'
const API_KEY = 'sk_test_devkey01_dev-secret-value'

afterEach(() => {
	vi.unstubAllEnvs()
})

test('수취 지갑이 없으면 버튼을 띄우지 않고 무엇을 채워야 하는지 알려준다', () => {
	vi.stubEnv('VITE_DEV_API_KEY', API_KEY)
	vi.stubEnv('VITE_DEV_RECEIVING_WALLET', '')

	render(<DevPaymentCreator onCreated={() => {}} />)

	expect(screen.queryByRole('button')).not.toBeInTheDocument()
	expect(screen.getByText(/VITE_DEV_RECEIVING_WALLET/)).toBeInTheDocument()
})

test('API Key가 없으면 버튼을 띄우지 않는다', () => {
	vi.stubEnv('VITE_DEV_API_KEY', '')
	vi.stubEnv('VITE_DEV_RECEIVING_WALLET', WALLET)

	render(<DevPaymentCreator onCreated={() => {}} />)

	expect(screen.queryByRole('button')).not.toBeInTheDocument()
	expect(screen.getByText(/VITE_DEV_API_KEY/)).toBeInTheDocument()
})

test('둘 다 있으면 버튼이 뜬다', () => {
	vi.stubEnv('VITE_DEV_API_KEY', API_KEY)
	vi.stubEnv('VITE_DEV_RECEIVING_WALLET', WALLET)

	render(<DevPaymentCreator onCreated={() => {}} />)

	expect(screen.getByRole('button', { name: '테스트 결제 생성' })).toBeInTheDocument()
})
