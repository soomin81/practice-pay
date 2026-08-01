import { useQuery } from '@tanstack/react-query'
import { adminApi } from '@/api/client'

/** `GET /admin/payments/{paymentId}` 캐시 키. */
export const PAYMENT_DETAIL_QUERY_KEY = ['paymentDetail'] as const

export function usePaymentDetail(paymentId: string) {
	return useQuery({
		queryKey: [...PAYMENT_DETAIL_QUERY_KEY, paymentId],
		queryFn: () => adminApi.getPaymentDetail(paymentId),
	})
}
