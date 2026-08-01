import { useQuery } from '@tanstack/react-query'
import { merchantApi } from '@/api/client'

/** `GET /merchant/payments/{paymentId}` 캐시 키. */
export const PAYMENT_DETAIL_QUERY_KEY = ['paymentDetail'] as const

export function usePaymentDetail(paymentId: string) {
	return useQuery({
		queryKey: [...PAYMENT_DETAIL_QUERY_KEY, paymentId],
		queryFn: () => merchantApi.getPaymentDetail(paymentId),
	})
}
