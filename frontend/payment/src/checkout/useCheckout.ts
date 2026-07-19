import { useQuery } from '@tanstack/react-query'
import { CheckoutApiError, checkoutApi } from '../api/client'
import type { CheckoutStatus } from '../api/types'

/** 계약(4.2)이 권장하는 폴링 주기. */
const POLL_INTERVAL_MS = 3_000

/** 같은 계약이 권장하는 폴링 상한. 넘으면 멈추고 새로고침을 안내한다. */
const POLL_TIMEOUT_MS = 5 * 60 * 1_000

/** 화면 렌더에 필요한 전체 정보. 한 번만 읽고 자동 갱신하지 않는다. */
export function useCheckoutSession(sessionId: string | null) {
	return useQuery({
		queryKey: ['checkout', 'session', sessionId],
		queryFn: () => checkoutApi.getSession(sessionId!),
		enabled: sessionId !== null,
		// 주문 금액·견적은 세션 수명 동안 바뀌지 않는다. 상태 변화는 아래 폴링이 따로 본다.
		staleTime: Infinity,
		retry: (failureCount, error) => {
			// 404/410은 다시 시도해도 결과가 같다 — 즉시 해당 화면으로 보낸다.
			if (error instanceof CheckoutApiError && error.status >= 400 && error.status < 500) return false
			return failureCount < 2
		},
	})
}

/**
 * 결제 상태 폴링.
 *
 * **끝난 상태에서는 폴링을 멈춘다.** `refetchInterval`에 콜백을 주면 직전 응답을 보고
 * 다음 주기를 정할 수 있는데, 여기서 `false`를 돌려주면 폴링이 선다 — 성공·실패로
 * 끝난 결제를 3초마다 계속 두드리지 않기 위해서다.
 *
 * [startedAt]을 인자로 받는 이유는 상한(5분) 계산을 훅 안에서 하지 않기 위해서다.
 * 훅 안에서 `Date.now()`를 기준점으로 잡으면 리렌더마다 기준이 밀려 상한이 영영
 * 오지 않는다.
 */
export function useCheckoutStatus(sessionId: string | null, options: { enabled: boolean; startedAt: number }) {
	return useQuery({
		queryKey: ['checkout', 'status', sessionId],
		queryFn: () => checkoutApi.getStatus(sessionId!),
		enabled: options.enabled && sessionId !== null,
		refetchInterval: (query) => {
			const status = query.state.data
			if (status && isSettled(status)) return false
			if (Date.now() - options.startedAt > POLL_TIMEOUT_MS) return false
			return POLL_INTERVAL_MS
		},
		// 탭이 백그라운드일 때도 계속 본다 — 고객이 지갑 앱으로 전환했다가
		// 돌아오는 사이에 결제가 확정되는 것이 정상 경로다.
		refetchIntervalInBackground: true,
	})
}

/** 더 기다려도 바뀌지 않는 상태인가. */
export function isSettled(status: CheckoutStatus): boolean {
	return status.paymentStatus === 'SUCCEEDED' || status.paymentStatus === 'FAILED' || status.paymentStatus === 'EXPIRED'
}

/** 폴링 상한을 넘겼는가(응답이 아직 진행 중인데 시간만 지난 경우). */
export function isPollTimedOut(startedAt: number): boolean {
	return Date.now() - startedAt > POLL_TIMEOUT_MS
}

export { POLL_INTERVAL_MS, POLL_TIMEOUT_MS }
