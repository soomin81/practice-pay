/**
 * 상태를 파스텔 배지로 보여준다.
 *
 * **상태 이름이 아니라 성격으로 색을 고른다.** 이 시스템에는 상태 집합이 여러 개
 * 있고(`Payment`, `SettlementReceivable`, `MerchantApiKey`, 계정, `WebhookDelivery`),
 * 이름은 제각각이지만 사람이 표를 훑을 때 알고 싶은 것은 다섯 가지뿐이다 —
 * **끝났나 / 기다리나 / 진행 중인가 / 닫혔나 / 잘못됐나**. 그래서 색을 다섯 성격에
 * 배정하고 각 상태를 거기에 매핑한다.
 *
 * 모르는 상태는 `off`(회색)로 떨어뜨린다 — 새 상태가 생겼을 때 화면이 깨지는 대신
 * 눈에 띄지 않는 회색으로 나오고, 그건 "색을 정해 달라"는 신호다.
 */
export type StateTone = 'ok' | 'wait' | 'move' | 'off' | 'bad'

const TONE_CLASS: Record<StateTone, string> = {
	ok: 'bg-state-ok text-state-ok-fg',
	wait: 'bg-state-wait text-state-wait-fg',
	move: 'bg-state-move text-state-move-fg',
	off: 'bg-state-off text-state-off-fg',
	bad: 'bg-state-bad text-state-bad-fg',
}

/**
 * 상태 코드 → 성격. 여러 애그리게이트의 상태가 한 표에 들어 있다.
 *
 * - `ok` 끝났고 정상이다 — 결제 성공, 정산 준비됨, 전송 성공, 활성 계정/Key
 * - `wait` 아직 손대지 않았다 — 생성됨, 대기
 * - `move` 진행 중이다 — 처리 중, 확인 중, 전송 중
 * - `off` 더는 살아있지 않다 — 만료, 취소, 폐기, 종료, 정지
 * - `bad` 잘못됐다 — 실패, reorg
 */
const TONE_BY_STATUS: Record<string, StateTone> = {
	// Payment
	CREATED: 'wait',
	READY: 'ok',
	PROCESSING: 'move',
	CONFIRMING: 'move',
	SUCCEEDED: 'ok',
	FAILED: 'bad',
	EXPIRED: 'off',
	// SettlementReceivable
	PENDING: 'wait',
	ASSIGNED: 'move',
	SETTLED: 'ok',
	HELD: 'off',
	CANCELLED: 'off',
	// 계정 / API Key
	ACTIVE: 'ok',
	INVITED: 'wait',
	// **잠김은 정지와 다르다.** 정지·종료는 관리자가 의도해서 닫은 것이고, 잠김은
	// 로그인 실패가 쌓여 저절로 걸린 것이라 누군가 봐야 하는 상태다.
	LOCKED: 'bad',
	SUSPENDED: 'off',
	TERMINATED: 'off',
	REVOKED: 'off',
	// 로그인 감사 결과 — 상태가 아니라 "무슨 일이 있었나"지만 색의 성격은 같다.
	SUCCESS: 'ok',
	FAILURE: 'bad',
	INVALID_CREDENTIALS: 'bad',
	// WebhookDelivery
	DELIVERING: 'move',
	RETRY_WAITING: 'wait',
	// BlockchainTransaction
	SUBMITTED: 'wait',
	DETECTED: 'move',
	CONFIRMED: 'ok',
	REORGED: 'bad',
	// ExchangeOrder
	REQUESTED: 'wait',
	COMPLETED: 'ok',
}

function toneFor(status: string): StateTone {
	return TONE_BY_STATUS[status] ?? 'off'
}

export function StatusBadge({ status, label }: { status: string; label?: string }) {
	return (
		<span
			className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium whitespace-nowrap ${TONE_CLASS[toneFor(status)]}`}
		>
			{label ?? status}
		</span>
	)
}
