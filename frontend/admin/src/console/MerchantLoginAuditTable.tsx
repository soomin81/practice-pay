import { formatDateTime } from '@/console/format'
import type { MerchantLoginAuditEntry } from '@/api/types'
import { Badge } from '@/components/ui/badge'

/**
 * 가맹점 로그인 감사 로그 표(전 가맹점, 최신순). 없는 merchantCode로의 시도는 `merchantName`이
 * 없어 "알 수 없는 가맹점"으로, 없는 loginId 시도는 `userName`이 없어 대시로 구분해 보여준다.
 */
export function MerchantLoginAuditTable({ entries }: { entries: readonly MerchantLoginAuditEntry[] }) {
	if (entries.length === 0) {
		return <p className="text-sm text-muted-foreground">아직 가맹점 로그인 기록이 없습니다.</p>
	}

	return (
		<div className="overflow-x-auto">
			<table className="w-full min-w-[52rem] border-collapse text-sm">
				<thead>
					<tr className="border-b text-left text-xs text-muted-foreground">
						<th className="py-2 pr-4 font-medium">시각</th>
						<th className="py-2 pr-4 font-medium">가맹점</th>
						<th className="py-2 pr-4 font-medium">로그인 아이디</th>
						<th className="py-2 pr-4 font-medium">이름</th>
						<th className="py-2 pr-4 font-medium">결과</th>
						<th className="py-2 font-medium">IP</th>
					</tr>
				</thead>
				<tbody>
					{entries.map((entry) => (
						<tr key={entry.auditId} className="border-b last:border-0">
							<td className="py-2.5 pr-4 text-xs text-muted-foreground">{formatDateTime(entry.occurredAt)}</td>
							<td className="py-2.5 pr-4">
								{entry.merchantName ?? <span className="text-xs text-muted-foreground">알 수 없는 가맹점</span>}
								<div className="font-mono text-xs text-muted-foreground">{entry.attemptedMerchantCode}</div>
							</td>
							<td className="py-2.5 pr-4 font-mono text-xs">{entry.attemptedLoginId}</td>
							<td className="py-2.5 pr-4">{entry.userName ?? <span className="text-xs text-muted-foreground">—</span>}</td>
							<td className="py-2.5 pr-4">
								<OutcomeBadge outcome={String(entry.outcome)} />
							</td>
							<td className="py-2.5 font-mono text-xs text-muted-foreground">{entry.clientIp ?? '—'}</td>
						</tr>
					))}
				</tbody>
			</table>
		</div>
	)
}

function OutcomeBadge({ outcome }: { outcome: string }) {
	if (outcome === 'SUCCESS') return <Badge variant="secondary">성공</Badge>
	if (outcome === 'LOCKED') return <Badge variant="destructive">잠김</Badge>
	if (outcome === 'INVALID_CREDENTIALS') return <Badge variant="destructive">실패</Badge>
	return <Badge variant="outline">{outcome}</Badge>
}
