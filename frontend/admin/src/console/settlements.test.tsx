import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '@/test-utils'
import { SettlementTable } from '@/console/SettlementTable'
import { SettlementPage } from '@/console/SettlementPage'
import { settlementQueryString } from '@/api/client'
import type { MeResponse, SettlementReceivableSummary } from '@/api/types'

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

/** 기본은 SUPER_ADMIN이다 — 보류 해제·취소를 할 수 있는 유일한 역할이라 액션이 그려진다. */
const ME = { internalUserId: 'iu_001', loginId: 'admin', role: 'SUPER_ADMIN' } as MeResponse

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
		renderWithRouter(<SettlementTable rows={[receivable()]} canManage />)

		expect(screen.getByText('20,000원')).toBeInTheDocument()
		expect(screen.getByText(/19,700원/)).toBeInTheDocument()
		expect(screen.getByText('1.5%')).toBeInTheDocument()
	})

	// READY 전에는 환전이 일어나지 않아 값이 없다 — 0으로 보이면 안 된다.
	it('환전 손익이 없으면 0이 아니라 빈 표식을 그린다', () => {
		renderWithRouter(
			<SettlementTable rows={[receivable({ status: 'PENDING', exchangeProfitLossAmount: null })]} canManage />,
		)

		expect(screen.getByText('—')).toBeInTheDocument()
	})

	it('조건에 맞는 채권이 없으면 안내를 그린다', () => {
		renderWithRouter(<SettlementTable rows={[]} canManage />)

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

		renderWithRouter(<SettlementPage me={ME} />)

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
			page: 1,
			size: 20,
		})
		vi.stubGlobal('fetch', fetchMock)

		renderWithRouter(<SettlementPage me={ME} />)
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

		renderWithRouter(<SettlementPage me={ME} />)
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

		renderWithRouter(<SettlementPage me={ME} />)
		await userEvent.click(await screen.findByRole('button', { name: '엑셀 다운로드' }))

		expect(await screen.findByText(/최대 10,000건까지만 담았습니다/)).toBeInTheDocument()
	})
})

/** 목록 조회·가맹점 목록·파일 다운로드 셋을 구분해 응답한다 — 한 화면에서 전부 나간다. */
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
		if (String(url).includes('/settlement-receivables')) {
			return Promise.resolve(
				fakeResponse({ settlementReceivables: [], totalCount: 0, totalNetAmount: 0, page: 0, size: 20 }),
			)
		}
		return Promise.resolve(fakeResponse({ merchants: [{ merchantId: 'mrc_001', merchantName: '테스트 가맹점' }] }))
	})
}

/** 목록 조회만 골라 마지막 것을 본다 — 가맹점 목록·다운로드가 섞여 있다. */
function lastUrl(fetchMock: ReturnType<typeof vi.fn>): string {
	const calls = fetchMock.mock.calls.filter(
		(call) => String(call[0]).includes('/settlement-receivables') && !String(call[0]).includes('/export'),
	)
	return String(calls[calls.length - 1][0])
}

function exportUrl(fetchMock: ReturnType<typeof vi.fn>): string {
	const calls = fetchMock.mock.calls.filter((call) => String(call[0]).includes('/settlement-receivables/export'))
	return String(calls[calls.length - 1][0])
}

describe('정산 보류 해제와 취소', () => {
	function held(overrides: Partial<SettlementReceivableSummary> = {}) {
		return receivable({ status: 'HELD', holdReasonCode: 'TRANSACTION_REORGED', ...overrides })
	}

	/**
	 * **"보류"만 보여주고 이유를 감추면 풀어도 되는지 판단할 수 없다.** 이 화면이 액션을
	 * 갖는 이유 전체가 그 판단이라, 이유가 함께 보이는 것이 전제다.
	 */
	it('보류된 행에는 왜 막혔는지를 함께 적는다', () => {
		renderWithRouter(<SettlementTable rows={[held()]} canManage />)

		expect(screen.getByText('보류')).toBeInTheDocument()
		expect(screen.getByText('확정 이후 입금이 체인에서 사라짐')).toBeInTheDocument()
	})

	/** 서버가 사유를 늘렸을 때 화면이 빈칸이 되면 "이유 없이 막혔다"로 읽힌다. */
	it('모르는 사유 코드는 그대로 보여준다', () => {
		renderWithRouter(<SettlementTable rows={[held({ holdReasonCode: 'SOMETHING_NEW' })]} canManage />)

		expect(screen.getByText('SOMETHING_NEW')).toBeInTheDocument()
	})

	it('보류가 아닌 행에는 해제·취소 버튼을 그리지 않는다', () => {
		renderWithRouter(<SettlementTable rows={[receivable()]} canManage />)

		expect(screen.queryByRole('button', { name: '보류 해제' })).not.toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '취소' })).not.toBeInTheDocument()
	})

	/**
	 * **막는 쪽(SUPER_ADMIN 전용)과 같은 등급이어야 한다** — 푸는 쪽만 넓히면 좁게 잡은
	 * 의미가 없어진다. 서버도 403으로 막지만 누를 수 있게 두고 거부하는 것보다 낫다.
	 */
	it('SUPER_ADMIN이 아니면 액션 대신 이력만 볼 수 있다', () => {
		renderWithRouter(<SettlementTable rows={[held()]} canManage={false} />)

		expect(screen.queryByRole('button', { name: '보류 해제' })).not.toBeInTheDocument()
		// 읽는 것과 바꾸는 것은 다른 권한이다 — 이력은 VIEWER도 본다.
		expect(screen.getByRole('button', { name: '이력' })).toBeInTheDocument()
	})

	/** 자동 경로가 없는 전이라 실행한 사람 말고는 이유를 아는 곳이 없다. */
	it('사유를 적기 전에는 해제할 수 없다', async () => {
		renderWithRouter(<SettlementTable rows={[held()]} canManage />)

		await userEvent.click(screen.getByRole('button', { name: '보류 해제' }))

		expect(screen.getByRole('button', { name: '해제합니다' })).toBeDisabled()

		await userEvent.type(screen.getByLabelText('사유 (필수)'), '탐지 오류로 확인되었습니다.')

		expect(screen.getByRole('button', { name: '해제합니다' })).toBeEnabled()
	})

	it('해제는 사유와 함께 그 채권 경로로 보낸다', async () => {
		const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
			if (init?.method === 'POST') {
				return Promise.resolve(fakeResponse({ settlementReceivableId: 'str_001', status: 'READY' }))
			}
			return Promise.resolve(fakeResponse({ merchants: [] }))
		})
		vi.stubGlobal('fetch', fetchMock)
		renderWithRouter(<SettlementTable rows={[held()]} canManage />)

		await userEvent.click(screen.getByRole('button', { name: '보류 해제' }))
		await userEvent.type(screen.getByLabelText('사유 (필수)'), '탐지 오류로 확인되었습니다.')
		await userEvent.click(screen.getByRole('button', { name: '해제합니다' }))

		await waitFor(() => {
			const posted = fetchMock.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === 'POST')
			expect(posted).toBeDefined()
			const [url, init] = posted as [string, RequestInit]
			expect(String(url)).toContain('/admin/settlement-receivables/str_001/release')
			expect(String(init.body)).toContain('탐지 오류로 확인되었습니다.')
		})
	})

	/**
	 * **취소는 되돌릴 수 없다** — 해제와 같은 자리에 있으므로 확인 문구가 그 차이를 분명히
	 * 말해야 잘못 누르지 않는다.
	 */
	it('취소는 되돌릴 수 없다고 알린다', async () => {
		renderWithRouter(<SettlementTable rows={[held()]} canManage />)

		await userEvent.click(screen.getByRole('button', { name: '취소' }))

		expect(screen.getByText('되돌릴 수 없습니다')).toBeInTheDocument()
		expect(screen.getByText(/영영 정산되지 않습니다/)).toBeInTheDocument()
	})

	/** `409`는 "왜 안 되는지"를 담고 있다 — 감추면 같은 버튼을 계속 누른다. */
	it('서버가 거절하면 그 이유를 그대로 보여준다', async () => {
		const fetchMock = vi.fn().mockImplementation((url: string, init?: RequestInit) => {
			if (init?.method === 'POST') {
				return Promise.resolve({
					ok: false,
					status: 409,
					json: async () => ({ message: '보류된 정산 채권만 해제할 수 있습니다. 현재 상태: CANCELLED' }),
				})
			}
			return Promise.resolve(fakeResponse({ merchants: [] }))
		})
		vi.stubGlobal('fetch', fetchMock)
		renderWithRouter(<SettlementTable rows={[held()]} canManage />)

		await userEvent.click(screen.getByRole('button', { name: '보류 해제' }))
		await userEvent.type(screen.getByLabelText('사유 (필수)'), '풀어봅니다.')
		await userEvent.click(screen.getByRole('button', { name: '해제합니다' }))

		expect(await screen.findByText(/현재 상태: CANCELLED/)).toBeInTheDocument()
	})

	/** 비어 있다는 것도 정보다 — "손댄 적이 없다"와 "못 불러왔다"는 다른 사실이다. */
	it('이력이 없으면 없다고 말한다', async () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fakeResponse({ history: [] })))
		renderWithRouter(<SettlementTable rows={[held()]} canManage />)

		await userEvent.click(screen.getByRole('button', { name: '이력' }))

		expect(await screen.findByText('보류·해제·취소된 적이 없습니다.')).toBeInTheDocument()
	})

	it('이력에는 누가 언제 무엇을 왜 했는지가 나온다', async () => {
		vi.stubGlobal(
			'fetch',
			vi.fn().mockResolvedValue(
				fakeResponse({
					history: [
						{
							auditId: 'sha_001',
							internalUserId: 'iu_001',
							internalUserName: '홍길동',
							action: 'RELEASED',
							reasonCode: null,
							note: '탐지 오류로 확인되어 해제합니다.',
							occurredAt: '2026-08-02T00:00:00Z',
						},
					],
				}),
			),
		)
		renderWithRouter(<SettlementTable rows={[held()]} canManage />)

		await userEvent.click(screen.getByRole('button', { name: '이력' }))

		expect(await screen.findByText('해제함')).toBeInTheDocument()
		expect(screen.getByText('홍길동')).toBeInTheDocument()
		expect(screen.getByText('탐지 오류로 확인되어 해제합니다.')).toBeInTheDocument()
	})
})

describe('보류 중인 돈을 한 줄에서 보여준다', () => {
	function body(overrides: Record<string, unknown> = {}) {
		return {
			settlementReceivables: [receivable()],
			totalCount: 3,
			totalNetAmount: 59100,
			heldCount: 0,
			heldNetAmount: 0,
			page: 0,
			size: 20,
			...overrides,
		}
	}

	/**
	 * **합계에서 빼기만 하고 어디로 갔는지 말해주지 않으면 숫자가 달라진 이유를 찾을 수
	 * 없다.** 합계는 지급 경로에 살아 있는 것만 더하므로(ADR-007), 막힌 돈은 같은 줄에서
	 * 그 차이를 설명해야 한다.
	 */
	it('보류 금액과 건수를 합계 옆에 함께 보여준다', async () => {
		vi.stubGlobal('fetch', routedFetch(body({ heldCount: 2, heldNetAmount: 39400 })))

		renderWithRouter(<SettlementPage me={ME} />)

		// 라벨은 데이터를 기다리지 않고 그려지므로 **값**이 나타나는 것을 기다린다.
		expect(await screen.findByText(/39,400원/)).toBeInTheDocument()
		expect(screen.getByText('보류 중')).toBeInTheDocument()
		expect(screen.getByText(/\(2건\)/)).toBeInTheDocument()
	})

	/**
	 * **목록을 뒤져야 보이는 사실은 없는 것과 같다** — 이 슬라이스의 목적 전체가 이 한
	 * 동작이다. 눌러서 바로 보류만 남긴다.
	 */
	it('보류 금액을 누르면 보류만 남도록 필터를 좁힌다', async () => {
		const fetchMock = routedFetch(body({ heldCount: 2, heldNetAmount: 39400 }))
		vi.stubGlobal('fetch', fetchMock)

		renderWithRouter(<SettlementPage me={ME} />)

		await userEvent.click(await screen.findByRole('button', { name: /39,400원/ }))

		await waitFor(() => {
			const urls = fetchMock.mock.calls.map(([url]) => String(url))
			expect(urls.some((url) => url.includes('status=HELD'))).toBe(true)
		})
	})

	/** 막힌 돈이 없을 때까지 눈에 띄면 경고가 배경이 되어 정작 필요할 때 보이지 않는다. */
	it('보류가 없으면 누를 것도 없이 0원만 적는다', async () => {
		vi.stubGlobal('fetch', routedFetch(body()))

		renderWithRouter(<SettlementPage me={ME} />)

		expect(await screen.findByText('0원')).toBeInTheDocument()
		expect(screen.getByText('보류 중')).toBeInTheDocument()
		expect(screen.queryByRole('button', { name: /0원/ })).not.toBeInTheDocument()
	})
})
