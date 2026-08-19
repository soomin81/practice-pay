import { useState } from 'react'
import { adminApi, AdminApiError } from '@/api/client'
import type { RevealPaymentCustomerResponse } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * 구매자 원본 열람 액션(계약 4.8, ADR-008의 6).
 *
 * ## 사유를 먼저 받는다
 *
 * 버튼 한 번으로 원문이 나오지 않는다. 사유 입력을 거치게 한 것은 서버가 요구해서이기도
 * 하지만(빈 값이면 400), **"봤다"가 기록으로 남는다는 사실을 누르기 전에 알리기 위해서**다 —
 * 기록되는 줄 모르고 누른 사람에게 기록은 함정이 된다.
 *
 * ## 원문을 상태에 오래 두지 않는다
 *
 * 열람 결과는 이 컴포넌트의 상태로만 갖고, react-query 캐시에 넣지 않는다. "가리기"를 누르면
 * 즉시 버린다 — 화면을 떠나거나 목록을 다시 검색하면 어차피 사라진다.
 *
 * 다시 보려면 다시 열람해야 하고, **그때 기록이 한 번 더 남는다.** 그게 맞다: 두 번 봤으면
 * 두 번 남아야 한다.
 *
 * ## 폭을 고정한다(`w-56`)
 *
 * 표의 한 칸 안에서 열리므로, 폭을 두지 않으면 입력 길이에 따라 그 열이 계속 넓어져 다른
 * 열들이 밀린다. **표 자체의 가로 스크롤은 결함이 아니다** — `DataTable`이 `overflow-x-auto`로
 * 설계돼 있고 정산 표는 9열이라 평소에도 스크롤된다.
 */
export function RevealCustomerAction({ paymentId }: { paymentId: string }) {
	const [asking, setAsking] = useState(false)
	const [reason, setReason] = useState('')
	const [revealed, setRevealed] = useState<RevealPaymentCustomerResponse | null>(null)
	const [error, setError] = useState<string | null>(null)
	const [revealing, setRevealing] = useState(false)

	function reset() {
		setAsking(false)
		setReason('')
		setRevealed(null)
		setError(null)
	}

	async function reveal(event: React.FormEvent) {
		event.preventDefault()
		setRevealing(true)
		setError(null)
		try {
			setRevealed(await adminApi.revealPaymentCustomer(paymentId, reason.trim()))
			setAsking(false)
		} catch (cause) {
			setError(revealErrorMessage(cause))
		} finally {
			setRevealing(false)
		}
	}

	if (revealed) {
		return (
			<div className="flex w-56 flex-col gap-1 rounded-lg border border-destructive/40 bg-destructive/5 p-2">
				<span className="text-xs font-medium text-destructive">열람 기록이 남았습니다</span>
				<span className="text-sm">{revealed.name}</span>
				<span className="text-sm">{revealed.email}</span>
				<span className="text-sm">{revealed.phone}</span>
				<Button variant="ghost" size="sm" onClick={reset}>
					가리기
				</Button>
			</div>
		)
	}

	if (asking) {
		return (
			<form className="flex w-56 flex-col gap-1.5" onSubmit={reveal}>
				<Label htmlFor={`reveal-reason-${paymentId}`} className="text-xs">
					열람 사유(기록에 남습니다)
				</Label>
				<Input
					id={`reveal-reason-${paymentId}`}
					name="reason"
					required
					value={reason}
					onChange={(event) => setReason(event.target.value)}
					placeholder="예: 결제 실패 문의 대응"
				/>
				{error && <p className="text-xs text-destructive">{error}</p>}
				<div className="flex gap-1.5">
					<Button type="submit" size="sm" disabled={revealing}>
						{revealing ? '여는 중…' : '원본 보기'}
					</Button>
					<Button type="button" variant="ghost" size="sm" onClick={reset}>
						취소
					</Button>
				</div>
			</form>
		)
	}

	return (
		<Button variant="outline" size="sm" onClick={() => setAsking(true)}>
			원본 보기
		</Button>
	)
}

function revealErrorMessage(error: unknown): string {
	if (error instanceof AdminApiError) {
		if (error.isForbidden) return '원본을 볼 권한이 없습니다(SUPER_ADMIN만 가능).'
		if (error.isNotFound) return '이 결제에는 구매자 정보가 없습니다.'
		return error.message
	}
	return '원본을 불러오지 못했습니다.'
}
