import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { merchantApi } from '@/api/client'
import type { IssueApiKeyRequest } from '@/api/types'

/** `GET /merchant/api-keys` 캐시 키. 발급·폐기가 이 키를 무효화한다. */
export const API_KEYS_QUERY_KEY = ['apiKeys'] as const

export function useApiKeys() {
	return useQuery({ queryKey: API_KEYS_QUERY_KEY, queryFn: merchantApi.listApiKeys })
}

export function useIssueApiKey() {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: (body: IssueApiKeyRequest) => merchantApi.issueApiKey(body),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: API_KEYS_QUERY_KEY })
		},
	})
}

export function useRevokeApiKey() {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: (merchantApiKeyId: string) => merchantApi.revokeApiKey(merchantApiKeyId),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: API_KEYS_QUERY_KEY })
		},
	})
}
