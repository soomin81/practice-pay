import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { merchantApi } from '@/api/client'
import type { LoginRequest } from '@/api/types'

/** `GET /merchant/me` 캐시 키. 로그인/로그아웃이 이 키를 무효화·갱신한다. */
export const ME_QUERY_KEY = ['me'] as const

/**
 * 현재 세션 사용자를 조회한다. `data`가 `null`이면 로그아웃 상태다(client가 401을
 * `null`로 바꿔 준다). 이 쿼리는 앱 부팅 시 실행돼 CSRF 토큰 쿠키 발급도 겸한다.
 */
export function useMe() {
	return useQuery({ queryKey: ME_QUERY_KEY, queryFn: merchantApi.me })
}

export function useLogin() {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: (body: LoginRequest) => merchantApi.login(body),
		// 로그인 성공 후 me를 다시 읽어 콘솔로 전환한다.
		onSuccess: () => {
			void queryClient.invalidateQueries({ queryKey: ME_QUERY_KEY })
		},
	})
}

export function useLogout() {
	const queryClient = useQueryClient()
	return useMutation({
		mutationFn: () => merchantApi.logout(),
		// 세션이 사라졌으니 me를 곧바로 null로 바꿔 로그인 화면으로 넘긴다.
		onSuccess: () => {
			queryClient.setQueryData(ME_QUERY_KEY, null)
		},
	})
}
