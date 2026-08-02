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
		const fetchMock = vi.fn().mockResolvedValue(
			fakeResponse({ payments: [payment()], totalCount: 100, succeededCount: 100, succeededAmount: 2_000_000, page: 1, size: 20 }),
		)
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
		const fetchMock = vi.fn().mockResolvedValue(
			fakeResponse({ payments: [], totalCount: 0, succeededCount: 0, succeededAmount: 0, page: 0, size: 20 }),
		)
		vi.stubGlobal('fetch', fetchMock)

		renderWithRouter(<PaymentsPage />)
		await waitFor(() => expect(fetchMock).toHaveBeenCalled())

		await userEvent.type(screen.getByLabelText('종료일'), '2026-07-31')

		await waitFor(() => expect(lastUrl(fetchMock)).toContain(encodeURIComponent('2026-07-31T23:59:59.999Z')))
	})
})

function lastUrl(fetchMock: ReturnType<typeof vi.fn>): string {
	return String(fetchMock.mock.calls[fetchMock.mock.calls.length - 1][0])
}

describe('엑셀 다운로드', () => {
	/**
	 * **다운로드 요청에 페이징을 실으면 안 된다** — 내보내기는 현재 페이지가 아니라 조건
	 * 전체가 대상이다. 실수로 page/size가 붙으면 사용자는 20건짜리 파일을 받고도 모른다.
	 */
	it('현재 필터만 보내고 페이징은 빼고 요청한다', async () => {
		const fetchMock = vi.fn().mockImplementation((url: string) => {
			if (String(url).includes('/payments/export')) {
				return Promise.resolve({
					ok: true,
					status: 200,
					blob: async () => new Blob(['x']),
					headers: new Headers({
						'Content-Disposition': 'attachment; filename="payments-20260801-153000.xlsx"',
						'X-Export-Truncated': 'false',
					}),
				})
			}
			return Promise.resolve(fakeResponse({ payments: [], totalCount: 0, succeededCount: 0, succeededAmount: 0, page: 0, size: 20, merchants: [] }))
		})
		vi.stubGlobal('fetch', fetchMock)
		vi.stubGlobal('URL', { ...URL, createObjectURL: () => 'blob:x', revokeObjectURL: () => {} })

		renderWithRouter(<PaymentsPage />)
		await userEvent.click(await screen.findByRole('button', { name: '엑셀 다운로드' }))

		await waitFor(() => {
			const url = exportUrl(fetchMock)
			expect(url).toContain('/payments/export')
			expect(url).not.toContain('page=')
			expect(url).not.toContain('size=')
		})
	})

	// 조용히 일부만 담긴 파일을 받아가는 것이 이 기능에서 가장 위험한 실패다.
	it('서버가 잘렸다고 알리면 화면에 경고를 띄운다', async () => {
		const fetchMock = vi.fn().mockImplementation((url: string) => {
			if (String(url).includes('/payments/export')) {
				return Promise.resolve({
					ok: true,
					status: 200,
					blob: async () => new Blob(['x']),
					headers: new Headers({ 'X-Export-Truncated': 'true' }),
				})
			}
			return Promise.resolve(fakeResponse({ payments: [], totalCount: 0, succeededCount: 0, succeededAmount: 0, page: 0, size: 20, merchants: [] }))
		})
		vi.stubGlobal('fetch', fetchMock)
		vi.stubGlobal('URL', { ...URL, createObjectURL: () => 'blob:x', revokeObjectURL: () => {} })

		renderWithRouter(<PaymentsPage />)
		await userEvent.click(await screen.findByRole('button', { name: '엑셀 다운로드' }))

		expect(await screen.findByText(/최대 10,000건까지만 담았습니다/)).toBeInTheDocument()
	})

	/**
	 * 통계는 **필터 전체**에 대한 값이지 현재 페이지의 합계가 아니다 — 20건짜리 페이지의
	 * 합계는 아무 질문에도 답하지 못한다. 여기서는 서버가 준 값을 그대로 그리는지 본다.
	 */
	it('서버가 준 집계를 통계 줄에 그린다', async () => {
		vi.stubGlobal(
			'fetch',
			vi.fn().mockResolvedValue(
				fakeResponse({
					payments: [payment()],
					totalCount: 8,
					succeededCount: 6,
					succeededAmount: 160_000,
					page: 0,
					size: 20,
				}),
			),
		)

		renderWithRouter(<PaymentsPage />)

		expect(await screen.findByText('8건')).toBeInTheDocument()
		expect(screen.getByText('6건')).toBeInTheDocument()
		expect(screen.getByText('160,000원')).toBeInTheDocument()
		expect(screen.getByText('75.0%')).toBeInTheDocument()
	})

	/**
	 * **`0/0`을 0%로 그리면 "결제가 없다"와 "전부 실패했다"가 화면에서 같아진다.**
	 * 둘은 운영자가 정반대로 반응해야 하는 상황이라 구분되어야 한다.
	 */
	it('결제가 하나도 없으면 승인율을 0%가 아니라 —로 그린다', async () => {
		vi.stubGlobal(
			'fetch',
			vi.fn().mockResolvedValue(
				fakeResponse({ payments: [], totalCount: 0, succeededCount: 0, succeededAmount: 0, page: 0, size: 20 }),
			),
		)

		renderWithRouter(<PaymentsPage />)

		expect(await screen.findByText('0원')).toBeInTheDocument()
		expect(screen.queryByText('0.0%')).not.toBeInTheDocument()
	})
})

function exportUrl(fetchMock: ReturnType<typeof vi.fn>): string {
	const calls = fetchMock.mock.calls.filter((call) => String(call[0]).includes('/payments/export'))
	return String(calls[calls.length - 1][0])
}
