import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/client'
import type { MerchantUserRole, MerchantUserStatusAction } from '@/api/types'

/** `GET /admin/merchants/{merchantId}/users` 캐시 키. 상태·역할 변경이 이 키를 무효화한다. */
export function merchantUsersQueryKey(merchantId: string) {
	return ['merchantUsers', merchantId] as const
}

export function useMerchantUsers(merchantId: string) {
	return useQuery({
		queryKey: merchantUsersQueryKey(merchantId),
		queryFn: () => adminApi.listMerchantUsers(merchantId),
	})
}

export function useChangeMerchantUserStatus(merchantId: string) {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ merchantUserId, action }: { merchantUserId: string; action: MerchantUserStatusAction }) =>
			adminApi.changeMerchantUserStatus(merchantId, merchantUserId, action),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: merchantUsersQueryKey(merchantId) })
		},
	})
}

export function useChangeMerchantUserRole(merchantId: string) {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ merchantUserId, role }: { merchantUserId: string; role: MerchantUserRole }) =>
			adminApi.changeMerchantUserRole(merchantId, merchantUserId, role),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: merchantUsersQueryKey(merchantId) })
		},
	})
}
