import { formatDateTime } from '@/console/format'
import type { LoginAuditEntry } from '@/api/types'
import { Badge } from '@/components/ui/badge'

/**
 * 로그인 감사 로그 표(최신순). 없는 로그인 아이디로의 시도는 `userName`이 없어 "알 수 없는
 * 계정"으로 구분해 보여준다 — 존재하지 않는 계정을 노린 시도를 눈에 띄게 하기 위해서다.
 */
export function LoginAuditTable({ entries }: { entries: readonly LoginAuditEntry[] }) {
	if (entries.length === 0) {
		return <p className="text-sm text-muted-foreground">아직 로그인 기록이 없습니다.</p>
	}

	return (
		<div className="overflow-x-auto">
			<table className="w-full min-w-[44rem] border-collapse text-sm">
				<thead>
					<tr className="border-b text-left text-xs text-muted-foreground">
						<th className="py-2 pr-4 font-medium">시각</th>
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
							<td className="py-2.5 pr-4 font-mono text-xs">{entry.attemptedLoginId}</td>
							<td className="py-2.5 pr-4">
								{entry.userName ?? <span className="text-xs text-muted-foreground">알 수 없는 계정</span>}
							</td>
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
