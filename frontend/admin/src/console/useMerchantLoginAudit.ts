import { useQuery } from '@tanstack/react-query'
import { adminApi } from '@/api/client'

/** `GET /admin/merchant-login-audit` 캐시 키. */
export const MERCHANT_LOGIN_AUDIT_QUERY_KEY = ['merchantLoginAudit'] as const

export function useMerchantLoginAudit() {
	return useQuery({ queryKey: MERCHANT_LOGIN_AUDIT_QUERY_KEY, queryFn: adminApi.listMerchantLoginAudit })
}
