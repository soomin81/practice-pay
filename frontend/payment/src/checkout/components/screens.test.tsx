import { render, screen } from '@testing-library/react'
import { expect, test } from 'vitest'
import type { CheckoutSession } from '@/api/types'
import { ConfirmationProgress } from './ConfirmationProgress'
import { PaymentDetails } from './PaymentDetails'
import { PaymentSummary } from './PaymentSummary'
import { StatusScreen } from './StatusScreen'

/**
 * 화면 컴포넌트 스모크 테스트.
 *
 * 지갑 연결은 자동 테스트가 불가능하지만(MetaMask 확장 필요) **화면이 서버 값을 그대로
 * 그리는지**는 확인할 수 있다. 여기서 잡으려는 것은 두 가지다.
 *  1. 컴포넌트가 마운트 중에 터지지 않는다.
 *  2. 금액·주소·Confirmation이 **서버가 준 값 그대로** 나온다 — 특히 토큰 금액을
 *     Number로 변환하면 여기서 깨진다.
 */

/** 안전 정수 범위(2^53-1)를 넘는 금액을 일부러 쓴다 — Number로 바꾸면 값이 달라진다. */
const HUGE_MINOR_AMOUNT = '9007199254740993123'

function sessionFixture(overrides: Partial<CheckoutSession> = {}): CheckoutSession {
	return {
		checkoutSessionId: 'cs_test_1',
		checkoutSessionStatus: 'OPEN',
		expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
		successUrl: 'https://merchant.example.com/done',
		cancelUrl: 'https://merchant.example.com/cancel',
		connectedWallet: null,
		order: { orderAmount: 50000, orderCurrency: 'KRW', orderName: '개발용 테스트 주문' },
		quote: {
			quotedAt: new Date().toISOString(),
			appliedRate: '1350.25',
			expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
		},
		payment: {
			paymentId: 'pay_test_1',
			paymentStatus: 'READY',
			amount: '35893755',
			asset: 'USDC',
			network: 'BASE_SEPOLIA',
			chainId: 84532,
			tokenDecimals: 6,
			tokenContractAddress: '0x036CbD53842c5426634e7929541eC2318f3dCF7e',
			receivingWallet: '0x1234567890abcdef1234567890abcdef12345678',
			requiredConfirmationCount: 3,
		},
		...overrides,
	}
}

test('결제 요약이 주문 금액과 보낼 토큰 금액을 함께 보여준다', () => {
	render(<PaymentSummary session={sessionFixture()} />)

	expect(screen.getByText('개발용 테스트 주문')).toBeInTheDocument()
	expect(screen.getByText(/50,000/)).toBeInTheDocument()
	// 35893755 minor unit / 6 decimals
	expect(screen.getByText(/35\.893755/)).toBeInTheDocument()
})

test('안전 정수 범위를 넘는 토큰 금액도 정확히 표시한다', () => {
	const session = sessionFixture()
	render(<PaymentSummary session={{ ...session, payment: { ...session.payment, amount: HUGE_MINOR_AMOUNT } }} />)

	// Number를 거쳤다면 끝자리가 달라진다.
	expect(screen.getByText(/9,007,199,254,740\.993123/)).toBeInTheDocument()
})

test('결제 상세가 체인 정보를 서버 값 그대로 보여준다', () => {
	render(<PaymentDetails session={sessionFixture()} />)

	expect(screen.getByText(/BASE_SEPOLIA/)).toBeInTheDocument()
	expect(screen.getByText(/84532/)).toBeInTheDocument()
	expect(screen.getByText(/1350\.25/)).toBeInTheDocument()
	// 주소는 줄여 보여주되 전체 값을 title에 남긴다 — 고객이 지갑 화면과 대조해야 한다.
	expect(screen.getByTitle('0x036CbD53842c5426634e7929541eC2318f3dCF7e')).toHaveTextContent('0x036C…CF7e')
	expect(screen.getByTitle('0x1234567890abcdef1234567890abcdef12345678')).toHaveTextContent('0x1234…5678')
})

test('확인 진행률이 서버가 준 Confirmation 수를 그대로 보여준다', () => {
	render(<ConfirmationProgress confirmations={2} required={3} transactionHash={null} />)

	expect(screen.getByText(/2 \/ 3 confirmations/)).toBeInTheDocument()
	expect(screen.getByRole('progressbar')).toHaveAccessibleName('확인 2 / 3')
})

test('required가 0이어도 진행률이 깨지지 않는다', () => {
	render(<ConfirmationProgress confirmations={0} required={0} transactionHash={null} />)

	expect(screen.getByText(/0 \/ 0 confirmations/)).toBeInTheDocument()
})

test('상태 화면이 제목과 안내 문구를 보여준다', () => {
	render(<StatusScreen tone="success" title="결제가 완료되었습니다" description="가맹점 페이지로 이동합니다." />)

	expect(screen.getByRole('heading', { name: '결제가 완료되었습니다' })).toBeInTheDocument()
	expect(screen.getByText('가맹점 페이지로 이동합니다.')).toBeInTheDocument()
})
