import { useMutation } from '@tanstack/react-query'
import { adminApi } from '@/api/client'
import type { AcceptInvitationRequest } from '@/api/types'

/**
 * 내부 운영자 초대 수락(계정 활성화).
 *
 * 이 요청만 **비인증 + CSRF 예외**다 — 자격증명이 세션 쿠키가 아니라 본문의 초대 Token
 * 자체이기 때문이다. 성공해도 로그인 상태가 되는 것은 아니므로(세션이 만들어지지 않는다)
 * `me` 쿼리를 무효화하지 않고 화면이 로그인으로 안내한다.
 */
export function useAcceptInvitation() {
	return useMutation({
		mutationFn: (body: AcceptInvitationRequest) => adminApi.acceptInvitation(body),
	})
}
