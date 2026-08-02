import { useState } from 'react'
import type { SettlementReceivableSummary } from '@/api/types'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { StatusBadge } from '@/components/console/StatusBadge'
import { labelFor } from '@/components/console/statusLabel'
import { formatDateTime } from '@/console/format'
import {
	settlementHoldErrorMessage,
	useCancelSettlementReceivable,
	useReleaseSettlementHold,
	useSettlementHoldHistory,
} from '@/console/useSettlementHold'

type Mode = 'closed' | 'release' | 'cancel' | 'history'

/**
 * 보류된 정산 채권의 행 액션 — **해제**, **취소**, 그리고 **이력 보기**.
 *
 * ## 왜 이 화면에 둘 다 있나
 *
 * 막을 수만 있고 풀 수 없으면, 정작 눌러야 할 상황에서 운영자가 망설여 보류 자체가
 * 쓰이지 않는다(ADR-007). 그래서 두 갈래 출구를 나란히 둔다 — 다만 **취소는 되돌릴 수
 * 없으므로** 확인 문구를 다르게 쓴다.
 *
 * ## `HELD` 행에만 그린다
 *
 * 서버의 취소는 `PENDING`/`READY`에서도 되지만(도메인이 허용한다), 막지도 않은 채권을
 * 목록에서 곧장 끝낼 수 있게 두면 실수 한 번의 대가가 너무 크다. 화면에서만 좁히는
 * 제약이고 서버는 강제하지 않는다.
 */
export function SettlementHoldActions({ row, canManage }: { row: SettlementReceivableSummary; canManage: boolean }) {
	const [mode, setMode] = useState<Mode>('closed')
	const [note, setNote] = useState('')
	const release = useReleaseSettlementHold(row.settlementReceivableId)
	const cancel = useCancelSettlementReceivable(row.settlementReceivableId)
	const history = useSettlementHoldHistory(row.settlementReceivableId, mode === 'history')

	// 보류가 아니면 풀 것도 끝낼 것도 없다. 이력만은 남아 있을 수 있어 언제나 열 수 있다.
	const held = row.status === 'HELD'

	function close() {
		setMode('closed')
		setNote('')
		release.reset()
		cancel.reset()
	}

	if (mode === 'closed') {
		return (
			<div className="flex flex-wrap gap-1">
				{held && canManage ? (
					<>
						<Button size="sm" variant="outline" onClick={() => setMode('release')}>
							보류 해제
						</Button>
						<Button size="sm" variant="outline" onClick={() => setMode('cancel')}>
							취소
						</Button>
					</>
				) : null}
				<Button size="sm" variant="ghost" onClick={() => setMode('history')}>
					이력
				</Button>
			</div>
		)
	}

	if (mode === 'history') {
		return (
			<div className="flex flex-col gap-2">
				{history.isPending ? <p className="text-sm text-muted-foreground">불러오는 중…</p> : null}
				{history.error ? (
					<p className="text-sm text-destructive">{settlementHoldErrorMessage(history.error)}</p>
				) : null}
				{history.data ? (
					history.data.history.length === 0 ? (
						/* **비어 있다는 것도 정보다** — "손댄 적이 없다"와 "못 불러왔다"를 구분해야 한다. */
						<p className="text-sm text-muted-foreground">보류·해제·취소된 적이 없습니다.</p>
					) : (
						<ul className="flex flex-col gap-2">
							{history.data.history.map((entry) => (
								<li key={entry.auditId} className="text-sm">
									<div className="flex items-center gap-2">
										<StatusBadge kind="settlementHoldAction" status={entry.action} />
										<span className="text-muted-foreground">{formatDateTime(entry.occurredAt)}</span>
										<span>{entry.internalUserName}</span>
									</div>
									{/* 보류는 사유 코드가, 해제·취소는 메모가 이유를 갖는다. */}
									{entry.reasonCode ? (
										<div className="mono-cell text-xs text-muted-foreground">{entry.reasonCode}</div>
									) : null}
									{entry.note ? <div className="text-xs text-muted-foreground">{entry.note}</div> : null}
								</li>
							))}
						</ul>
					)
				) : null}
				<div>
					<Button size="sm" variant="outline" onClick={close}>
						닫기
					</Button>
				</div>
			</div>
		)
	}

	const cancelling = mode === 'cancel'
	const mutation = cancelling ? cancel : release
	const noteId = `hold-note-${row.settlementReceivableId}`

	return (
		<Alert variant={cancelling ? 'destructive' : 'default'} className="flex flex-col gap-3">
			<AlertTitle>{cancelling ? '되돌릴 수 없습니다' : '보류를 풉니다'}</AlertTitle>
			<AlertDescription>
				{cancelling ? (
					<>
						이 채권을 <strong>취소</strong>하면 이 결제는 영영 정산되지 않습니다. 가맹점과 합의가 끝났는지 먼저
						확인하세요.
					</>
				) : (
					<>
						정산 흐름으로 되돌립니다 — <strong>매도가 끝난 채권은 정산 준비됨</strong>, 아직이면 정산 대기로
						갑니다(어느 쪽인지는 서버가 정합니다). 막았던 이유가 해소됐는지 확인하세요.
					</>
				)}
			</AlertDescription>
			{/* **사유는 필수다** — 자동 경로가 없는 전이라 실행한 사람 말고는 이유를 아는 곳이 없다. */}
			<div className="flex flex-col gap-1">
				<Label htmlFor={noteId}>사유 (필수)</Label>
				<Input
					id={noteId}
					value={note}
					onChange={(event) => setNote(event.target.value)}
					placeholder={cancelling ? '왜 정산하지 않기로 했는지' : '왜 풀어도 되는지'}
				/>
			</div>
			{mutation.error ? <p className="text-sm text-destructive">{settlementHoldErrorMessage(mutation.error)}</p> : null}
			{mutation.data ? (
				<p className="text-sm">
					{labelFor('settlement', mutation.data.status)}(으)로 바뀌었습니다.
				</p>
			) : null}
			<div className="flex items-center gap-2">
				<Button
					size="sm"
					variant={cancelling ? 'destructive' : 'default'}
					disabled={mutation.isPending || note.trim() === ''}
					onClick={() => mutation.mutate(note, { onSuccess: () => close() })}
				>
					{mutation.isPending ? '처리 중…' : cancelling ? '취소합니다' : '해제합니다'}
				</Button>
				<Button size="sm" variant="outline" onClick={close}>
					그만두기
				</Button>
			</div>
		</Alert>
	)
}
