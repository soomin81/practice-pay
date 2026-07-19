import { beforeEach, describe, expect, test, vi } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import { CheckoutApiError, checkoutApi } from '../api/client'
import type { CheckoutSession, CheckoutStatus } from '../api/types'
import { renderWithQuery } from '../test-utils'
import { CheckoutPage } from './CheckoutPage'

/**
 * 상태 → 화면 매핑 테스트.
 *
 * `CheckoutPage`는 **서버가 준 상태를 보고 어떤 화면을 그릴지만** 정한다
 * (`docs/architecture/checkout-api.md` 6절: 프론트가 다음 상태를 스스로 추론하지
 * 않는다). 그 매핑이 틀리면 컴파일은 통과하는데 고객에게 엉뚱한 화면이 뜬다 —
 * 만료된 결제에 "다시 시도"가 뜨거나, 실패한 결제에 완료 화면이 뜨는 식이다.
 *
 * 지갑 연동은 여기서 관심사가 아니라 훅째로 대체한다. 지갑 흐름 자체는
 * `wallet/guards.test.ts`와 실물 수동 검증이 담당한다.
 */

vi.mock('../api/client', async (importOriginal) => {
	const actual = await importOriginal<typeof import('../api/client')>()
	return {
		...actual,
		checkoutApi: { getSession: vi.fn(), getStatus: vi.fn(), cancel: vi.fn() },
	}
})

vi.mock('@/wallet/useWalletPayment', () => ({
	useWalletPayment: () => ({
		step: 'idle',
		busy: false,
		error: null,
		address: undefined,
		isConnected: false,
		hasConnector: true,
		onTargetChain: null,
		connect: vi.fn(),
		pay: vi.fn(),
	}),
}))

const api = vi.mocked(checkoutApi)

function sessionFixture(overrides: Partial<CheckoutSession> = {}): CheckoutSession {
	return {
		checkoutSessionId: 'cs_1',
		checkoutSessionStatus: 'OPEN',
		expiresAt: new Date(Date.now() + 600_000).toISOString(),
		successUrl: 'https://merchant.example.com/done',
		cancelUrl: 'https://merchant.example.com/cancel',
		connectedWallet: null,
		order: { orderAmount: 50000, orderCurrency: 'KRW', orderName: '프리미엄 구독 1개월' },
		quote: {
			quotedAt: new Date().toISOString(),
			appliedRate: '1393.000000000000',
			expiresAt: new Date(Date.now() + 600_000).toISOString(),
		},
		payment: {
			paymentId: 'pay_1',
			paymentStatus: 'READY',
			amount: '35893755',
			asset: 'USDC',
			network: 'BASE_SEPOLIA',
			chainId: 84532,
			tokenDecimals: 6,
			tokenContractAddress: '0x036CbD53842c5426634e7929541eC2318f3dCF7e',
			receivingWallet: '0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
			requiredConfirmationCount: 12,
		},
		...overrides,
	}
}

function statusFixture(overrides: Partial<CheckoutStatus> = {}): CheckoutStatus {
	return {
		checkoutSessionStatus: 'PAYMENT_SUBMITTED',
		paymentStatus: 'PROCESSING',
		confirmationCount: 3,
		requiredConfirmationCount: 12,
		transactionHash: '0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef',
		failureReason: null,
		redirectUrl: null,
		...overrides,
	}
}

beforeEach(() => {
	vi.clearAllMocks()
	api.getStatus.mockResolvedValue(statusFixture())
})

describe('조회 실패', () => {
	test('404면 "찾을 수 없습니다"를 보여준다', async () => {
		api.getSession.mockRejectedValue(new CheckoutApiError(404, '없음'))

		renderWithQuery(<CheckoutPage sessionId="cs_1" />)

		expect(await screen.findByRole('heading', { name: '결제를 찾을 수 없습니다' })).toBeInTheDocument()
	})

	test('410이면 "다시 시도"가 아니라 만료 화면을 보여준다', async () => {
		// 계약이 만료를 409가 아닌 410으로 분리한 이유가 이 화면이다.
		api.getSession.mockRejectedValue(new CheckoutApiError(410, '만료'))

		renderWithQuery(<CheckoutPage sessionId="cs_1" />)

		expect(await screen.findByRole('heading', { name: '결제 시간이 만료되었습니다' })).toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '다시 시도' })).not.toBeInTheDocument()
	})

	test('그 밖의 오류는 다시 시도할 수 있게 한다', async () => {
		api.getSession.mockRejectedValue(new CheckoutApiError(0, '결제 서버에 연결하지 못했습니다.'))

		renderWithQuery(<CheckoutPage sessionId="cs_1" />)

		expect(await screen.findByRole('button', { name: '다시 시도' })).toBeInTheDocument()
		expect(screen.getByText('결제 서버에 연결하지 못했습니다.')).toBeInTheDocument()
	})
})

describe('종료 상태 — 조회는 허용된다(계약 3절)', () => {
	test('CANCELLED면 취소 화면과 돌아가기 링크를 보여준다', async () => {
		api.getSession.mockResolvedValue(sessionFixture({ checkoutSessionStatus: 'CANCELLED' }))

		renderWithQuery(<CheckoutPage sessionId="cs_1" />)

		expect(await screen.findByRole('heading', { name: '결제가 취소되었습니다' })).toBeInTheDocument()
		expect(screen.getByRole('link', { name: '가맹점으로 돌아가기' })).toHaveAttribute(
			'href',
			'https://merchant.example.com/cancel',
		)
	})

	test('cancelUrl이 없으면 링크 대신 안내만 보여준다', async () => {
		api.getSession.mockResolvedValue(sessionFixture({ checkoutSessionStatus: 'CANCELLED', cancelUrl: null }))

		renderWithQuery(<CheckoutPage sessionId="cs_1" />)

		expect(await screen.findByText('이 창을 닫으셔도 됩니다.')).toBeInTheDocument()
		expect(screen.queryByRole('link')).not.toBeInTheDocument()
	})

	test('EXPIRED면 만료 화면을 보여준다', async () => {
		api.getSession.mockResolvedValue(sessionFixture({ checkoutSessionStatus: 'EXPIRED' }))

		renderWithQuery(<CheckoutPage sessionId="cs_1" />)

		expect(await screen.findByRole('heading', { name: '결제 시간이 만료되었습니다' })).toBeInTheDocument()
	})
})

describe('제출 이후', () => {
	test('redirectUrl이 채워지면 완료 화면으로 넘어간다', async () => {
		// 프론트가 성공을 추론하지 않는다 — 이 필드가 채워지는 것이 유일한 신호다.
		api.getSession.mockResolvedValue(sessionFixture({ checkoutSessionStatus: 'PAYMENT_SUBMITTED' }))
		api.getStatus.mockResolvedValue(
			statusFixture({ paymentStatus: 'SUCCEEDED', redirectUrl: 'https://merchant.example.com/done' }),
		)

		renderWithQuery(<CheckoutPage sessionId="cs_1" />)

		expect(await screen.findByRole('heading', { name: '결제가 완료되었습니다' })).toBeInTheDocument()
		expect(screen.getByRole('link', { name: '바로 이동하기' })).toHaveAttribute(
			'href',
			'https://merchant.example.com/done',
		)
	})

	test('확인 중이면 진행률을 서버 값 그대로 보여준다', async () => {
		api.getSession.mockResolvedValue(sessionFixture({ checkoutSessionStatus: 'PAYMENT_SUBMITTED' }))

		renderWithQuery(<CheckoutPage sessionId="cs_1" />)

		expect(await screen.findByRole('heading', { name: '결제 확인 중' })).toBeInTheDocument()
		// 세션 조회와 상태 조회가 별개라, 상태가 도착하기 전에는 0으로 그린다 — 도착까지 기다린다.
		expect(await screen.findByText(/3 \/ 12 confirmations/)).toBeInTheDocument()
	})

	test('실패하면 사유 코드를 안내 문구로 바꿔 보여준다', async () => {
		// 계약 4.2: 실패 사유 코드를 고객에게 그대로 노출하지 않는다.
		api.getSession.mockResolvedValue(sessionFixture({ checkoutSessionStatus: 'PAYMENT_SUBMITTED' }))
		api.getStatus.mockResolvedValue(
			statusFixture({ paymentStatus: 'FAILED', failureReason: 'RECEIVING_WALLET_MISMATCH' }),
		)

		renderWithQuery(<CheckoutPage sessionId="cs_1" />)

		expect(await screen.findByRole('heading', { name: '결제가 실패했습니다' })).toBeInTheDocument()
		expect(screen.getByText('전송이 확인되지 않았습니다. 가맹점에 문의해 주세요.')).toBeInTheDocument()
		// 코드는 문의용 보조 정보로만 남는다 — 제목이나 본문 안내가 되지 않는다.
		expect(screen.getByText(/사유 코드: RECEIVING_WALLET_MISMATCH/)).toBeInTheDocument()
		expect(screen.queryByRole('heading', { name: /RECEIVING_WALLET_MISMATCH/ })).not.toBeInTheDocument()
	})

	test('실패가 완료 화면을 이기지 않는다', async () => {
		api.getSession.mockResolvedValue(sessionFixture({ checkoutSessionStatus: 'PAYMENT_SUBMITTED' }))
		api.getStatus.mockResolvedValue(statusFixture({ paymentStatus: 'FAILED', failureReason: 'X' }))

		renderWithQuery(<CheckoutPage sessionId="cs_1" />)

		await screen.findByRole('heading', { name: '결제가 실패했습니다' })
		expect(screen.queryByRole('heading', { name: '결제가 완료되었습니다' })).not.toBeInTheDocument()
	})
})

describe('결제 화면', () => {
	test('OPEN이면 결제 화면을 그리고 금액을 서버 값 그대로 보여준다', async () => {
		api.getSession.mockResolvedValue(sessionFixture())

		renderWithQuery(<CheckoutPage sessionId="cs_1" />)

		expect(await screen.findByText('프리미엄 구독 1개월')).toBeInTheDocument()
		expect(screen.getByText(/35\.893755/)).toBeInTheDocument()
		expect(screen.getByText(/50,000/)).toBeInTheDocument()
		expect(screen.getByRole('button', { name: '결제 취소' })).toBeInTheDocument()
	})

	test('제출 전에는 상태 폴링을 시작하지 않는다', async () => {
		api.getSession.mockResolvedValue(sessionFixture())

		renderWithQuery(<CheckoutPage sessionId="cs_1" />)

		await screen.findByText('프리미엄 구독 1개월')
		// 고객이 아직 아무것도 보내지 않았다 — 물어볼 상태가 없다.
		await waitFor(() => expect(api.getStatus).not.toHaveBeenCalled())
	})
})
