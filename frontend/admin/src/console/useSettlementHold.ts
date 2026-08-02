import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi, AdminApiError } from '@/api/client'
import { SETTLEMENT_QUERY_KEY } from '@/console/useSettlementReceivables'

/** `GET .../{id}/hold-history` 캐시 키. 채권마다 다른 조회다. */
export const SETTLEMENT_HOLD_HISTORY_QUERY_KEY = ['settlementHoldHistory'] as const

/**
 * 채권 한 건의 보류·해제·취소 이력.
 *
 * **`enabled`로 열었을 때만 부른다** — 목록의 모든 행이 각자 이력을 미리 당겨오면 한 화면에
 * 수십 번의 요청이 나간다. 이력을 보는 시점은 "이 채권을 풀어도 되나"를 판단할 때뿐이다.
 */
export function useSettlementHoldHistory(settlementReceivableId: string, enabled: boolean) {
	return useQuery({
		queryKey: [...SETTLEMENT_HOLD_HISTORY_QUERY_KEY, settlementReceivableId],
		queryFn: () => adminApi.settlementHoldHistory(settlementReceivableId),
		enabled,
	})
}

/**
 * 보류를 푼다.
 *
 * 성공하면 **목록과 그 채권의 이력을 함께** 다시 불러온다 — 상태가 바뀐 것과 "누가 왜
 * 풀었는지"가 한 줄 늘어난 것을 같은 화면에서 확인할 수 있어야 한다.
 */
export function useReleaseSettlementHold(settlementReceivableId: string) {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: (note: string) => adminApi.releaseSettlementHold(settlementReceivableId, note),
		onSuccess: () => invalidate(queryClient, settlementReceivableId),
	})
}

/** 채권을 취소한다. **`CANCELLED`는 종료 상태라 되돌릴 수 없다.** */
export function useCancelSettlementReceivable(settlementReceivableId: string) {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: (note: string) => adminApi.cancelSettlementReceivable(settlementReceivableId, note),
		onSuccess: () => invalidate(queryClient, settlementReceivableId),
	})
}

function invalidate(queryClient: ReturnType<typeof useQueryClient>, settlementReceivableId: string) {
	void queryClient.invalidateQueries({ queryKey: SETTLEMENT_QUERY_KEY })
	void queryClient.invalidateQueries({
		queryKey: [...SETTLEMENT_HOLD_HISTORY_QUERY_KEY, settlementReceivableId],
	})
}

/**
 * `409`(보류가 아님·이미 취소됨)와 `400`(사유 누락)은 **왜 안 되는지**를 담고 있다 —
 * 그대로 보여주지 않으면 같은 버튼을 계속 누른다.
 */
export function settlementHoldErrorMessage(error: unknown): string {
	return error instanceof AdminApiError ? error.message : '요청을 처리하지 못했습니다.'
}
