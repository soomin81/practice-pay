import { useState } from 'react'
import { useRevokeApiKey } from '@/console/useApiKeys'
import { formatDateTime } from '@/console/format'
import type { ApiKeySummary } from '@/api/types'
import { Button } from '@/components/ui/button'
import { DataTable, EmptyRow, Td, Th } from '@/components/console/DataTable'
import { StatusBadge } from '@/components/console/StatusBadge'

/** API Key 목록 테이블. 활성 Key에만 폐기 버튼을 두고, 폐기는 인라인으로 한 번 더 확인한다. */
export function ApiKeyTable({ apiKeys }: { apiKeys: readonly ApiKeySummary[] }) {
	return (
		<DataTable
			head={
				<>
					<Th>이름</Th>
					<Th>Prefix</Th>
					<Th>Scope</Th>
					<Th>상태</Th>
					<Th>발급</Th>
					<Th>마지막 사용</Th>
					<Th align="right"> </Th>
				</>
			}
		>
			{apiKeys.length === 0 ? (
				<EmptyRow colSpan={7}>아직 발급된 API Key가 없습니다.</EmptyRow>
			) : (
				apiKeys.map((key) => (
					<tr key={key.merchantApiKeyId} className="hover:bg-muted/40">
						<Td className="font-medium">{key.keyName}</Td>
						<Td variant="mono" className="text-foreground">
							{key.keyPrefix}
						</Td>
						<Td className="text-xs text-muted-foreground">{key.scopes.map(String).join(', ')}</Td>
						<Td>
							<StatusBadge kind="apiKey" status={String(key.status)} />
						</Td>
						<Td variant="mono" className="text-xs">
							{formatDateTime(key.createdAt)}
						</Td>
						<Td variant="mono" className="text-xs">
							{formatDateTime(key.lastUsedAt)}
						</Td>
						<Td className="text-right">
							{String(key.status) === 'ACTIVE' && <RevokeAction merchantApiKeyId={key.merchantApiKeyId} />}
						</Td>
					</tr>
				))
			)}
		</DataTable>
	)
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
