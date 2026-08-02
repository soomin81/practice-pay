import { useMutation } from '@tanstack/react-query'
import { adminApi } from '@/api/client'
import { saveFile } from '@/api/http'
import type { SettlementFilters } from '@/api/types'

/**
 * 정산 채권 `.xlsx` 다운로드. **쿼리가 아니라 뮤테이션으로 다룬다** — 캐시할 이유가 없고
 * (같은 조건이어도 매번 새로 받는 것이 맞다) 버튼을 눌렀을 때만 일어나야 하기 때문이다
 * (`usePaymentExport`와 같은 판단).
 *
 * 저장까지 여기서 끝내고, 화면에는 **잘렸는지**만 돌려준다 — 조용히 일부만 담긴 파일을
 * 받아가는 것이 이 기능에서 가장 위험한 실패라 호출부가 반드시 안내해야 한다.
 */
export function useSettlementExport() {
	return useMutation({
		mutationFn: async (filters: SettlementFilters) => {
			const file = await adminApi.exportSettlementReceivables(filters)
			saveFile(file, '정산채권.xlsx')
			return file.truncated
		},
	})
}
