import { labelFor, type StatusKind } from '@/components/console/statusLabel'

/**
 * 상태를 파스텔 배지로 **한글과 함께** 보여준다.
 *
 * **색은 코드로, 말은 맥락으로 고른다.** 색은 상태 이름이 아니라 성격으로 정하는데, 사람이
 * 표를 훑을 때 알고 싶은 것이 다섯 가지뿐이기 때문이다 — **끝났나 / 기다리나 / 진행 중인가 /
 * 닫혔나 / 잘못됐나**. 색은 거칠어서 코드가 겹쳐도 문제가 없다(`READY`는 어느 집합에서든
 * "정상"이다).
 *
 * 말은 다르다 — 같은 코드가 애그리게이트마다 다른 뜻이라 [StatusKind]가 필요하다. 근거와
 * 표기 목록은 `statusLabel.ts`에 있다.
 *
 * 모르는 상태는 색을 `off`(회색)로 떨어뜨린다 — 새 상태가 생겼을 때 화면이 깨지는 대신
 * 눈에 띄지 않는 회색으로 나오고, 그건 "색을 정해 달라"는 신호다.
 */
type StateTone = 'ok' | 'wait' | 'move' | 'off' | 'bad'

const TONE_CLASS: Record<StateTone, string> = {
	ok: 'bg-state-ok text-state-ok-fg',
	wait: 'bg-state-wait text-state-wait-fg',
	move: 'bg-state-move text-state-move-fg',
	off: 'bg-state-off text-state-off-fg',
	bad: 'bg-state-bad text-state-bad-fg',
}

/**
 * 상태 코드 → 성격(색).
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
	// CheckoutSession
	OPEN: 'wait',
	WALLET_CONNECTED: 'move',
	PAYMENT_SUBMITTED: 'move',
}

export function StatusBadge({
	kind,
	status,
	label,
}: {
	kind: StatusKind
	status: string
	/** 표기를 직접 지정한다. 지정하지 않으면 [kind]에 맞는 한글을 고른다. */
	label?: string
}) {
	return (
		<span
			className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium whitespace-nowrap ${
				TONE_CLASS[TONE_BY_STATUS[status] ?? 'off']
			}`}
			// 코드를 완전히 감추지 않는다 — 운영자가 API 문서·로그와 대조해야 할 때가 있다.
			title={status}
		>
			{label ?? labelFor(kind, status)}
		</span>
	)
}
