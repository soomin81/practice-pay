import { useQuery } from '@tanstack/react-query'
import { merchantApi } from '@/api/client'
import type { SettlementFilters } from '@/api/types'

/** `GET .../settlement-receivables` 캐시 키. 필터가 바뀌면 다른 조회다. */
export const SETTLEMENT_QUERY_KEY = ['settlementReceivables'] as const

export function useSettlementReceivables(filters: SettlementFilters) {
	return useQuery({
		queryKey: [...SETTLEMENT_QUERY_KEY, filters],
		queryFn: () => merchantApi.listSettlementReceivables(filters),
		// 페이지를 넘길 때 목록이 빈 화면으로 깜빡이지 않게 이전 결과를 유지한다.
		placeholderData: (previous) => previous,
	})
}
