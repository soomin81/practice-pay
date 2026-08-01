import { useState } from 'react'
import { useAccount, useConnect, useSwitchChain, useWriteContract } from 'wagmi'
import { UserRejectedRequestError, getAddress } from 'viem'
import { CheckoutApiError, checkoutApi } from '@/api/client'
import type { CheckoutSession } from '@/api/types'
import { erc20Abi } from './erc20'
import { wagmiConfig } from './config'

/** wagmi 설정에 들어 있는 체인들의 id. 설정이 바뀌면 이 타입이 따라 바뀐다. */
type SupportedChainId = (typeof wagmiConfig)['chains'][number]['id']

/**
 * 지갑 연결 → USDC 전송 → Hash 제출까지의 흐름.
 *
 * 계약 문서 7절의 순서를 그대로 따른다:
 *   지갑 연결 → `POST /wallet` → ERC-20 `transfer` 서명·브로드캐스트 → `POST /transaction`
 *   → 이후는 상태 폴링이 이어받는다.
 *
 * **여기서 결제 성공을 판단하지 않는다.** `POST /transaction`의 성공 응답은 "제출을
 * 접수했다"는 뜻이지 "결제됐다"가 아니다(계약 4.4). 확정은 백엔드 Confirm Worker가
 * 하고, 화면은 폴링이 주는 상태를 따른다.
 */
export type PaymentStep =
	| 'idle'
	/** 지갑 확장에 연결 요청을 보냈다. 고객이 지갑 창에서 승인해야 한다. */
	| 'connecting'
	/** 연결된 지갑을 서버에 등록하는 중(`POST /wallet`). */
	| 'registering'
	/** 지갑이 다른 체인에 있어 전환을 요청했다. */
	| 'switchingChain'
	/** 전송 서명을 요청했다. 고객이 지갑 창에서 승인해야 한다. */
	| 'signing'
	/** Hash를 서버에 제출하는 중(`POST /transaction`). */
	| 'submitting'

export function useWalletPayment({
	session,
	onSubmitted,
}: {
	session: CheckoutSession
	/** 제출이 접수된 뒤 호출된다. 호출부가 세션을 다시 읽어 폴링 화면으로 넘어간다. */
	onSubmitted: () => void
}) {
	const [step, setStep] = useState<PaymentStep>('idle')
	const [error, setError] = useState<string | null>(null)

	const { address, isConnected, chainId } = useAccount()
	const { connectAsync, connectors } = useConnect()
	const { switchChainAsync } = useSwitchChain()
	const { writeContractAsync } = useWriteContract()

	const targetChainId = session.payment.chainId
	const busy = step !== 'idle'

	/** 지갑 확장을 찾지 못하면 연결 버튼 자체를 띄우지 않는다. */
	const connector = connectors.find((candidate) => candidate.type === 'injected') ?? connectors[0]

	async function connect() {
		setError(null)
		setStep('connecting')
		try {
			const result = await connectAsync({ connector })
			const connected = result.accounts[0]

			setStep('registering')
			await registerWallet(session.checkoutSessionId, connected)
			setStep('idle')
		} catch (cause) {
			setError(toMessage(cause))
			setStep('idle')
		}
	}

	async function pay() {
		if (!address) {
			setError('지갑이 연결되어 있지 않습니다.')
			return
		}

		setError(null)
		try {
			// **이 세션에 지갑이 등록돼 있지 않으면 먼저 등록한다.**
			//
			// 등록을 [connect]에만 맡기면 안 된다 — wagmi는 연결 상태를 브라우저에 저장하므로,
			// 새 체크아웃 세션을 열었을 때 화면은 이미 `isConnected`라 연결 버튼을 건너뛰고
			// 곧장 "보내기"를 보여준다. 그러면 **그 세션은 지갑이 등록된 적이 없어** 전송
			// 서명까지 끝난 뒤 `POST /transaction`이 "연결된 지갑이 없습니다"로 거부한다 —
			// 고객은 USDC를 이미 보낸 뒤다. 등록은 연결이라는 *동작*이 아니라 **세션의
			// 상태**에 묶여야 한다.
			if (!session.connectedWallet) {
				setStep('registering')
				await registerWallet(session.checkoutSessionId, address)
			}

			// 서버가 준 chainId를 앱이 실제로 말을 걸 수 있는 체인인지 확인해서 좁힌다.
			// 모르는 체인이면 여기서 멈춘다 — 추측해서 보내면 돈이 사라진다.
			const target = asSupportedChainId(targetChainId)

			// 지갑이 다른 체인에 있으면 전환을 요청한다. 전환에 실패해도 그대로 멈춘다.
			if (chainId !== target) {
				setStep('switchingChain')
				await switchChainAsync({ chainId: target })
			}

			setStep('signing')
			// 금액은 Minor Unit 문자열을 BigInt로만 옮긴다. Number를 거치면 안전 정수
			// 범위를 넘을 때 조용히 값이 달라진다.
			const transactionHash = await writeContractAsync({
				address: asAddress(session.payment.tokenContractAddress, '토큰 Contract 주소'),
				abi: erc20Abi,
				functionName: 'transfer',
				args: [asAddress(session.payment.receivingWallet, '수취 지갑 주소'), BigInt(session.payment.amount)],
				chainId: target,
			})

			setStep('submitting')
			await checkoutApi.submitTransaction(session.checkoutSessionId, transactionHash)

			setStep('idle')
			onSubmitted()
		} catch (cause) {
			setError(toMessage(cause))
			setStep('idle')
		}
	}

	return {
		step,
		busy,
		error,
		address,
		isConnected,
		hasConnector: connector !== undefined,
		/** 지갑이 결제 대상 체인에 붙어 있는가. 연결 전에는 판단하지 않는다. */
		onTargetChain: isConnected ? chainId === targetChainId : null,
		connect,
		pay,
	}
}

/**
 * 연결한 지갑을 서버에 등록한다.
 *
 * **이미 `WALLET_CONNECTED`면 409가 오는데, 이것은 오류가 아니다**(계약 4.3: 재연결은
 * 지원하지 않는다). 새로고침하고 같은 지갑으로 다시 연결하면 정상적으로 이 경로를 탄다.
 */
async function registerWallet(sessionId: string, walletAddress: string) {
	try {
		await checkoutApi.connectWallet(sessionId, walletAddress)
	} catch (cause) {
		if (cause instanceof CheckoutApiError && cause.isConflict) return
		throw cause
	}
}

/**
 * 서버가 준 `chainId`가 이 앱이 말을 걸 수 있는 체인인지 확인한다.
 *
 * 타입만 맞추려면 캐스팅으로 끝낼 수 있지만 그러면 안 된다 — 백엔드가 네트워크를
 * 추가했는데 프론트 설정이 따라오지 않은 상황에서, 지갑에게 "그 체인으로 보내"라고
 * 시키게 된다. 모르면 보내지 않는 쪽이 맞다.
 */
export function asSupportedChainId(chainId: number): SupportedChainId {
	const supported = wagmiConfig.chains.find((chain) => chain.id === chainId)
	if (!supported) {
		throw new Error(`이 페이지가 지원하지 않는 네트워크입니다 (chainId ${chainId}).`)
	}
	return supported.id
}

/**
 * viem은 주소를 `0x…` 리터럴 타입으로 받는다. 형식을 확인하고 좁힌다 — 캐스팅만 하면
 * 잘못된 값이 그대로 지갑까지 간다.
 *
 * **[getAddress]로 EIP-55 체크섬(대소문자)을 정규화한다.** viem은 인코딩 단계에서 체크섬이
 * 맞지 않으면 거부하는데, 백엔드의 `WalletAddress`는 체크섬을 검증하지 않는다(의도적 —
 * "EIP-55 checksum 검증은 하지 않는다"). 그래서 수취 지갑을 소문자로 설정하면(주소를
 * 다루는 도구 상당수가 소문자로 출력한다) **결제가 지갑 단계에서
 * "Address must match its checksum counterpart"로 막힌다.** 정규화는 대소문자만 바꾸므로
 * 주소 자체는 달라지지 않는다.
 *
 * **알려진 gap**: 이 정규화는 운영자가 수취 지갑을 오타로 넣은 경우를 잡아주지 못한다.
 * 원래도 백엔드가 잡지 않았으므로 여기서 잃는 보호는 없지만, 오타를 실제로 막으려면
 * 백엔드가 설정을 읽는 시점에 체크섬을 검증해 기동을 실패시키는 편이 맞다.
 */
export function asAddress(value: string, label: string): `0x${string}` {
	if (!/^0x[0-9a-fA-F]{40}$/.test(value)) {
		throw new Error(`${label} 형식이 올바르지 않습니다: ${value}`)
	}
	return getAddress(value)
}

function toMessage(cause: unknown): string {
	// 고객이 지갑 창에서 거절한 것은 오류가 아니라 선택이다 — 문구를 다르게 준다.
	if (cause instanceof UserRejectedRequestError) return '지갑에서 요청을 거절했습니다.'
	if (cause instanceof CheckoutApiError) return cause.message
	if (cause instanceof Error) return cause.message
	return String(cause)
}
