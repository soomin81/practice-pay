import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '@/test-utils'
import { PaymentTable } from '@/console/PaymentTable'
import { PaymentsPage } from '@/console/PaymentsPage'
import { paymentQueryString } from '@/api/client'
import { formatTokenAmount } from '@/console/format'
import type { PaymentSummary } from '@/api/types'

function payment(overrides: Partial<PaymentSummary> = {}): PaymentSummary {
	return {
		paymentId: 'pay_001',
		merchantId: 'mrc_001',
		merchantName: '테스트 가맹점',
		merchantOrderId: 'order-001',
		orderName: '테스트 주문',
		orderAmount: 50000,
		paymentAsset: 'USDC',
		paymentAmount: '72992701',
		tokenDecimals: 6,
		network: 'BASE_SEPOLIA',
		status: 'SUCCEEDED',
		failureReason: null,
		transactionHash: null,
		paidAt: '2026-07-20T10:05:00Z',
		createdAt: '2026-07-20T10:00:00Z',
		...overrides,
	} as PaymentSummary
}

function fakeResponse(body: unknown) {
	return { ok: true, status: 200, json: async () => body }
}

/**
 * **URL별로 다른 응답을 준다** — 이 페이지는 결제 목록 말고 가맹점 목록(필터 선택지)도
 * 불러온다. 하나의 응답을 모든 요청에 돌려주면 가맹점 필터가 빈 데이터를 받아 터진다.
 */
function routedFetch(paymentsBody: unknown) {
	return vi.fn().mockImplementation((url: string) => {
		if (String(url).includes('/admin/payments')) return Promise.resolve(fakeResponse(paymentsBody))
		return Promise.resolve(fakeResponse({ merchants: [{ merchantId: 'mrc_001', merchantName: '테스트 가맹점' }] }))
	})
}

afterEach(() => {
	vi.unstubAllGlobals()
	vi.restoreAllMocks()
})

describe('토큰 금액 표시', () => {
	/**
	 * **이 앱에서 가장 조용히 깨지는 종류다** — Minor Unit을 `Number`로 옮기면 안전 정수
	 * 범위를 넘는 순간 값이 달라지는데 화면은 멀쩡해 보인다. 그래서 백엔드가 문자열로 준다.
	 */
	it('안전 정수 범위를 넘는 금액도 자리수를 잃지 않는다', () => {
		expect(formatTokenAmount('9007199254740993', 6)).toBe('9007199254.740993')
		expect(formatTokenAmount('72992701', 6)).toBe('72.992701')
		// 18-decimals 토큰(대부분의 ERC-20)에서도 정수부가 0으로 채워져야 한다.
		expect(formatTokenAmount('1', 18)).toBe('0.000000000000000001')
	})

	it('표가 서버 값을 그대로 보여준다', () => {
		renderWithRouter(<PaymentTable payments={[payment({ paymentAmount: '9007199254740993' })]} />)

		expect(screen.getByText(/9007199254\.740993/)).toBeInTheDocument()
		expect(screen.getByText('50,000원')).toBeInTheDocument()
		expect(screen.getByText('테스트 가맹점')).toBeInTheDocument()
	})

	it('거래 Hash가 없으면 빈 값 대신 표식을 그린다', () => {
		renderWithRouter(<PaymentTable payments={[payment({ transactionHash: null })]} />)

		expect(screen.getByText('—')).toBeInTheDocument()
	})
})

describe('필터 쿼리스트링', () => {
	it('빈 값은 파라미터에 넣지 않는다', () => {
		expect(paymentQueryString({ status: '', from: undefined, page: 0, size: 20 })).toBe('?page=0&size=20')
	})

	it('지정한 값만 넣는다', () => {
		expect(paymentQueryString({ status: 'SUCCEEDED' })).toBe('?status=SUCCEEDED')
	})
})

describe('결제 내역 페이지', () => {
	it('필터를 바꾸면 첫 페이지로 돌아간다', async () => {
		const fetchMock = routedFetch({ payments: [payment()], totalCount: 100, page: 1, size: 20 })
		vi.stubGlobal('fetch', fetchMock)

		renderWithRouter(<PaymentsPage />)
		// 페이징 UI는 응답이 온 뒤에야 그려진다 — fetch 호출만 기다리면 아직 로딩 화면이다.
		await userEvent.click(await screen.findByRole('button', { name: '다음' }))
		await waitFor(() => expect(lastUrl(fetchMock)).toContain('page=1'))

		await userEvent.selectOptions(screen.getByLabelText('상태'), 'FAILED')

		// 3페이지를 보다가 조건을 좁히면 결과가 없는데 "결제가 없다"로 보이는 혼란을 막는다.
		await waitFor(() => {
			expect(lastUrl(fetchMock)).toContain('status=FAILED')
			expect(lastUrl(fetchMock)).toContain('page=0')
		})
	})

	/**
	 * 종료일을 그날 00:00으로 보내면 **마지막 날 결제가 통째로 빠진다** — 기간 필터에서
	 * 가장 흔한 실수라 회귀로 고정한다.
	 */
	it('종료일은 그날 끝까지 포함한다', async () => {
		const fetchMock = routedFetch({ payments: [], totalCount: 0, page: 0, size: 20 })
		vi.stubGlobal('fetch', fetchMock)

		renderWithRouter(<PaymentsPage />)
		await waitFor(() => expect(fetchMock).toHaveBeenCalled())

		await userEvent.type(screen.getByLabelText('종료일'), '2026-07-31')

		await waitFor(() => expect(lastUrl(fetchMock)).toContain(encodeURIComponent('2026-07-31T23:59:59.999Z')))
	})
})

/** 가맹점 목록 조회가 섞여 있으므로 **결제 조회만** 골라 마지막 것을 본다. */
function lastUrl(fetchMock: ReturnType<typeof vi.fn>): string {
	const paymentCalls = fetchMock.mock.calls.filter((call) => String(call[0]).includes('/admin/payments'))
	return String(paymentCalls[paymentCalls.length - 1][0])
}
