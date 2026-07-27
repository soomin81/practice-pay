import { useQuery } from '@tanstack/react-query'
import { adminApi } from '@/api/client'

/** `GET /admin/login-audit` 캐시 키. */
export const LOGIN_AUDIT_QUERY_KEY = ['loginAudit'] as const

export function useLoginAudit() {
	return useQuery({ queryKey: LOGIN_AUDIT_QUERY_KEY, queryFn: adminApi.listLoginAudit })
}
