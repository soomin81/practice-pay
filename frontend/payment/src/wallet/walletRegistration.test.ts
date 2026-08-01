import { renderHook, act } from '@testing-library/react'
import { beforeEach, expect, test, vi } from 'vitest'
import type { CheckoutSession } from '@/api/types'

/**
 * **실물 검증에서 잡은 회귀다.**
 *
 * 지갑 등록(`POST /wallet`)이 "지갑 연결" 버튼을 누르는 *동작*에만 묶여 있었다. wagmi는
 * 연결 상태를 브라우저에 저장하므로, 새 체크아웃 세션을 열면 화면은 이미 `isConnected`라
 * 연결 버튼을 건너뛰고 곧장 "보내기"를 보여준다 — 그러면 **그 세션은 지갑이 등록된 적이
 * 없어**, 고객이 USDC를 실제로 보낸 **뒤에** `POST /transaction`이 "연결된 지갑이 없습니다"로
 * 거부한다.
 *
 * **돈은 나갔는데 결제는 실패하는** 가장 나쁜 종류의 실패라, 등록이 연결 동작이 아니라
 * **세션 상태**에 묶여 있는지를 여기서 고정한다.
 */

const connectWallet = vi.fn()
const submitTransaction = vi.fn()
const writeContractAsync = vi.fn()

vi.mock('@/api/client', async (importOriginal) => ({
	...(await importOriginal<object>()),
	checkoutApi: {
		connectWallet: (...args: unknown[]) => connectWallet(...args),
		submitTransaction: (...args: unknown[]) => submitTransaction(...args),
	},
}))

// wagmi는 훅만 갈아끼운다 — `createConfig`까지 가짜로 만들면 `wallet/config.ts`가 깨진다.
vi.mock('wagmi', async (importOriginal) => ({
	...(await importOriginal<object>()),
	useAccount: () => ({ address: SENDER, isConnected: true, chainId: 84532 }),
	useConnect: () => ({ connectAsync: vi.fn(), connectors: [{ type: 'injected' }] }),
	useSwitchChain: () => ({ switchChainAsync: vi.fn() }),
	useWriteContract: () => ({ writeContractAsync }),
}))

const { useWalletPayment } = await import('./useWalletPayment')

const SENDER = '0xb2d9b0e2298fe19d41883b7490fd430097167f68'
const TX_HASH = '0x40a0473b2bbd7c9b63b0e11fca3141b9b0ab046b749dba71d413ece12f208e90'

function session(connectedWallet: string | null): CheckoutSession {
	return {
		checkoutSessionId: 'cs_test_1',
		checkoutSessionStatus: connectedWallet ? 'WALLET_CONNECTED' : 'CREATED',
		expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
		successUrl: 'https://merchant.example.com/done',
		cancelUrl: null,
		connectedWallet,
		order: { orderAmount: 20000, orderCurrency: 'KRW', orderName: '개발용 테스트 주문' },
		quote: {
			quotedAt: new Date().toISOString(),
			appliedRate: '1393',
			expiresAt: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
		},
		payment: {
			paymentId: 'pay_test_1',
			paymentStatus: 'READY',
			amount: '14357502',
			asset: 'USDC',
			network: 'BASE_SEPOLIA',
			chainId: 84532,
			tokenDecimals: 6,
			tokenContractAddress: '0x036CbD53842c5426634e7929541eC2318f3dCF7e',
			// 소문자다 — 백엔드가 EIP-55를 검증하지 않아 실제로 이렇게 내려온다.
			receivingWallet: '0x9dc10cd9f75b98de43c8b8b40d4c6b4da5cab9e1',
			requiredConfirmationCount: 12,
		},
	}
}

beforeEach(() => {
	vi.clearAllMocks()
	writeContractAsync.mockResolvedValue(TX_HASH)
	connectWallet.mockResolvedValue(undefined)
	submitTransaction.mockResolvedValue(undefined)
})

test('세션에 등록된 지갑이 없으면 전송 전에 등록한다', async () => {
	const { result } = renderHook(() => useWalletPayment({ session: session(null), onSubmitted: vi.fn() }))

	await act(async () => {
		await result.current.pay()
	})

	expect(connectWallet).toHaveBeenCalledWith('cs_test_1', SENDER)
	expect(submitTransaction).toHaveBeenCalledWith('cs_test_1', TX_HASH)
})

test('등록이 서명보다 먼저 일어난다 — 순서가 뒤집히면 돈만 나가고 제출이 거부된다', async () => {
	const order: string[] = []
	connectWallet.mockImplementation(async () => void order.push('register'))
	writeContractAsync.mockImplementation(async () => {
		order.push('sign')
		return TX_HASH
	})

	const { result } = renderHook(() => useWalletPayment({ session: session(null), onSubmitted: vi.fn() }))
	await act(async () => {
		await result.current.pay()
	})

	expect(order).toEqual(['register', 'sign'])
})

test('이미 등록된 세션이면 다시 등록하지 않는다', async () => {
	const { result } = renderHook(() => useWalletPayment({ session: session(SENDER), onSubmitted: vi.fn() }))

	await act(async () => {
		await result.current.pay()
	})

	expect(connectWallet).not.toHaveBeenCalled()
	expect(submitTransaction).toHaveBeenCalledWith('cs_test_1', TX_HASH)
})

test('제출이 접수되면 호출부에 알린다', async () => {
	const onSubmitted = vi.fn()
	const { result } = renderHook(() => useWalletPayment({ session: session(null), onSubmitted }))

	await act(async () => {
		await result.current.pay()
	})

	expect(onSubmitted).toHaveBeenCalledOnce()
})
