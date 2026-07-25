import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '@/api/client'
import type { LoginRequest } from '@/api/types'

/** `GET /admin/me` 캐시 키. 로그인/로그아웃이 이 키를 무효화·갱신한다. */
export const ME_QUERY_KEY = ['me'] as const

/**
 * 현재 세션 사용자를 조회한다. `data`가 `null`이면 로그아웃 상태다(client가 401을
 * `null`로 바꿔 준다). 앱 부팅 시 실행돼 CSRF 토큰 쿠키 발급도 겸한다.
 */
export function useMe() {
	return useQuery({ queryKey: ME_QUERY_KEY, queryFn: adminApi.me })
}

export function useLogin() {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: (body: LoginRequest) => adminApi.login(body),
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY })
		},
	})
}

export function useLogout() {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: () => adminApi.logout(),
		onSuccess: () => {
			queryClient.setQueryData(ME_QUERY_KEY, null)
		},
	})
}
