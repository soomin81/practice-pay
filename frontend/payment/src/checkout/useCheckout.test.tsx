import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { CheckoutApiError, checkoutApi } from '../api/client'
import type { CheckoutStatus } from '../api/types'
import { Providers, createTestQueryClient } from '../test-utils'
import {
	POLL_INTERVAL_MS,
	isPollTimedOut,
	isSettled,
	useCheckoutSession,
	useCheckoutStatus,
} from './useCheckout'

/**
 * 폴링 훅 테스트.
 *
 * 여기서 잡으려는 실패는 조용한 것들이다:
 *  - **끝난 결제를 계속 두드리는 것** — 화면은 멀쩡해 보이는데 서버만 3초마다 맞는다.
 *  - **404/410에 재시도하는 것** — 결과가 같은데 화면 전환만 늦어진다.
 *  - **상한이 영영 오지 않는 것** — `startedAt`을 훅 안에서 잡으면 리렌더마다 기준이
 *    밀려서 5분이 지나도 상한에 걸리지 않는다(그래서 인자로 받는다).
 */

vi.mock('../api/client', async (importOriginal) => {
	const actual = await importOriginal<typeof import('../api/client')>()
	return {
		...actual,
		checkoutApi: {
			getSession: vi.fn(),
			getStatus: vi.fn(),
			connectWallet: vi.fn(),
			submitTransaction: vi.fn(),
			cancel: vi.fn(),
		},
	}
})

const api = vi.mocked(checkoutApi)

function wrapper({ children }: { children: ReactNode }) {
	return <Providers client={createTestQueryClient()}>{children}</Providers>
}

function statusFixture(overrides: Partial<CheckoutStatus> = {}): CheckoutStatus {
	return {
		checkoutSessionStatus: 'PAYMENT_SUBMITTED',
		paymentStatus: 'PROCESSING',
		confirmationCount: 1,
		requiredConfirmationCount: 12,
		transactionHash: '0xhash',
		failureReason: null,
		redirectUrl: null,
		...overrides,
	}
}

beforeEach(() => {
	vi.clearAllMocks()
})

afterEach(() => {
	vi.useRealTimers()
})

describe('isSettled — 더 기다려도 바뀌지 않는 상태인가', () => {
	test.each(['SUCCEEDED', 'FAILED', 'EXPIRED'])('%s는 끝난 상태다', (paymentStatus) => {
		expect(isSettled(statusFixture({ paymentStatus }))).toBe(true)
	})

	test.each(['CREATED', 'READY', 'PROCESSING', 'CONFIRMING'])('%s는 아직 진행 중이다', (paymentStatus) => {
		expect(isSettled(statusFixture({ paymentStatus }))).toBe(false)
	})
})

describe('isPollTimedOut — 5분 상한', () => {
	test('방금 시작했으면 상한이 아니다', () => {
		expect(isPollTimedOut(Date.now())).toBe(false)
	})

	test('5분이 지나면 상한이다', () => {
		expect(isPollTimedOut(Date.now() - 5 * 60 * 1000 - 1)).toBe(true)
	})

	test('4분 59초는 아직 상한이 아니다', () => {
		expect(isPollTimedOut(Date.now() - (5 * 60 * 1000 - 1000))).toBe(false)
	})
})

describe('useCheckoutSession', () => {
	test('세션을 한 번 읽어 온다', async () => {
		api.getSession.mockResolvedValue({ checkoutSessionId: 'cs_1' } as never)

		const { result } = renderHook(() => useCheckoutSession('cs_1'), { wrapper })

		await waitFor(() => expect(result.current.isSuccess).toBe(true))
		expect(api.getSession).toHaveBeenCalledExactlyOnceWith('cs_1')
	})

	test('sessionId가 없으면 요청하지 않는다', () => {
		renderHook(() => useCheckoutSession(null), { wrapper })

		expect(api.getSession).not.toHaveBeenCalled()
	})

	test.each([404, 410, 409])('%i는 재시도하지 않는다 — 다시 물어도 답이 같다', async (status) => {
		api.getSession.mockRejectedValue(new CheckoutApiError(status, '실패'))

		const { result } = renderHook(() => useCheckoutSession('cs_1'), { wrapper })

		await waitFor(() => expect(result.current.isError).toBe(true))
		expect(api.getSession).toHaveBeenCalledTimes(1)
	})

	test('5xx는 재시도한다 — 일시적일 수 있다', async () => {
		api.getSession.mockRejectedValue(new CheckoutApiError(500, '서버 오류'))

		const { result } = renderHook(() => useCheckoutSession('cs_1'), { wrapper })

		await waitFor(() => expect(result.current.isError).toBe(true))
		expect(api.getSession.mock.calls.length).toBeGreaterThan(1)
	})
})

describe('useCheckoutStatus — 폴링', () => {
	test('enabled가 false면 아예 호출하지 않는다', () => {
		renderHook(() => useCheckoutStatus('cs_1', { enabled: false, startedAt: Date.now() }), { wrapper })

		expect(api.getStatus).not.toHaveBeenCalled()
	})

	test('진행 중이면 주기적으로 다시 읽는다', async () => {
		vi.useFakeTimers({ shouldAdvanceTime: true })
		api.getStatus.mockResolvedValue(statusFixture({ paymentStatus: 'CONFIRMING' }))

		renderHook(() => useCheckoutStatus('cs_1', { enabled: true, startedAt: Date.now() }), { wrapper })

		await vi.waitFor(() => expect(api.getStatus).toHaveBeenCalledTimes(1))
		await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 2)
		expect(api.getStatus.mock.calls.length).toBeGreaterThan(1)
	})

	test('결제가 끝나면 폴링을 멈춘다', async () => {
		vi.useFakeTimers({ shouldAdvanceTime: true })
		api.getStatus.mockResolvedValue(statusFixture({ paymentStatus: 'SUCCEEDED', redirectUrl: 'https://m/done' }))

		renderHook(() => useCheckoutStatus('cs_1', { enabled: true, startedAt: Date.now() }), { wrapper })

		await vi.waitFor(() => expect(api.getStatus).toHaveBeenCalledTimes(1))
		await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 5)

		// 끝난 결제를 계속 두드리지 않는다.
		expect(api.getStatus).toHaveBeenCalledTimes(1)
	})

	test('상한(5분)을 넘기면 진행 중이어도 폴링을 멈춘다', async () => {
		vi.useFakeTimers({ shouldAdvanceTime: true })
		api.getStatus.mockResolvedValue(statusFixture({ paymentStatus: 'CONFIRMING' }))

		// 이미 5분 전에 시작한 폴링이다.
		const startedAt = Date.now() - 5 * 60 * 1000 - 1
		renderHook(() => useCheckoutStatus('cs_1', { enabled: true, startedAt }), { wrapper })

		await vi.waitFor(() => expect(api.getStatus).toHaveBeenCalledTimes(1))
		await vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 5)

		expect(api.getStatus).toHaveBeenCalledTimes(1)
	})
})
