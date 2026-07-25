import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/client'
import type { RegisterMerchantRequest } from '@/api/types'

/** `GET /admin/merchants` 캐시 키. 등록이 이 키를 무효화한다. */
export const MERCHANTS_QUERY_KEY = ['merchants'] as const

export function useMerchants() {
	return useQuery({ queryKey: MERCHANTS_QUERY_KEY, queryFn: adminApi.listMerchants })
}

export function useRegisterMerchant() {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: (body: RegisterMerchantRequest) => adminApi.registerMerchant(body),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: MERCHANTS_QUERY_KEY })
		},
	})
}
