import { useMutation } from '@tanstack/react-query'
import { adminApi, AdminApiError } from '@/api/client'
import { saveFile } from '@/api/http'
import type { PaymentListFilters } from '@/api/types'

/**
 * 결제 내역 `.xlsx` 다운로드. **쿼리가 아니라 뮤테이션으로 다룬다** — 캐시할 이유가 없고
 * (같은 조건이어도 매번 새로 받는 것이 맞다) 버튼을 눌렀을 때만 일어나야 하기 때문이다.
 *
 * 저장까지 여기서 끝내고, 화면에는 **잘렸는지**만 돌려준다 — 조용히 일부만 담긴 파일을
 * 받아가는 것이 이 기능에서 가장 위험한 실패라 호출부가 반드시 안내해야 한다.
 */
export function usePaymentExport() {
	return useMutation({
		mutationFn: async (filters: PaymentListFilters) => {
			const file = await adminApi.exportPayments(filters)
			saveFile(file, '결제내역.xlsx')
			return file.truncated
		},
	})
}

/** 다운로드 실패 문구. 권한·인증 오류는 서버 메시지가 이미 구체적이라 그대로 쓴다. */
export function exportErrorMessage(error: unknown): string {
	return error instanceof AdminApiError ? error.message : '엑셀 파일을 만들지 못했습니다.'
}
