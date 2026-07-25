import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { merchantApi } from '@/api/client'
import type { InviteSubAccountRequest, MerchantUserRole, MerchantUserStatusAction } from '@/api/types'

/** `GET /merchant/merchant-users` 캐시 키. 초대 발급이 이 키를 무효화한다. */
export const MERCHANT_USERS_QUERY_KEY = ['merchantUsers'] as const

export function useMerchantUsers() {
	return useQuery({ queryKey: MERCHANT_USERS_QUERY_KEY, queryFn: merchantApi.listMerchantUsers })
}

export function useInviteSubAccount() {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: (body: InviteSubAccountRequest) => merchantApi.inviteSubAccount(body),
		// 새로 초대된 계정이 INVITED로 명부에 나타나야 한다.
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: MERCHANT_USERS_QUERY_KEY })
		},
	})
}

export function useChangeMerchantUserStatus() {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ merchantUserId, action }: { merchantUserId: string; action: MerchantUserStatusAction }) =>
			merchantApi.changeMerchantUserStatus(merchantUserId, action),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: MERCHANT_USERS_QUERY_KEY })
		},
	})
}

export function useChangeMerchantUserRole() {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: ({ merchantUserId, role }: { merchantUserId: string; role: MerchantUserRole }) =>
			merchantApi.changeMerchantUserRole(merchantUserId, role),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: MERCHANT_USERS_QUERY_KEY })
		},
	})
}
