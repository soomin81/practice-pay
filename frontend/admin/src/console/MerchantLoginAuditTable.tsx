import { formatDateTime } from '@/console/format'
import type { MerchantLoginAuditEntry } from '@/api/types'
import { DataTable, EmptyRow, Td, Th } from '@/components/console/DataTable'
import { StatusBadge } from '@/components/console/StatusBadge'

/**
 * 가맹점 로그인 감사 로그 표(전 가맹점, 최신순). 없는 merchantCode로의 시도는 `merchantName`이
 * 없어 "알 수 없는 가맹점"으로, 없는 loginId 시도는 `userName`이 없어 대시로 구분해 보여준다.
 */
export function MerchantLoginAuditTable({ entries }: { entries: readonly MerchantLoginAuditEntry[] }) {
	return (
		<DataTable
			head={
				<>
					<Th>시각</Th>
					<Th>가맹점</Th>
					<Th>로그인 아이디</Th>
					<Th>이름</Th>
					<Th>결과</Th>
					<Th>IP</Th>
				</>
			}
		>
			{entries.length === 0 ? (
				<EmptyRow colSpan={6}>아직 가맹점 로그인 기록이 없습니다.</EmptyRow>
			) : (
				entries.map((entry) => (
					<tr key={entry.auditId} className="hover:bg-muted/40">
						<Td variant="mono" className="text-xs">
							{formatDateTime(entry.occurredAt)}
						</Td>
						<Td>
							{entry.merchantName ?? <span className="text-xs text-muted-foreground">알 수 없는 가맹점</span>}
							<div className="mono-cell text-xs text-muted-foreground">{entry.attemptedMerchantCode}</div>
						</Td>
						<Td variant="mono" className="text-foreground">
							{entry.attemptedLoginId}
						</Td>
						<Td>{entry.userName ?? <span className="text-xs text-muted-foreground">—</span>}</Td>
						<Td>
							<StatusBadge status={String(entry.outcome)} label={outcomeLabel(String(entry.outcome))} />
						</Td>
						<Td variant="mono" className="text-xs">
							{entry.clientIp ?? '—'}
						</Td>
					</tr>
				))
			)}
		</DataTable>
	)
}

/** 감사 결과는 코드가 아니라 한글로 읽힌다(`LoginAuditTable`과 같은 규칙). */
function outcomeLabel(outcome: string): string {
	if (outcome === 'SUCCESS') return '성공'
	if (outcome === 'LOCKED') return '잠김'
	if (outcome === 'INVALID_CREDENTIALS') return '실패'
	return outcome
}
