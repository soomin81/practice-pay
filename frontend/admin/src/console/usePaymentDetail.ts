import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/client'

/** `GET /admin/payments/{paymentId}` 캐시 키. */
export const PAYMENT_DETAIL_QUERY_KEY = ['paymentDetail'] as const

export function usePaymentDetail(paymentId: string) {
	return useQuery({
		queryKey: [...PAYMENT_DETAIL_QUERY_KEY, paymentId],
		queryFn: () => adminApi.getPaymentDetail(paymentId),
	})
}

/**
 * 실패한 Webhook 전송을 다시 보내도록 예약한다.
 *
 * 성공하면 상세를 다시 불러온다 — 그 전송의 상태가 `FAILED`에서 `PENDING`으로 바뀐 것을
 * 화면에서 확인할 수 있어야 한다. **다만 그것이 "전달됐다"는 뜻은 아니다**: 실제 발송은
 * 발행 Worker가 하므로, 최종 결과를 보려면 잠시 뒤 다시 봐야 한다.
 */
/**
 * 확정된 입금이 체인 재구성으로 사라졌다고 표시한다.
 *
 * 성공하면 상세를 다시 불러온다 — 온체인 거래가 `REORGED`로, 정산이 `HELD`로 바뀐 것을
 * 화면에서 확인할 수 있어야 한다. **결제는 여전히 "결제 완료"로 남는다**(의도된 동작).
 */
export function useMarkTransactionReorged(paymentId: string) {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: (blockchainTransactionId: string) => adminApi.markTransactionReorged(blockchainTransactionId),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: [...PAYMENT_DETAIL_QUERY_KEY, paymentId] })
		},
	})
}

export function useRedeliverWebhook(paymentId: string) {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: (webhookDeliveryId: string) => adminApi.redeliverWebhook(webhookDeliveryId),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: [...PAYMENT_DETAIL_QUERY_KEY, paymentId] })
		},
	})
}
