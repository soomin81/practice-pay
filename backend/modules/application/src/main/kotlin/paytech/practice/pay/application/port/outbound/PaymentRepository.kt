package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentId
import java.time.Instant

/**
 * [Payment] Aggregate를 저장·복원하는 Command Repository Outbound Port다.
 */
interface PaymentRepository {
	/** Payment를 저장한다(신규 생성·상태 변경 모두 이 메서드로 반영한다). */
	fun save(payment: Payment)

	/** `payment_id`로 Payment를 찾는다. 없으면 `null`이다. */
	fun findById(paymentId: PaymentId): Payment?

	/**
	 * [findById]와 같지만 **행 잠금을 잡고** 읽는다(`SELECT ... FOR UPDATE`) — 읽은 값을 바꿔
	 * 다시 저장할 목적일 때만 쓴다.
	 *
	 * **반드시 트랜잭션 안에서 불러야 한다**([TransactionManager]) — 잠금은 트랜잭션이 끝날 때
	 * 풀리므로, 트랜잭션 없이 부르면 자동 커밋으로 즉시 풀려 아무것도 막지 못한다.
	 *
	 * 이게 필요한 이유: 같은 결제를 두 흐름이 동시에 읽어 각자 전이시키면(예: 고객의 결제 제출과
	 * 만료 Sweep Worker) 도메인의 상태 가드는 각자의 메모리 사본에만 적용돼 막지 못하고, 나중에
	 * 저장한 쪽이 먼저 저장한 쪽을 덮어쓴다. 잠금을 **변경할 목적으로 읽는 시점**에 잡아야 두
	 * 흐름이 직렬화되고, 뒤에 온 흐름이 앞 흐름의 결과를 보고 다시 판단할 수 있다.
	 */
	fun findByIdForUpdate(paymentId: PaymentId): Payment?

	/**
	 * `(merchant_seq, merchant_order_id)` 조합으로 기존 Payment를 찾는다.
	 *
	 * 결제 생성의 멱등성 키다(`backend/CLAUDE.md`의 "Idempotency keys" 참고) — 같은
	 * 가맹점 주문으로 다시 결제 생성을 요청하면 새로 만들지 않고 이 조회 결과를
	 * 재사용한다.
	 */
	fun findByMerchantOrderId(
		merchantId: MerchantId,
		merchantOrderId: MerchantOrderId,
	): Payment?

	/**
	 * 아직 Fake Exchange 매도 처리가 안 된 `SUCCEEDED` Payment를 전부 찾는다 —
	 * 발행 Worker(`apps:batch`)가 폴링 대상 목록을 뽑을 때 쓴다.
	 *
	 * `payment` 레코드에는 정산 상태를 두지 않는다는 규칙(루트 `CLAUDE.md`) 때문에
	 * Payment 테이블만으로는 "이미 매도 처리됐는지"를 판단할 수 없다 — 그래서 이
	 * 조회는 `exchange_order`(Payment 1건당 최대 1건, `uk_exchange_payment`)가
	 * 아직 없는 `SUCCEEDED` Payment를 찾는 크로스 애그리게이트 Join으로 구현된다
	 * (`docs/database/database-design.md`에 이 폴링만을 위한 전용 인덱스가 명시돼
	 * 있지는 않다 — Confirm Worker/Outbox 발행과 달리 알려진 gap).
	 */
	fun findPendingExchangeSettlement(): List<Payment>

	/**
	 * **아직 만료 전이되지 않은, 만료 시각이 지난 Payment**를 전부 찾는다 — 만료 Sweep
	 * Worker(`apps:batch`)가 폴링 대상을 뽑을 때 쓴다. `status IN (CREATED, READY)`이고
	 * `expires_at < now`인 것만 돌려준다([Payment.expire]가 그 두 상태에서만 허용된다).
	 *
	 * [findPendingExchangeSettlement]와 같은 성격의 **도메인 규칙 보조 조회**라 Projection이
	 * 아니라 Command Repository에 둔다. 후보를 뽑은 뒤 실제 전이 직전에 Use Case가 다시
	 * 읽어 상태를 재검증하므로(그 사이 결제가 진행됐을 수 있다), 이 조회 결과가 살짝 낡아도
	 * 안전하다.
	 */
	fun findExpirable(now: Instant): List<Payment>
}
