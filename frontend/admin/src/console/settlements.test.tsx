import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '@/test-utils'
import { SettlementTable } from '@/console/SettlementTable'
import { SettlementPage } from '@/console/SettlementPage'
import { settlementQueryString } from '@/api/client'
import type { SettlementReceivableSummary } from '@/api/types'

function receivable(overrides: Partial<SettlementReceivableSummary> = {}): SettlementReceivableSummary {
	return {
		settlementReceivableId: 'str_001',
		merchantId: 'mrc_001',
		merchantName: '테스트 가맹점',
		paymentId: 'pay_001',
		merchantOrderId: 'order-001',
		status: 'READY',
		settlementCurrency: 'KRW',
		grossAmount: 20000,
		feeRate: 0.015,
		feeAmount: 300,
		adjustmentAmount: 0,
		netAmount: 19700,
		exchangeReceivedAmount: 20101,
		exchangeProfitLossAmount: 101,
		eligibleDate: '2026-08-01',
		createdAt: '2026-08-01T04:07:24Z',
		...overrides,
	} as SettlementReceivableSummary
}

function fakeResponse(body: unknown) {
	return { ok: true, status: 200, json: async () => body }
}

function routedFetch(settlementBody: unknown) {
	return vi.fn().mockImplementation((url: string) => {
		if (String(url).includes('/settlement-receivables')) return Promise.resolve(fakeResponse(settlementBody))
		return Promise.resolve(fakeResponse({ merchants: [{ merchantId: 'mrc_001', merchantName: '테스트 가맹점' }] }))
	})
}

afterEach(() => {
	vi.unstubAllGlobals()
	vi.restoreAllMocks()
})

describe('정산 표', () => {
	it('정산 기준·수수료·정산 예정 금액을 보여준다', () => {
		renderWithRouter(<SettlementTable rows={[receivable()]} />)

		expect(screen.getByText('20,000원')).toBeInTheDocument()
		expect(screen.getByText(/19,700원/)).toBeInTheDocument()
		expect(screen.getByText('1.5%')).toBeInTheDocument()
	})

	// READY 전에는 환전이 일어나지 않아 값이 없다 — 0으로 보이면 안 된다.
	it('환전 손익이 없으면 0이 아니라 빈 표식을 그린다', () => {
		renderWithRouter(
			<SettlementTable rows={[receivable({ status: 'PENDING', exchangeProfitLossAmount: null })]} />,
		)

		expect(screen.getByText('—')).toBeInTheDocument()
	})

	it('조건에 맞는 채권이 없으면 안내를 그린다', () => {
		renderWithRouter(<SettlementTable rows={[]} />)

		expect(screen.getByText(/정산 채권이 없습니다/)).toBeInTheDocument()
	})
})

describe('필터 쿼리스트링', () => {
	it('빈 값은 넣지 않는다', () => {
		expect(settlementQueryString({ status: '', page: 0, size: 20 })).toBe('?page=0&size=20')
	})

	it('정산 예정일은 날짜 그대로 보낸다', () => {
		expect(settlementQueryString({ eligibleFrom: '2026-08-01' })).toBe('?eligibleFrom=2026-08-01')
	})
})

describe('정산 페이지', () => {
	/**
	 * **이 화면의 핵심 숫자다** — "그래서 얼마를 받나"에 답하는 값이고, 현재 페이지의 합이
	 * 아니라 필터 전체의 합계여야 한다.
	 */
	it('필터 전체의 정산 예정 금액 합계를 크게 보여준다', async () => {
		vi.stubGlobal(
			'fetch',
			routedFetch({ settlementReceivables: [receivable()], totalCount: 3, totalNetAmount: 59100, page: 0, size: 20 }),
		)

		renderWithRouter(<SettlementPage />)

		expect(await screen.findByText('59,100원')).toBeInTheDocument()
		expect(screen.getByText(/3건 기준/)).toBeInTheDocument()
	})

	it('필터를 바꾸면 첫 페이지로 돌아간다', async () => {
		const fetchMock = routedFetch({
			settlementReceivables: [receivable()],
			totalCount: 100,
			totalNetAmount: 100,
			page: 1,
			size: 20,
		})
		vi.stubGlobal('fetch', fetchMock)

		renderWithRouter(<SettlementPage />)
		await userEvent.click(await screen.findByRole('button', { name: '다음' }))
		await waitFor(() => expect(lastUrl(fetchMock)).toContain('page=1'))

		await userEvent.selectOptions(screen.getByLabelText('상태'), 'PENDING')

		await waitFor(() => {
			expect(lastUrl(fetchMock)).toContain('status=PENDING')
			expect(lastUrl(fetchMock)).toContain('page=0')
		})
	})
})

function lastUrl(fetchMock: ReturnType<typeof vi.fn>): string {
	const calls = fetchMock.mock.calls.filter((call) => String(call[0]).includes('/settlement-receivables'))
	return String(calls[calls.length - 1][0])
}
