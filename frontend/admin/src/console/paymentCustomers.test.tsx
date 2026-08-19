import { describe, expect, it, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '@/test-utils'
import { adminApi } from '@/api/client'
import { ConsoleShell } from '@/console/ConsoleShell'
import { PaymentCustomerSearchPage } from '@/console/PaymentCustomerSearchPage'
import { PaymentCustomerTable } from '@/console/PaymentCustomerTable'
import type { MeResponse, PaymentCustomerMatch } from '@/api/types'

function match(overrides: Partial<PaymentCustomerMatch> = {}): PaymentCustomerMatch {
	return {
		paymentId: 'pay_001',
		merchantId: 'mrc_001',
		merchantName: '테스트 가맹점',
		merchantOrderId: 'order-1001',
		orderName: '테스트 상품',
		orderAmount: 20000,
		status: 'SUCCEEDED',
		nameMasked: '홍*동',
		emailMasked: 'gi***@example.com',
		phoneMasked: '010-****-5678',
		paidAt: '2026-08-20T00:00:00Z',
		createdAt: '2026-08-20T00:00:00Z',
		...overrides,
	} as PaymentCustomerMatch
}

function me(role: string): MeResponse {
	return { internalUserId: 'iu_me', loginId: 'me01', role } as MeResponse
}

describe('PaymentCustomerTable', () => {
	it('가려진 값만 보여준다', () => {
		renderWithRouter(<PaymentCustomerTable matches={[match()]} canReveal={false} />)

		expect(screen.getByText('홍*동')).toBeInTheDocument()
		expect(screen.getByText('gi***@example.com')).toBeInTheDocument()
		expect(screen.getByText('010-****-5678')).toBeInTheDocument()
	})

	/**
	 * **가장 중요한 회귀다.** 서버도 403으로 막지만, SUPER_ADMIN이 아닌 사람에게 버튼이 보이면
	 * "눌러도 되는 일"로 읽힌다(ADR-008의 6).
	 */
	it('원본 열람 버튼은 SUPER_ADMIN에게만 보인다', () => {
		renderWithRouter(<PaymentCustomerTable matches={[match()]} canReveal={false} />)
		expect(screen.queryByRole('button', { name: '원본 보기' })).not.toBeInTheDocument()

		renderWithRouter(<PaymentCustomerTable matches={[match()]} canReveal={true} />)
		expect(screen.getByRole('button', { name: '원본 보기' })).toBeInTheDocument()
	})
})

describe('RevealCustomerAction', () => {
	/** 사유를 받기 전에는 서버를 부르지 않는다 — 서버도 빈 사유를 400으로 막는다. */
	it('버튼을 눌러도 곧바로 열람하지 않고 사유를 먼저 묻는다', async () => {
		const reveal = vi.spyOn(adminApi, 'revealPaymentCustomer')
		renderWithRouter(<PaymentCustomerTable matches={[match()]} canReveal={true} />)

		await userEvent.setup().click(screen.getByRole('button', { name: '원본 보기' }))

		expect(screen.getByLabelText('열람 사유(기록에 남습니다)')).toBeInTheDocument()
		expect(reveal).not.toHaveBeenCalled()
		reveal.mockRestore()
	})

	it('사유와 함께 그 결제 경로로 열람을 요청하고 원문을 보여준다', async () => {
		const reveal = vi.spyOn(adminApi, 'revealPaymentCustomer').mockResolvedValue({
			paymentId: 'pay_001',
			name: '홍길동',
			email: 'gildong@example.com',
			phone: '010-1234-5678',
			revealedAt: '2026-08-20T00:00:00Z',
		} as Awaited<ReturnType<typeof adminApi.revealPaymentCustomer>>)
		renderWithRouter(<PaymentCustomerTable matches={[match()]} canReveal={true} />)

		const user = userEvent.setup()
		await user.click(screen.getByRole('button', { name: '원본 보기' }))
		await user.type(screen.getByLabelText('열람 사유(기록에 남습니다)'), '결제 실패 문의 대응')
		await user.click(screen.getByRole('button', { name: '원본 보기' }))

		expect(reveal).toHaveBeenCalledWith('pay_001', '결제 실패 문의 대응')
		expect(await screen.findByText('gildong@example.com')).toBeInTheDocument()
		// 기록이 남는다는 사실을 화면에서도 알려야 한다.
		expect(screen.getByText('열람 기록이 남았습니다')).toBeInTheDocument()
		reveal.mockRestore()
	})

	/** 원문을 화면에 계속 두지 않는다 — 다시 보려면 다시 열람하고, 기록도 다시 남는다. */
	it('가리기를 누르면 원문이 화면에서 사라진다', async () => {
		const reveal = vi.spyOn(adminApi, 'revealPaymentCustomer').mockResolvedValue({
			paymentId: 'pay_001',
			name: '홍길동',
			email: 'gildong@example.com',
			phone: '010-1234-5678',
			revealedAt: '2026-08-20T00:00:00Z',
		} as Awaited<ReturnType<typeof adminApi.revealPaymentCustomer>>)
		renderWithRouter(<PaymentCustomerTable matches={[match()]} canReveal={true} />)

		const user = userEvent.setup()
		await user.click(screen.getByRole('button', { name: '원본 보기' }))
		await user.type(screen.getByLabelText('열람 사유(기록에 남습니다)'), '문의 대응')
		await user.click(screen.getByRole('button', { name: '원본 보기' }))
		await screen.findByText('gildong@example.com')

		await user.click(screen.getByRole('button', { name: '가리기' }))

		await waitFor(() => expect(screen.queryByText('gildong@example.com')).not.toBeInTheDocument())
		reveal.mockRestore()
	})
})

describe('PaymentCustomerSearchPage', () => {
	it('고른 기준 하나로만 검색한다', async () => {
		const search = vi.spyOn(adminApi, 'searchPaymentCustomers').mockResolvedValue({ matches: [match()] })
		renderWithRouter(<PaymentCustomerSearchPage me={me('OPERATOR')} />)

		const user = userEvent.setup()
		await user.type(screen.getByLabelText('이메일'), 'gildong@example.com')
		await user.click(screen.getByRole('button', { name: '검색' }))

		expect(search).toHaveBeenCalledWith('email', 'gildong@example.com')
		expect(await screen.findByText('gi***@example.com')).toBeInTheDocument()
		search.mockRestore()
	})

	it('기준을 휴대전화로 바꾸면 그 조건으로 보낸다', async () => {
		const search = vi.spyOn(adminApi, 'searchPaymentCustomers').mockResolvedValue({ matches: [] })
		renderWithRouter(<PaymentCustomerSearchPage me={me('OPERATOR')} />)

		const user = userEvent.setup()
		await user.click(screen.getByLabelText('휴대전화로 검색'))
		await user.type(screen.getByLabelText('휴대전화'), '010-1234-5678')
		await user.click(screen.getByRole('button', { name: '검색' }))

		expect(search).toHaveBeenCalledWith('phone', '010-1234-5678')
		search.mockRestore()
	})

	/** 못 찾은 것과 오류를 섞으면 운영자가 오타를 의심하지 못한다. */
	it('결과가 없으면 오류가 아니라 없다고 알려준다', async () => {
		const search = vi.spyOn(adminApi, 'searchPaymentCustomers').mockResolvedValue({ matches: [] })
		renderWithRouter(<PaymentCustomerSearchPage me={me('SUPER_ADMIN')} />)

		const user = userEvent.setup()
		await user.type(screen.getByLabelText('이메일'), 'nobody@example.com')
		await user.click(screen.getByRole('button', { name: '검색' }))

		expect(await screen.findByText(/정확히 일치하는 구매자가 없습니다/)).toBeInTheDocument()
		search.mockRestore()
	})
})

describe('사이드바', () => {
	it('VIEWER에게는 구매자 조회 메뉴를 감춘다', () => {
		renderWithRouter(
			<ConsoleShell me={me('VIEWER')}>
				<div />
			</ConsoleShell>,
		)
		expect(screen.queryByRole('link', { name: /구매자 조회/ })).not.toBeInTheDocument()
	})

	it('OPERATOR에게는 구매자 조회 메뉴를 보여준다', () => {
		renderWithRouter(
			<ConsoleShell me={me('OPERATOR')}>
				<div />
			</ConsoleShell>,
		)
		expect(screen.getByRole('link', { name: /구매자 조회/ })).toBeInTheDocument()
	})
})
