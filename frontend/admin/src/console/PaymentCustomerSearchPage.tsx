import { useState } from 'react'
import { adminApi, AdminApiError } from '@/api/client'
import {
	canRevealPaymentCustomer,
	type MeResponse,
	type PaymentCustomerMatch,
	type PaymentCustomerSearchField,
} from '@/api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PageHeader } from '@/components/console/PageHeader'
import { Panel } from '@/components/console/Panel'
import { PaymentCustomerTable } from '@/console/PaymentCustomerTable'

/**
 * 구매자 정보 검색 페이지(계약 4.7/4.8, ADR-008).
 *
 * ## 검색 결과를 react-query 캐시에 넣지 않는다
 *
 * 다른 목록 화면과 다르게 `useQuery`를 쓰지 않고 상태로 들고 있는다. 검색어가 **개인정보
 * 자체**라 캐시 키에 이메일·전화번호가 박히고, 화면을 떠난 뒤에도 그 키와 결과가 메모리에
 * 남기 때문이다. 목록을 다시 보려면 다시 검색하는 편이 맞다.
 *
 * ## 조건은 하나만 보낸다
 *
 * 서버가 둘 다 오면 400을 내므로(AND 조합 탐색 차단) 화면도 **라디오로 하나만** 고르게
 * 한다. 두 칸을 나란히 두고 하나만 채우게 하면 실수로 둘 다 채운 사용자가 400을 만난다.
 */
export function PaymentCustomerSearchPage({ me }: { me: MeResponse }) {
	const [field, setField] = useState<PaymentCustomerSearchField>('email')
	const [value, setValue] = useState('')
	const [matches, setMatches] = useState<PaymentCustomerMatch[] | null>(null)
	const [error, setError] = useState<string | null>(null)
	const [searching, setSearching] = useState(false)

	async function search(event: React.FormEvent) {
		event.preventDefault()
		setSearching(true)
		setError(null)
		try {
			const result = await adminApi.searchPaymentCustomers(field, value.trim())
			setMatches(result.matches)
		} catch (cause) {
			setMatches(null)
			setError(searchErrorMessage(cause))
		} finally {
			setSearching(false)
		}
	}

	return (
		<>
			<PageHeader
				title="구매자 조회"
				description="이메일 또는 휴대전화로 결제를 찾습니다. 목록에는 가려진 값만 표시됩니다."
			/>

			<div className="flex flex-col gap-6">
				<Panel
					title={<>구매자 검색</>}
					meta={
						<>
							암호화되어 저장되므로 <strong>정확히 일치</strong>해야 찾을 수 있습니다 — 일부만 입력하거나
							이름으로는 검색할 수 없습니다.
						</>
					}
				>
					<form className="flex flex-col gap-3" onSubmit={search}>
						<fieldset className="flex gap-4">
							<legend className="sr-only">검색 기준</legend>
							{(['email', 'phone'] as const).map((option) => (
								<label key={option} className="flex items-center gap-1.5 text-sm">
									<input
										type="radio"
										name="searchField"
										value={option}
										checked={field === option}
										onChange={() => setField(option)}
									/>
									{option === 'email' ? '이메일로 검색' : '휴대전화로 검색'}
								</label>
							))}
						</fieldset>

						<div className="flex items-end gap-2">
							<div className="flex-1 space-y-1.5">
								<Label htmlFor="customer-search-value">
									{field === 'email' ? '이메일' : '휴대전화'}
								</Label>
								<Input
									id="customer-search-value"
									name="value"
									type={field === 'email' ? 'email' : 'tel'}
									required
									value={value}
									onChange={(event) => setValue(event.target.value)}
									placeholder={field === 'email' ? 'gildong@example.com' : '010-1234-5678'}
								/>
							</div>
							<Button type="submit" disabled={searching}>
								{searching ? '찾는 중…' : '검색'}
							</Button>
						</div>

						{error && <p className="text-sm text-destructive">{error}</p>}
					</form>
				</Panel>

				{matches !== null && (
					<Panel
						title={<>검색 결과</>}
						meta={
							matches.length === 0 ? (
								<>일치하는 결제가 없습니다.</>
							) : (
								<>{matches.length}건. 원본 열람은 SUPER_ADMIN만 가능하며 열람 기록이 남습니다.</>
							)
						}
					>
						{matches.length === 0 ? (
							<p className="text-sm text-muted-foreground">
								입력한 값과 정확히 일치하는 구매자가 없습니다. 오타가 없는지 확인해 주세요.
							</p>
						) : (
							<PaymentCustomerTable
								matches={matches}
								canReveal={canRevealPaymentCustomer(String(me.role))}
							/>
						)}
					</Panel>
				)}
			</div>
		</>
	)
}

function searchErrorMessage(error: unknown): string {
	if (error instanceof AdminApiError) {
		if (error.isForbidden) return '구매자를 조회할 권한이 없습니다(SUPER_ADMIN/OPERATOR만 가능).'
		// 형식 오류·조건 오류는 서버가 이유를 한글로 준다 — 그대로 보여준다.
		return error.message
	}
	return '구매자를 조회하지 못했습니다.'
}
