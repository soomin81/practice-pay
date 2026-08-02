import { formatDateTime } from '@/console/format'
import type { LoginAuditEntry } from '@/api/types'
import { DataTable, EmptyRow, Td, Th } from '@/components/console/DataTable'
import { StatusBadge } from '@/components/console/StatusBadge'

/**
 * 로그인 감사 로그 표(최신순). 없는 로그인 아이디로의 시도는 `userName`이 없어 "알 수 없는
 * 계정"으로 구분해 보여준다 — 존재하지 않는 계정을 노린 시도를 눈에 띄게 하기 위해서다.
 */
export function LoginAuditTable({ entries }: { entries: readonly LoginAuditEntry[] }) {
	return (
		<DataTable
			head={
				<>
					<Th>시각</Th>
					<Th>로그인 아이디</Th>
					<Th>이름</Th>
					<Th>결과</Th>
					<Th>IP</Th>
				</>
			}
		>
			{entries.length === 0 ? (
				<EmptyRow colSpan={5}>아직 로그인 기록이 없습니다.</EmptyRow>
			) : (
				entries.map((entry) => (
					<tr key={entry.auditId} className="hover:bg-muted/40">
						<Td variant="mono" className="text-xs">
							{formatDateTime(entry.occurredAt)}
						</Td>
						<Td variant="mono" className="text-foreground">
							{entry.attemptedLoginId}
						</Td>
						<Td>{entry.userName ?? <span className="text-xs text-muted-foreground">알 수 없는 계정</span>}</Td>
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

/** 감사 결과는 코드가 아니라 한글로 읽힌다 — 이 표는 사람이 훑어보는 것이 목적이다. */
function outcomeLabel(outcome: string): string {
	if (outcome === 'SUCCESS') return '성공'
	if (outcome === 'LOCKED') return '잠김'
	if (outcome === 'INVALID_CREDENTIALS') return '실패'
	return outcome
}
