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
			routedFetch({ settlementReceivables: [receivable()], totalCount: 3, totalNetAmount: 59100, heldCount: 0, heldNetAmount: 0, page: 0, size: 20 }),
		)

		renderWithRouter(<SettlementPage />)

		expect(await screen.findByText('59,100원')).toBeInTheDocument()
		// 합계가 **어느 범위의 것인지**도 함께 보인다 — 예전에는 합계 아래에 "3건 기준"으로
		// 적었고, 지금은 통계 줄의 옆 칸(건수)이 같은 답을 한다. 확인하려는 것은 표현이
		// 아니라 "합계만 덩그러니 있지 않다"는 사실이다.
		expect(screen.getByText('3건')).toBeInTheDocument()
	})

	it('필터를 바꾸면 첫 페이지로 돌아간다', async () => {
		const fetchMock = routedFetch({
			settlementReceivables: [receivable()],
			totalCount: 100,
			totalNetAmount: 100,
			heldCount: 0,
			heldNetAmount: 0,
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

	/**
	 * **다운로드 요청에 페이징을 실으면 안 된다** — 내보내기는 현재 페이지가 아니라 조건
	 * 전체가 대상이다. 실수로 page/size가 붙으면 사용자는 20건짜리 파일을 받고도 모른다.
	 */
	it('내보내기는 현재 필터만 보내고 페이징은 뺀다', async () => {
		const fetchMock = exportAwareFetch({ truncated: false })
		vi.stubGlobal('fetch', fetchMock)
		vi.stubGlobal('URL', { ...URL, createObjectURL: () => 'blob:x', revokeObjectURL: () => {} })

		renderWithRouter(<SettlementPage />)
		await userEvent.click(await screen.findByRole('button', { name: '엑셀 다운로드' }))

		await waitFor(() => {
			const url = exportUrl(fetchMock)
			expect(url).toContain('/settlement-receivables/export')
			expect(url).not.toContain('page=')
			expect(url).not.toContain('size=')
		})
	})

	// 조용히 일부만 담긴 파일을 받아가는 것이 이 기능에서 가장 위험한 실패다.
	it('서버가 잘렸다고 알리면 화면에 경고를 띄운다', async () => {
		vi.stubGlobal('fetch', exportAwareFetch({ truncated: true }))
		vi.stubGlobal('URL', { ...URL, createObjectURL: () => 'blob:x', revokeObjectURL: () => {} })

		renderWithRouter(<SettlementPage />)
		await userEvent.click(await screen.findByRole('button', { name: '엑셀 다운로드' }))

		expect(await screen.findByText(/최대 10,000건까지만 담았습니다/)).toBeInTheDocument()
	})

	/**
	 * **가맹점도 자기 돈이 얼마나 막혔는지는 알아야 한다** — 다만 이 콘솔에는 푸는 수단이
	 * 없다(보류·해제·취소는 PG 내부 운영자만 한다). 합계가 지급 경로에 살아 있는 것만
	 * 더하므로, 이 값이 없으면 "왜 줄었나"를 가맹점이 알 길이 없어 결국 문의로 돌아온다.
	 */
	it('보류된 금액과 건수를 합계 옆에 보여준다', async () => {
		vi.stubGlobal(
			'fetch',
			routedFetch({
				settlementReceivables: [receivable()],
				totalCount: 3,
				totalNetAmount: 59100,
				heldCount: 2,
				heldNetAmount: 39400,
				page: 0,
				size: 20,
			}),
		)

		renderWithRouter(<SettlementPage />)

		// 라벨은 데이터를 기다리지 않고 그려지므로 값이 나타나는 것을 기다린다.
		expect(await screen.findByText('39,400원 (2건)')).toBeInTheDocument()
		expect(screen.getByText('보류 중')).toBeInTheDocument()
	})
})

/** 목록 조회와 파일 다운로드를 구분해 응답한다 — 한 화면에서 둘 다 나간다. */
function exportAwareFetch({ truncated }: { truncated: boolean }) {
	return vi.fn().mockImplementation((url: string) => {
		if (String(url).includes('/settlement-receivables/export')) {
			return Promise.resolve({
				ok: true,
				status: 200,
				blob: async () => new Blob(['x']),
				headers: new Headers({ 'X-Export-Truncated': String(truncated) }),
			})
		}
		return Promise.resolve(
			fakeResponse({ settlementReceivables: [], totalCount: 0, totalNetAmount: 0, heldCount: 0, heldNetAmount: 0, page: 0, size: 20, merchants: [] }),
		)
	})
}

function exportUrl(fetchMock: ReturnType<typeof vi.fn>): string {
	const calls = fetchMock.mock.calls.filter((call) => String(call[0]).includes('/settlement-receivables/export'))
	return String(calls[calls.length - 1][0])
}

function lastUrl(fetchMock: ReturnType<typeof vi.fn>): string {
	const calls = fetchMock.mock.calls.filter((call) => String(call[0]).includes('/settlement-receivables'))
	return String(calls[calls.length - 1][0])
}
