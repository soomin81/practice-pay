import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import { checkoutApi } from '@/api/client'
import type { CheckoutSession } from '@/api/types'
import { PayScreen } from './PayScreen'

/**
 * **순서를 강제하는 것이 이 화면의 책임이다.**
 *
 * 서버는 구매자 정보 없이도 결제를 진행시킨다(API는 어느 쪽이 먼저 와도 받는다). 그래서
 * "지갑보다 구매자 정보가 먼저"라는 ADR-008의 결정은 **여기서 막지 않으면 아무도 막지
 * 않는다** — 서명 이후에 입력을 요구하면 돈은 나갔는데 결제가 미완인 창이 생긴다.
 *
 * 지갑 패널 자체는 wagmi(브라우저 확장)에 의존해 단위 테스트가 불가능하므로, 그것이
 * **렌더링되는지 여부**만 본다. 그게 정확히 이 테스트가 잡으려는 것이다.
 */
vi.mock('./WalletPanel', () => ({
	WalletPanel: () => <div data-testid="wallet-panel">지갑 패널</div>,
}))

const MASKED = {
	checkoutSessionId: 'cs_test_1',
	checkoutSessionStatus: 'OPEN',
	nameMasked: '홍*동',
	emailMasked: 'gi***@example.com',
	phoneMasked: '010-****-5678',
}

function sessionFixture(): CheckoutSession {
	return {
		checkoutSessionId: 'cs_test_1',
		checkoutSessionStatus: 'OPEN',
		expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
		successUrl: 'https://merchant.example.com/done',
		cancelUrl: null,
		connectedWallet: null,
		order: { orderAmount: 1000, orderCurrency: 'KRW', orderName: '테스트 주문' },
		quote: {
			quotedAt: new Date().toISOString(),
			appliedRate: '1393.00',
			expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
		},
		payment: {
			paymentId: 'pay_test_1',
			paymentStatus: 'READY',
			amount: '717876',
			asset: 'USDC',
			network: 'BASE_SEPOLIA',
			chainId: 84532,
			tokenDecimals: 6,
			tokenContractAddress: '0x036CbD53842c5426634e7929541eC2318f3dCF7e',
			receivingWallet: '0x1234567890abcdef1234567890abcdef12345678',
			requiredConfirmationCount: 3,
		},
	}
}

afterEach(() => {
	vi.restoreAllMocks()
})

test('구매자 정보를 넣기 전에는 지갑 단계가 나오지 않는다', () => {
	render(<PayScreen session={sessionFixture()} onSessionChanged={() => {}} />)

	expect(screen.queryByTestId('wallet-panel')).not.toBeInTheDocument()
	expect(screen.getByLabelText('이메일')).toBeInTheDocument()
})

test('구매자 정보를 접수하면 지갑 단계로 넘어간다', async () => {
	vi.spyOn(checkoutApi, 'submitCustomer').mockResolvedValue(MASKED)
	render(<PayScreen session={sessionFixture()} onSessionChanged={() => {}} />)

	const user = userEvent.setup()
	await user.type(screen.getByLabelText('이름'), '홍길동')
	await user.type(screen.getByLabelText('이메일'), 'gildong@example.com')
	await user.type(screen.getByLabelText('휴대전화'), '010-1234-5678')
	await user.click(screen.getByRole('button', { name: '다음' }))

	expect(await screen.findByTestId('wallet-panel')).toBeInTheDocument()
})

/** 확인란에 원문이 그대로 나오면 서버가 마스킹만 돌려준 의미가 없다. */
test('접수된 값은 가려진 형태로만 화면에 남는다', async () => {
	vi.spyOn(checkoutApi, 'submitCustomer').mockResolvedValue(MASKED)
	render(<PayScreen session={sessionFixture()} onSessionChanged={() => {}} />)

	const user = userEvent.setup()
	await user.type(screen.getByLabelText('이름'), '홍길동')
	await user.type(screen.getByLabelText('이메일'), 'gildong@example.com')
	await user.type(screen.getByLabelText('휴대전화'), '010-1234-5678')
	await user.click(screen.getByRole('button', { name: '다음' }))

	expect(await screen.findByText('gi***@example.com')).toBeInTheDocument()
	expect(screen.queryByText('gildong@example.com')).not.toBeInTheDocument()
	expect(screen.queryByText('010-1234-5678')).not.toBeInTheDocument()
})
