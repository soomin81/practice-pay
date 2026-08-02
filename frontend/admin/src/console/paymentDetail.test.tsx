import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router-dom'
import { renderWithRouter } from '@/test-utils'
import { PaymentDetailPage } from '@/console/PaymentDetailPage'
import type { PaymentDetailResponse } from '@/api/types'

/**
 * 상세 화면의 목적은 **"돈이 어디 있나"에 답하는 것**이다. 그래서 가장 중요한 단언은
 * "값이 예쁘게 나온다"가 아니라 **아직 진행되지 않은 단계를 감추지 않는다**는 것이다 —
 * 감추면 "없는 것"과 "아직인 것"이 화면에서 같아져 진단이 불가능해진다.
 */

/**
 * **`overrides`가 느슨한 타입인 이유**: 서버는 진행되지 않은 단계를 `null`로 내려주는데,
 * 생성된 타입은 그걸 `undefined`(optional)로만 표현한다 — restdocs-api-spec이 OpenAPI의
 * `nullable`을 표현하지 못해서다. 픽스처는 **실제 응답 그대로 `null`**을 써야 의미가 있으므로
 * 여기서만 타입을 느슨하게 둔다. 화면 코드는 `?`(truthy) 검사라 둘 다 안전하게 처리한다.
 */
function detail(overrides: Record<string, unknown> = {}): PaymentDetailResponse {
	return {
		payment: {
			paymentId: 'pay_001',
			merchantId: 'mrc_001',
			merchantName: '테스트 가맹점',
			merchantOrderId: 'order-001',
			orderName: '테스트 주문',
			orderAmount: 20000,
			orderCurrency: 'KRW',
			paymentAsset: 'USDC',
			paymentAmount: '14357502',
			tokenDecimals: 6,
			network: 'BASE_SEPOLIA',
			receivingWallet: '0x9dC10cd9f75B98DE43c8B8B40D4c6B4DA5Cab9e1',
			customerWallet: '0xb2d9b0e2298fe19d41883b7490fd430097167f68',
			status: 'SUCCEEDED',
			failureReason: null,
			expiresAt: '2026-08-01T04:30:00Z',
			paidAt: '2026-08-01T04:07:24Z',
			createdAt: '2026-08-01T04:00:00Z',
		},
		quote: {
			marketProviderCode: 'FAKE',
			marketRate: 1400,
			appliedRate: 1393,
			spreadRate: 0.005,
			quotedAt: '2026-08-01T04:00:00Z',
			expiresAt: '2026-08-01T04:30:00Z',
		},
		checkoutSession: {
			checkoutSessionId: 'cs_001',
			status: 'PAYMENT_SUBMITTED',
			connectedWallet: '0xb2d9b0e2298fe19d41883b7490fd430097167f68',
			expiresAt: '2026-08-01T04:30:00Z',
		},
		blockchainTransaction: {
			transactionHash: '0x40a0473b2bbd7c9b63b0e11fca3141b9b0ab046b749dba71d413ece12f208e90',
			status: 'CONFIRMED',
			blockNumber: 44910246,
			confirmationCount: 433,
			requiredConfirmationCount: 12,
			fromAddress: '0xb2d9b0e2298fe19d41883b7490fd430097167f68',
			toAddress: '0x9dc10cd9f75b98de43c8b8b40d4c6b4da5cab9e1',
			tokenContractAddress: '0x036CbD53842c5426634e7929541eC2318f3dCF7e',
			amountMinor: '14357502',
			failureCode: null,
			submittedAt: '2026-08-01T04:05:00Z',
			detectedAt: '2026-08-01T04:06:00Z',
			confirmedAt: '2026-08-01T04:07:24Z',
		},
		exchangeOrder: {
			exchangeOrderId: 'exo_001',
			providerCode: 'FAKE',
			status: 'COMPLETED',
			executedAmount: '14357502',
			averageExecutionRate: 1400,
			receivedAmount: 20101,
			feeAmount: 0,
			completedAt: '2026-08-01T04:08:00Z',
		},
		settlementReceivable: {
			settlementReceivableId: 'str_001',
			status: 'READY',
			grossAmount: 20000,
			feeRate: 0.015,
			feeAmount: 300,
			adjustmentAmount: 0,
			netAmount: 19700,
			exchangeProfitLossAmount: 101,
			eligibleDate: '2026-08-01',
		},
		webhookDeliveries: [
			{
				webhookDeliveryId: 'whd_001',
				eventType: 'payment.created',
				destinationUrl: 'http://localhost:9000/webhook',
				status: 'SUCCEEDED',
				attemptCount: 1,
				lastHttpStatus: 200,
				lastErrorMessage: null,
				nextRetryAt: null,
				deliveredAt: '2026-08-01T04:00:05Z',
				createdAt: '2026-08-01T04:00:01Z',
			},
		],
		...overrides,
	} as PaymentDetailResponse
}

function stubFetch(body: unknown, status = 200) {
	vi.stubGlobal(
		'fetch',
		vi.fn().mockResolvedValue({ ok: status >= 200 && status < 300, status, json: async () => body }),
	)
}

function renderDetail() {
	return renderWithRouter(
		<Routes>
			<Route path="/payments/:paymentId" element={<PaymentDetailPage />} />
		</Routes>,
		{ route: '/payments/pay_001' },
	)
}

afterEach(() => {
	vi.unstubAllGlobals()
	vi.restoreAllMocks()
})

describe('결제 상세', () => {
	it('완주한 결제의 각 단계를 보여준다', async () => {
		stubFetch(detail())
		renderDetail()

		expect(await screen.findByText('테스트 주문')).toBeInTheDocument()
		// 토큰 금액은 Minor Unit 문자열을 자리수만 잘라 쓴다.
		expect(screen.getAllByText(/14\.357502 USDC/).length).toBeGreaterThan(0)
		// 주문 금액과 정산 기준 금액이 같은 값이라 둘 다 잡힌다 — 개수를 세지 않는다.
		expect(screen.getAllByText('20,000원').length).toBeGreaterThan(0)
		expect(screen.getByText('19,700원')).toBeInTheDocument()
		expect(screen.getByText(/433 \/ 12 Confirm/)).toBeInTheDocument()
	})

	/**
	 * **이 테스트가 이 화면의 핵심이다** — 진행되지 않은 단계를 감추면 "없는 것"과
	 * "아직인 것"이 구분되지 않아 운영자가 진단할 수 없다.
	 */
	it('아직 진행되지 않은 단계를 감추지 않고 이유를 적는다', async () => {
		stubFetch(
			detail({
				blockchainTransaction: null,
				exchangeOrder: null,
				settlementReceivable: null,
				webhookDeliveries: [],
			}),
		)
		renderDetail()

		expect(await screen.findByText(/거래 Hash를 제출하지 않았습니다/)).toBeInTheDocument()
		expect(screen.getByText(/아직 매도되지 않았습니다/)).toBeInTheDocument()
		expect(screen.getByText(/정산 채권이 만들어지지 않았습니다/)).toBeInTheDocument()
		expect(screen.getByText(/전송 이력이 없습니다/)).toBeInTheDocument()
	})

	/**
	 * 결제가 실패해도 **온체인 수령 사실은 남고 화면에 보여야 한다**(ADR-007) — 실패가
	 * "돈이 오지 않았다"를 뜻하지 않기 때문이다.
	 */
	it('실패한 결제에서도 온체인 기록을 보여준다', async () => {
		stubFetch(
			detail({
				payment: { ...detail().payment, status: 'FAILED', failureReason: 'AMOUNT_INSUFFICIENT', paidAt: null },
			}),
		)
		renderDetail()

		expect(await screen.findByText('FAILED')).toBeInTheDocument()
		expect(screen.getByText('AMOUNT_INSUFFICIENT')).toBeInTheDocument()
		expect(screen.getByText(/0x40a0473b/)).toBeInTheDocument()
	})

	it('없는 결제는 404 안내를 보여준다', async () => {
		stubFetch({ message: '결제를 찾을 수 없습니다.' }, 404)
		renderDetail()

		expect(await screen.findByText('결제를 찾을 수 없습니다.')).toBeInTheDocument()
	})

	/**
	 * **성공한 전송을 다시 보내는 것은 재전송이 아니라 중복 발송이다** — 서버도 409로
	 * 거절하므로, 누를 수 있게 두고 거부하는 것보다 아예 그리지 않는다.
	 */
	it('성공한 전송에는 재전송 버튼을 그리지 않는다', async () => {
		stubFetch(detail())
		renderDetail()

		expect(await screen.findByText('payment.created')).toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '재전송' })).not.toBeInTheDocument()
	})

	it('실패한 전송에는 재전송 버튼을 그린다', async () => {
		stubFetch(detail({ webhookDeliveries: [failedDelivery()] }))
		renderDetail()

		expect(await screen.findByRole('button', { name: '재전송' })).toBeInTheDocument()
	})

	/**
	 * **"보냈다"가 아니라 "예약했다"고 말해야 한다.** 실제 발송은 발행 Worker가 하므로 이
	 * 시점에는 결과를 모른다 — 성공으로 읽히면 사용자가 거짓 정보를 갖는다.
	 */
	it('재전송을 누르면 예약됐다고 알리고 성공이라고 말하지 않는다', async () => {
		const fetchMock = vi.fn().mockImplementation((_url: string, init?: RequestInit) => {
			if (init?.method === 'POST') {
				return Promise.resolve({
					ok: true,
					status: 200,
					json: async () => ({ webhookDeliveryId: 'whd_001', status: 'PENDING', attemptCount: 5 }),
				})
			}
			return Promise.resolve({ ok: true, status: 200, json: async () => detail({ webhookDeliveries: [failedDelivery()] }) })
		})
		vi.stubGlobal('fetch', fetchMock)
		renderDetail()

		await userEvent.click(await screen.findByRole('button', { name: '재전송' }))

		expect(await screen.findByText(/재전송을 예약했습니다/)).toBeInTheDocument()
		const posted = fetchMock.mock.calls.find(([, init]) => (init as RequestInit | undefined)?.method === 'POST')
		expect(String(posted?.[0])).toContain('/admin/webhook-deliveries/whd_001/redeliver')
	})

	/** `409`는 "왜 안 되는지"를 담고 있다 — 그대로 보여주지 않으면 같은 버튼을 계속 누른다. */
	it('재전송할 수 없는 상태면 서버가 준 이유를 보여준다', async () => {
		const fetchMock = vi.fn().mockImplementation((_url: string, init?: RequestInit) => {
			if (init?.method === 'POST') {
				return Promise.resolve({
					ok: false,
					status: 409,
					json: async () => ({ message: '실패한 전송만 다시 보낼 수 있습니다. 현재 상태: SUCCEEDED' }),
				})
			}
			return Promise.resolve({ ok: true, status: 200, json: async () => detail({ webhookDeliveries: [failedDelivery()] }) })
		})
		vi.stubGlobal('fetch', fetchMock)
		renderDetail()

		await userEvent.click(await screen.findByRole('button', { name: '재전송' }))

		expect(await screen.findByText(/현재 상태: SUCCEEDED/)).toBeInTheDocument()
	})
})

/** 자동 재시도를 소진해 `FAILED`로 끝난 전송 — 재전송 버튼이 나오는 유일한 상태다. */
function failedDelivery() {
	return {
		webhookDeliveryId: 'whd_001',
		eventType: 'payment.succeeded',
		destinationUrl: 'http://localhost:9000/webhook',
		status: 'FAILED',
		attemptCount: 5,
		lastHttpStatus: 500,
		lastErrorMessage: 'HTTP 500',
		nextRetryAt: null,
		deliveredAt: null,
		createdAt: '2026-08-01T04:00:01Z',
	}
}
