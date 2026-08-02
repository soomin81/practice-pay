import { useState } from 'react'
import { AdminApiError } from '@/api/client'
import { PAYMENT_STATUSES, type ListPaymentsResponse, type PaymentListFilters, type PaymentStatus } from '@/api/types'
import { PaymentTable } from '@/console/PaymentTable'
import { usePayments } from '@/console/usePayments'
import { exportErrorMessage, usePaymentExport } from '@/console/usePaymentExport'
import { useMerchants } from '@/console/useMerchants'
import { formatKrw } from '@/console/format'
import { Button } from '@/components/ui/button'
import { Download } from 'lucide-react'
import { LiveStamp, PageHeader } from '@/components/console/PageHeader'
import { Panel } from '@/components/console/Panel'
import { labelFor } from '@/components/console/statusLabel'
import { StatStrip, type Stat } from '@/components/console/StatStrip'
import { FilterChips } from '@/components/console/FilterChips'
import { RANGE_OPTIONS, rangeFilters, type RangePreset } from '@/console/dateRange'
import { Label } from '@/components/ui/label'
import { Input } from '@/components/ui/input'

const PAGE_SIZE = 20

/**
 * 결제 내역 페이지(전 가맹점). **인증된 내부 사용자 전원**이 볼 수 있다 — 서버도
 * `/admin/payments`를 역할로 좁히지 않는다(`docs/architecture/admin-console-api.md`의 4.1).
 *
 * 가맹점 선택지는 이미 캐시된 가맹점 목록(`useMerchants`)에서 가져온다 — 이 화면 때문에
 * 별도 조회를 추가하지 않는다(`MerchantDetailPage`가 세운 선례).
 */
export function PaymentsPage() {
	const [filters, setFilters] = useState<PaymentListFilters>({ page: 0, size: PAGE_SIZE })
	const [preset, setPreset] = useState<RangePreset | null>(null)
	const payments = usePayments(filters)
	const exportPayments = usePaymentExport()
	const merchants = useMerchants()

	// 필터를 바꾸면 항상 첫 페이지로 돌아간다 — 3페이지를 보다가 좁히면 결과가 없는데
	// "결제가 없다"로 보이는 흔한 혼란을 막는다.
	function updateFilter(patch: Partial<PaymentListFilters>) {
		setFilters((previous) => ({ ...previous, ...patch, page: 0 }))
	}

	const page = filters.page ?? 0
	const totalCount = payments.data?.totalCount ?? 0
	const lastPage = Math.max(0, Math.ceil(totalCount / PAGE_SIZE) - 1)

	return (
		<>
			<PageHeader
				title="결제 내역"
				description="전 가맹점의 결제를 생성 시각 최신순으로 조회합니다."
				action={<LiveStamp at={new Date()} />}
			/>
		<div className="pb-6">
			<StatStrip stats={paymentStats(payments.data)} />
		</div>

		<div className="flex flex-col gap-6">
			<Panel
				title="결제 내역"
				meta={payments.data ? `조회 결과 ${totalCount.toLocaleString('ko-KR')}건` : '불러오는 중…'}
				action={
					<Button
						variant="outline"
						size="sm"
						disabled={exportPayments.isPending}
						onClick={() => exportPayments.mutate(filters)}
					>
						<Download className="size-4" />
						{exportPayments.isPending ? '만드는 중…' : '엑셀 다운로드'}
					</Button>
				}
			>
				<div className="flex flex-col gap-4">
					{exportPayments.isError && (
						<p className="text-sm text-destructive">{exportErrorMessage(exportPayments.error)}</p>
					)}
					{/* 잘린 파일을 그냥 받아가지 않도록 반드시 알린다. */}
					{exportPayments.data === true && (
						<p className="text-sm text-destructive">
							결과가 너무 많아 최대 10,000건까지만 담았습니다. 기간이나 조건을 좁혀 다시 받으세요.
						</p>
					)}
					{/* 칩은 날짜 입력을 **대신하지 않고 함께 바꾼다** — 지금 무엇이 걸려 있는지
					    두 곳에서 같은 답이 나와야 한다(가맹점 콘솔과 같은 규칙). */}
					<FilterChips
						ariaLabel="기간 빠른 선택"
						options={RANGE_OPTIONS}
						value={preset}
						onChange={(next) => {
							setPreset(next)
							updateFilter(rangeFilters(next))
						}}
					/>
					<div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
						<div className="flex flex-col gap-1.5">
							<Label htmlFor="filter-merchant">가맹점</Label>
							<select
								id="filter-merchant"
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
							<Label htmlFor="filter-status">상태</Label>
							<select
								id="filter-status"
								className="h-9 rounded-lg border bg-card px-3 text-sm"
								value={filters.status ?? ''}
								onChange={(event) => updateFilter({ status: event.target.value as PaymentStatus | '' })}
							>
								<option value="">전체</option>
								{PAYMENT_STATUSES.map((status) => (
									<option key={status} value={status}>
										{/* value는 코드 그대로다 — 쿼리스트링과 API 계약은 바뀌지 않는다. */}
										{labelFor('payment', status)}
									</option>
								))}
							</select>
						</div>
						<div className="flex flex-col gap-1.5">
							<Label htmlFor="filter-from">시작일</Label>
							<Input
								id="filter-from"
								type="date"
								className="bg-card"
								value={toDateInput(filters.from)}
								onChange={(event) => {
									setPreset(null)
									updateFilter({ from: startOfDayIso(event.target.value) })
								}}
							/>
						</div>
						<div className="flex flex-col gap-1.5">
							<Label htmlFor="filter-to">종료일</Label>
							<Input
								id="filter-to"
								type="date"
								className="bg-card"
								value={toDateInput(filters.to)}
								onChange={(event) => {
									setPreset(null)
									updateFilter({ to: endOfDayIso(event.target.value) })
								}}
							/>
						</div>
					</div>

					{payments.isPending && <p className="text-sm text-muted-foreground">불러오는 중…</p>}
					{payments.isError && <p className="text-sm text-destructive">{listErrorMessage(payments.error)}</p>}
					{payments.data && (
						<>
							<PaymentTable payments={payments.data.payments} />
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


/**
 * `<input type="date">`는 `YYYY-MM-DD`만 다루는데 서버는 ISO-8601 순간을 받는다.
 * **종료일은 그날 23:59:59.999로 늘린다** — 그러지 않으면 "7월 31일까지"가 그날 00:00까지가
 * 되어 마지막 날 결제가 통째로 빠진다(기간 필터에서 가장 흔한 실수다).
 */
function startOfDayIso(date: string): string | undefined {
	return date ? new Date(`${date}T00:00:00Z`).toISOString() : undefined
}

function endOfDayIso(date: string): string | undefined {
	return date ? new Date(`${date}T23:59:59.999Z`).toISOString() : undefined
}

function toDateInput(iso: string | undefined): string {
	return iso ? iso.slice(0, 10) : ''
}

function listErrorMessage(error: unknown): string {
	if (error instanceof AdminApiError) return error.message
	return '결제 내역을 불러오지 못했습니다.'
}

/**
 * 화면 위쪽의 통계 네 칸(가맹점 콘솔과 같은 규칙). **전부 필터 전체에 대한 값**이고
 * 현재 페이지와 무관하다.
 *
 * **분모가 0이면 승인율은 "—"다** — `0/0`을 0%로 그리면 "결제가 없다"와 "전부 실패했다"가
 * 화면에서 같아진다.
 */
function paymentStats(data: ListPaymentsResponse | undefined): Stat[] {
	if (!data) {
		return [
			{ label: '거래건수', value: '—' },
			{ label: '승인 건수', value: '—' },
			{ label: '승인금액', value: '—' },
			{ label: '승인율', value: '—' },
		]
	}
	return [
		{ label: '거래건수', value: `${data.totalCount.toLocaleString('ko-KR')}건` },
		{ label: '승인 건수', value: `${data.succeededCount.toLocaleString('ko-KR')}건` },
		{ label: '승인금액', value: formatKrw(data.succeededAmount) },
		{
			label: '승인율',
			value: data.totalCount === 0 ? '—' : `${((data.succeededCount / data.totalCount) * 100).toFixed(1)}%`,
		},
	]
}
