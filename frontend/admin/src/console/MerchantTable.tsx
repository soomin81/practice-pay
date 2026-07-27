import { Link } from 'react-router-dom'
import { formatDateTime } from '@/console/format'
import type { MerchantSummary } from '@/api/types'
import { Badge } from '@/components/ui/badge'

/**
 * 가맹점 목록. 인증된 내부 운영자 전원이 볼 수 있다(VIEWER 포함).
 *
 * 가맹점 이름은 상세 화면(`/merchants/{id}`)으로 가는 링크다 — 거기서 그 가맹점의 사용자
 * 명부와 계정 관리를 한다.
 */
export function MerchantTable({ merchants }: { merchants: readonly MerchantSummary[] }) {
	if (merchants.length === 0) {
		return <p className="text-sm text-muted-foreground">아직 등록된 가맹점이 없습니다.</p>
	}

	return (
		<div className="overflow-x-auto">
			<table className="w-full min-w-[36rem] border-collapse text-sm">
				<thead>
					<tr className="border-b text-left text-xs text-muted-foreground">
						<th className="py-2 pr-4 font-medium">가맹점 코드</th>
						<th className="py-2 pr-4 font-medium">이름</th>
						<th className="py-2 pr-4 font-medium">상태</th>
						<th className="py-2 font-medium">등록</th>
					</tr>
				</thead>
				<tbody>
					{merchants.map((merchant) => (
						<tr key={merchant.merchantId} className="border-b last:border-0">
							<td className="py-2.5 pr-4 font-mono text-xs">{merchant.merchantCode}</td>
							<td className="py-2.5 pr-4">
								<Link to={`/merchants/${merchant.merchantId}`} className="text-primary underline-offset-2 hover:underline">
									{merchant.merchantName}
								</Link>
							</td>
							<td className="py-2.5 pr-4">
								<StatusBadge status={String(merchant.status)} />
							</td>
							<td className="py-2.5 text-xs text-muted-foreground">{formatDateTime(merchant.createdAt)}</td>
						</tr>
					))}
				</tbody>
			</table>
		</div>
	)
}

function StatusBadge({ status }: { status: string }) {
	if (status === 'ACTIVE') return <Badge variant="secondary">ACTIVE</Badge>
	if (status === 'SUSPENDED' || status === 'TERMINATED') return <Badge variant="destructive">{status}</Badge>
	return <Badge variant="outline">{status}</Badge>
}
