/**
 * 상태 코드 → **한글 표기**.
 *
 * ## 같은 코드가 집합마다 다른 뜻이다
 *
 * 코드 하나로 한글을 정하면 정산 화면에 "결제 대기"가 뜬다:
 *
 * | 코드 | Payment | SettlementReceivable | WebhookDelivery |
 * |---|---|---|---|
 * | `READY` | 결제 대기 | **정산 준비됨** | — |
 * | `PENDING` | — | **정산 대기** | 전송 대기 |
 * | `SUCCEEDED` | 결제 완료 | — | **전송 성공** |
 *
 * 그래서 [labelFor]는 **어느 상태 집합인지**([StatusKind])를 함께 받는다. 호출부는 자기가
 * 무엇을 그리는지 이미 알고 있으므로 추가 부담이 거의 없다.
 *
 * **배지의 색은 이 파일과 무관하다** — 색은 코드만으로 정한다(`StatusBadge`). 색은 거칠어서
 * 코드가 겹쳐도 문제가 없고(`READY`는 어느 집합에서든 "정상"이다), 말은 그렇지 않다.
 */

/**
 * 상태 집합. **표기를 고르는 유일한 근거**다.
 *
 * `audit`만 상태가 아니라 "무슨 일이 있었나"(로그인 결과)지만, 배지로 그리는 모양이 같아서
 * 함께 다룬다.
 */
export type StatusKind =
	| 'payment'
	| 'settlement'
	| 'account'
	| 'apiKey'
	| 'merchant'
	| 'webhook'
	| 'onchain'
	| 'exchange'
	| 'checkout'
	| 'audit'

/** 각 집합은 `docs/domain/state-transitions.md`의 상태 머신과 1:1로 대응한다. */
const LABELS: Record<StatusKind, Record<string, string>> = {
	payment: {
		CREATED: '생성됨',
		READY: '결제 대기',
		PROCESSING: '처리 중',
		CONFIRMING: '확인 중',
		SUCCEEDED: '결제 완료',
		FAILED: '결제 실패',
		EXPIRED: '만료됨',
	},
	settlement: {
		PENDING: '정산 대기',
		READY: '정산 준비됨',
		ASSIGNED: '지급 예정',
		SETTLED: '정산 완료',
		HELD: '보류',
		CANCELLED: '취소됨',
	},
	account: {
		INVITED: '초대됨',
		ACTIVE: '활성',
		// 잠김은 관리자가 닫은 것이 아니라 저절로 걸린 것이라 말도 구분한다.
		LOCKED: '잠김',
		SUSPENDED: '정지',
		TERMINATED: '종료',
	},
	apiKey: {
		ACTIVE: '사용 중',
		REVOKED: '폐기됨',
		EXPIRED: '만료됨',
	},
	merchant: {
		ACTIVE: '활성',
		SUSPENDED: '정지',
		TERMINATED: '종료',
	},
	webhook: {
		PENDING: '전송 대기',
		DELIVERING: '전송 중',
		SUCCEEDED: '전송 성공',
		RETRY_WAITING: '재시도 대기',
		FAILED: '전송 실패',
	},
	onchain: {
		SUBMITTED: '제출됨',
		DETECTED: '감지됨',
		CONFIRMING: '확인 중',
		CONFIRMED: '확정됨',
		FAILED: '실패',
		// 체인 재구성으로 거래가 사라진 상태 — 흔치 않아 용어를 그대로 둔다.
		REORGED: '체인 재구성',
	},
	exchange: {
		REQUESTED: '요청됨',
		SUBMITTED: '제출됨',
		PROCESSING: '처리 중',
		COMPLETED: '매도 완료',
	},
	checkout: {
		CREATED: '생성됨',
		OPEN: '열림',
		WALLET_CONNECTED: '지갑 연결됨',
		PAYMENT_SUBMITTED: '전송 제출됨',
		COMPLETED: '완료',
		EXPIRED: '만료됨',
		CANCELLED: '취소됨',
	},
	audit: {
		SUCCESS: '성공',
		LOCKED: '잠김',
		INVALID_CREDENTIALS: '실패',
		FAILURE: '실패',
	},
}

/**
 * **모르는 코드는 코드 그대로 돌려준다.** 서버에 새 상태가 생겼을 때 화면이 비거나
 * "알 수 없음"으로 뭉개지면 운영자가 무슨 일인지 알 수 없다 — 코드라도 보이면 문서를
 * 찾아볼 수 있고, 동시에 "이 상태에 한글을 붙여 달라"는 신호가 된다.
 */
export function labelFor(kind: StatusKind, status: string): string {
	return LABELS[kind][status] ?? status
}
