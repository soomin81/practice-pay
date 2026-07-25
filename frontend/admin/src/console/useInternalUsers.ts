import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/client'
import type { IssueInternalUserRequest } from '@/api/types'

/** `GET /admin/internal-users` 캐시 키. 발급이 이 키를 무효화한다. */
export const INTERNAL_USERS_QUERY_KEY = ['internalUsers'] as const

export function useInternalUsers() {
	return useQuery({ queryKey: INTERNAL_USERS_QUERY_KEY, queryFn: adminApi.listInternalUsers })
}

export function useIssueInternalUser() {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: (body: IssueInternalUserRequest) => adminApi.issueInternalUser(body),
		// 새로 초대된 계정이 INVITED로 명부에 나타나야 한다.
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: INTERNAL_USERS_QUERY_KEY })
		},
	})
}
