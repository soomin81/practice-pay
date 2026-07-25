import { useState } from 'react'
import { useRevokeApiKey } from '@/console/useApiKeys'
import { formatDateTime } from '@/console/format'
import type { ApiKeySummary } from '@/api/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

/** API Key 목록 테이블. 활성 Key에만 폐기 버튼을 두고, 폐기는 인라인으로 한 번 더 확인한다. */
export function ApiKeyTable({ apiKeys }: { apiKeys: readonly ApiKeySummary[] }) {
	if (apiKeys.length === 0) {
		return <p className="text-sm text-muted-foreground">아직 발급된 API Key가 없습니다.</p>
	}

	return (
		<div className="overflow-x-auto">
			<table className="w-full min-w-[40rem] border-collapse text-sm">
				<thead>
					<tr className="border-b text-left text-xs text-muted-foreground">
						<th className="py-2 pr-4 font-medium">이름</th>
						<th className="py-2 pr-4 font-medium">Prefix</th>
						<th className="py-2 pr-4 font-medium">Scope</th>
						<th className="py-2 pr-4 font-medium">상태</th>
						<th className="py-2 pr-4 font-medium">발급</th>
						<th className="py-2 pr-4 font-medium">마지막 사용</th>
						<th className="py-2 font-medium" />
					</tr>
				</thead>
				<tbody>
					{apiKeys.map((key) => (
						<tr key={key.merchantApiKeyId} className="border-b last:border-0">
							<td className="py-2.5 pr-4">{key.keyName}</td>
							<td className="py-2.5 pr-4 font-mono text-xs">{key.keyPrefix}</td>
							<td className="py-2.5 pr-4 text-xs text-muted-foreground">
								{key.scopes.map(String).join(', ')}
							</td>
							<td className="py-2.5 pr-4">
								<StatusBadge status={String(key.status)} />
							</td>
							<td className="py-2.5 pr-4 text-xs text-muted-foreground">{formatDateTime(key.createdAt)}</td>
							<td className="py-2.5 pr-4 text-xs text-muted-foreground">{formatDateTime(key.lastUsedAt)}</td>
							<td className="py-2.5 text-right">
								{String(key.status) === 'ACTIVE' && <RevokeAction merchantApiKeyId={key.merchantApiKeyId} />}
							</td>
						</tr>
					))}
				</tbody>
			</table>
		</div>
	)
}

function StatusBadge({ status }: { status: string }) {
	if (status === 'ACTIVE') return <Badge variant="secondary">ACTIVE</Badge>
	if (status === 'EXPIRED') return <Badge variant="destructive">EXPIRED</Badge>
	// 그 밖의 상태(REVOKED 등)는 중립적으로 — shadcn 생성물의 variant 목록에 muted는 없다.
	return <Badge variant="outline">{status}</Badge>
}

/**
 * 폐기 버튼. 브라우저 `confirm()`을 쓰지 않고 인라인으로 한 번 더 묻는다 — 되돌릴 수
 * 없는 동작이라 오조작을 막되(폐기된 Key는 재활성화 불가), 모달 dialog 없이 가볍게 둔다.
 */
function RevokeAction({ merchantApiKeyId }: { merchantApiKeyId: string }) {
	const [confirming, setConfirming] = useState(false)
	const revoke = useRevokeApiKey()

	if (!confirming) {
		return (
			<Button variant="destructive" size="sm" onClick={() => setConfirming(true)}>
				폐기
			</Button>
		)
	}

	return (
		<span className="inline-flex items-center gap-1">
			<span className="text-xs text-muted-foreground">폐기할까요?</span>
			<Button variant="destructive" size="sm" onClick={() => revoke.mutate(merchantApiKeyId)} disabled={revoke.isPending}>
				확인
			</Button>
			<Button variant="ghost" size="sm" onClick={() => setConfirming(false)} disabled={revoke.isPending}>
				취소
			</Button>
		</span>
	)
}
