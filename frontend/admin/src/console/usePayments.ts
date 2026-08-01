import { useQuery } from '@tanstack/react-query'
import { adminApi } from '@/api/client'
import type { PaymentListFilters } from '@/api/types'

/** `GET /admin/payments` 캐시 키. 필터가 바뀌면 다른 조회이므로 키에 포함한다. */
export const PAYMENTS_QUERY_KEY = ['payments'] as const

export function usePayments(filters: PaymentListFilters) {
	return useQuery({
		queryKey: [...PAYMENTS_QUERY_KEY, filters],
		queryFn: () => adminApi.listPayments(filters),
		// 페이지를 넘길 때 목록이 빈 화면으로 깜빡이지 않게 이전 결과를 유지한다.
		placeholderData: (previous) => previous,
	})
}
