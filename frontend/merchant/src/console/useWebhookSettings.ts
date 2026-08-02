import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { merchantApi } from '@/api/client'
import type { MerchantWebhookSettingsResponse } from '@/api/types'

/** `GET /merchant/webhook` 캐시 키. URL 변경과 비밀 교체가 이 키를 갱신한다. */
export const WEBHOOK_SETTINGS_QUERY_KEY = ['webhookSettings'] as const

export function useWebhookSettings() {
	return useQuery({ queryKey: WEBHOOK_SETTINGS_QUERY_KEY, queryFn: merchantApi.getWebhookSettings })
}

export function useUpdateWebhookUrl() {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: (webhookUrl: string | null) => merchantApi.updateWebhookUrl({ webhookUrl }),
		onSuccess: (settings: MerchantWebhookSettingsResponse) => {
			// 서버가 갱신된 설정을 그대로 돌려주므로 다시 불러오지 않고 캐시에 넣는다.
			queryClient.setQueryData(WEBHOOK_SETTINGS_QUERY_KEY, settings)
		},
	})
}

export function useRotateWebhookSecret() {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: () => merchantApi.rotateWebhookSecret(),
		onSuccess: (settings: MerchantWebhookSettingsResponse) => {
			queryClient.setQueryData(WEBHOOK_SETTINGS_QUERY_KEY, settings)
		},
	})
}
