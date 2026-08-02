import { useState } from 'react'
import { AdminApiError } from '@/api/client'
import { SETTLEMENT_RECEIVABLE_STATUSES, type SettlementFilters, type SettlementReceivableStatus } from '@/api/types'
import { SettlementTable } from '@/console/SettlementTable'
import { useSettlementReceivables } from '@/console/useSettlementReceivables'
import { useMerchants } from '@/console/useMerchants'
import { formatKrw } from '@/console/format'
import { Button } from '@/components/ui/button'
import { LiveStamp, PageHeader } from '@/components/console/PageHeader'
import { Panel } from '@/components/console/Panel'
import { StatStrip } from '@/components/console/StatStrip'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'

const PAGE_SIZE = 20

/**
 * 정산 채권 페이지.
 *
 * **기간 필터가 정산 예정일 기준이다** — 결제 내역이 생성 시각을 쓰는 것과 다르다. 정산에서
 * 묻는 질문은 "언제 만들어졌나"가 아니라 "언제 정산되나"이고, 날짜라 종료일 경계 문제도 없다.
 *
 * 화면 맨 위에 **필터 전체의 정산 예정 금액 합계**를 크게 보여준다 — 이 화면에서 사람이
 * 가장 먼저 묻는 것이 "그래서 얼마를 받나"인데, 목록만 있으면 답할 수 없다.
 */
export function SettlementPage() {
	const [filters, setFilters] = useState<SettlementFilters>({ page: 0, size: PAGE_SIZE })
	const settlements = useSettlementReceivables(filters)
	const merchants = useMerchants()

	// 필터를 바꾸면 첫 페이지로 돌아간다(결제 내역과 같은 이유).
	function updateFilter(patch: Partial<SettlementFilters>) {
		setFilters((previous) => ({ ...previous, ...patch, page: 0 }))
	}

	const page = filters.page ?? 0
	const totalCount = settlements.data?.totalCount ?? 0
	const lastPage = Math.max(0, Math.ceil(totalCount / PAGE_SIZE) - 1)

	return (
		<>
			<PageHeader
				title="정산"
				description="전 가맹점의 정산 예정 금액입니다. 기간은 정산 예정일 기준입니다."
				action={<LiveStamp at={new Date()} />}
			/>
		{/* **여기 두 값은 목록 API가 실제로 내려주는 것뿐이다**(`totalCount`,
		    `totalNetAmount`). 참고 디자인처럼 네 칸을 채우려면 서버가 합계를 더 줘야
		    하는데, 없는 값을 그럴듯하게 만들면 그 숫자를 아무도 설명할 수 없게 된다. */}
		<div className="pb-6">
			<StatStrip
				stats={[
					{ label: '건수', value: settlements.data ? `${totalCount.toLocaleString('ko-KR')}건` : '—' },
					{
						label: '정산 예정 금액 합계',
						value: settlements.data ? formatKrw(settlements.data.totalNetAmount) : '—',
					},
				]}
			/>
		</div>

		<div className="flex flex-col gap-6">
			<Panel
				title="정산 채권"
				meta={settlements.data ? `조회 결과 ${totalCount.toLocaleString('ko-KR')}건` : '불러오는 중…'}
			>
				<div className="flex flex-col gap-4">
					<div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
						<div className="flex flex-col gap-1.5">
							<Label htmlFor="settle-merchant">가맹점</Label>
							<select
								id="settle-merchant"
								className="h-9 rounded-lg border bg-card px-3 text-sm"
								value={filters.merchantId ?? ''}
								onChange={(event) => updateFilter({ merchantId: event.target.value })}
							>
								<option value="">전체</option>
								{merchants.data?.merchants.map((merchant) => (
									<option key={merchant.merchantId} value={merchant.merchantId}>
										{merchant.merchantName}
									</option>
								))}
							</select>
						</div>
						<div className="flex flex-col gap-1.5">
							<Label htmlFor="settle-status">상태</Label>
							<select
								id="settle-status"
								className="h-9 rounded-lg border bg-card px-3 text-sm"
								value={filters.status ?? ''}
								onChange={(event) =>
									updateFilter({ status: event.target.value as SettlementReceivableStatus | '' })
								}
							>
								<option value="">전체</option>
								{SETTLEMENT_RECEIVABLE_STATUSES.map((status) => (
									<option key={status} value={status}>
										{status}
									</option>
								))}
							</select>
						</div>
						<div className="flex flex-col gap-1.5">
							<Label htmlFor="settle-from">정산 예정일 시작</Label>
							<Input
								id="settle-from"
								type="date"
								value={filters.eligibleFrom ?? ''}
								onChange={(event) => updateFilter({ eligibleFrom: event.target.value || undefined })}
							/>
						</div>
						<div className="flex flex-col gap-1.5">
							<Label htmlFor="settle-to">정산 예정일 종료</Label>
							<Input
								id="settle-to"
								type="date"
								value={filters.eligibleTo ?? ''}
								onChange={(event) => updateFilter({ eligibleTo: event.target.value || undefined })}
							/>
						</div>
					</div>

					{settlements.isPending && <p className="text-sm text-muted-foreground">불러오는 중…</p>}
					{settlements.isError && (
						<p className="text-sm text-destructive">{listErrorMessage(settlements.error)}</p>
					)}
					{settlements.data && (
						<>
							<SettlementTable rows={settlements.data.settlementReceivables} />
							<div className="flex items-center justify-between text-sm">
								<span className="text-muted-foreground">
									전체 {totalCount.toLocaleString('ko-KR')}건 · {page + 1} / {lastPage + 1} 페이지
								</span>
								<div className="flex gap-2">
									<Button
										variant="outline"
										size="sm"
										disabled={page <= 0}
										onClick={() => setFilters((previous) => ({ ...previous, page: page - 1 }))}
									>
										이전
									</Button>
									<Button
										variant="outline"
										size="sm"
										disabled={page >= lastPage}
										onClick={() => setFilters((previous) => ({ ...previous, page: page + 1 }))}
									>
										다음
									</Button>
								</div>
							</div>
						</>
					)}
				</div>
			</Panel>
		</div>
		</>
	)
}


function listErrorMessage(error: unknown): string {
	if (error instanceof AdminApiError) return error.message
	return '정산 채권을 불러오지 못했습니다.'
}
