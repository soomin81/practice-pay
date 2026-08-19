package paytech.practice.pay.domain.customer

import paytech.practice.pay.domain.payment.PaymentId
import java.time.Instant

/**
 * 결제 한 건에 붙는 **구매자 정보** Aggregate Root다(`payment_customer` 테이블, 1:1).
 *
 * ## 이 타입은 언제나 평문만 안다
 *
 * 암호화·복호화·Blind Index 계산은 **전부 어댑터 경계**에서 일어난다 — 도메인은 인프라를
 * 모른다는 규칙 그대로다. 대신 **마스킹은 여기가 갖는다**(각 VO의 `masked`): 마스킹 규칙은
 * 표현 형식이 아니라 업무 규칙이고, 두 콘솔이 각자 마스킹하면 규칙이 갈린다(ADR-008).
 *
 * ## 왜 `Payment`에 넣지 않았나
 *
 * 결제를 읽는 모든 경로(목록·정산·환전 워커)가 개인정보를 함께 끌고 오지 않게 하려는 것이고,
 * **보관 기간이 지나 파기할 때 이 애그리게이트만 지우면 결제 기록은 그대로 남기** 때문이다 —
 * 결제 기록과 개인정보는 수명이 다르다.
 *
 * ## 상태 전이가 없다
 *
 * 구매자 정보에는 상태가 없다. 다만 **고칠 수는 있어야 한다** — 고객이 오타를 냈을 때 결제를
 * 처음부터 다시 만들게 하는 것은 과하다. 그래서 [PaymentQuote][paytech.practice.pay.domain.quote.PaymentQuote]처럼
 * 완전한 불변 스냅샷으로 두지 않고 [change]를 뒀고, `updated_at`/`version` 컬럼도 갖는다.
 */
class PaymentCustomer private constructor(
	val id: PaymentCustomerId,
	val paymentId: PaymentId,
	val createdAt: Instant,
	name: CustomerName,
	email: CustomerEmail,
	phone: CustomerPhone,
	updatedAt: Instant,
) {
	var name: CustomerName = name
		private set

	var email: CustomerEmail = email
		private set

	var phone: CustomerPhone = phone
		private set

	var updatedAt: Instant = updatedAt
		private set

	/**
	 * 입력한 값을 고친다 — 고객이 오타를 냈을 때 쓴다.
	 *
	 * **어느 항목을 바꿨는지 이력으로 남기지 않는다.** 남기려면 옛 값을 어딘가 보관해야 하는데,
	 * 그건 지우려고 만든 구조(파기 가능한 별도 테이블)와 정면으로 어긋난다 — 고치기 전 값이
	 * 다른 테이블에 남으면 파기가 반쪽이 된다.
	 */
	fun change(
		name: CustomerName,
		email: CustomerEmail,
		phone: CustomerPhone,
		changedAt: Instant,
	) {
		this.name = name
		this.email = email
		this.phone = phone
		updatedAt = changedAt
	}

	companion object {
		fun create(
			id: PaymentCustomerId,
			paymentId: PaymentId,
			name: CustomerName,
			email: CustomerEmail,
			phone: CustomerPhone,
			createdAt: Instant,
		): PaymentCustomer =
			PaymentCustomer(
				id = id,
				paymentId = paymentId,
				createdAt = createdAt,
				name = name,
				email = email,
				phone = phone,
				updatedAt = createdAt,
			)

		/** 영속 계층에 저장되어 있던 값으로 Aggregate를 복원한다(어댑터가 복호화한 뒤 부른다). */
		fun reconstitute(
			id: PaymentCustomerId,
			paymentId: PaymentId,
			name: CustomerName,
			email: CustomerEmail,
			phone: CustomerPhone,
			createdAt: Instant,
			updatedAt: Instant,
		): PaymentCustomer =
			PaymentCustomer(
				id = id,
				paymentId = paymentId,
				createdAt = createdAt,
				name = name,
				email = email,
				phone = phone,
				updatedAt = updatedAt,
			)
	}
}
