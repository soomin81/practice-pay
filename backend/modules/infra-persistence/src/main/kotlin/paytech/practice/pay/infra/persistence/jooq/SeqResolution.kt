package paytech.practice.pay.infra.persistence.jooq

import org.jooq.DSLContext
import paytech.practice.pay.dbcore.jooq.tables.ExchangeOrder.Companion.EXCHANGE_ORDER
import paytech.practice.pay.dbcore.jooq.tables.InternalUser.Companion.INTERNAL_USER
import paytech.practice.pay.dbcore.jooq.tables.Merchant.Companion.MERCHANT
import paytech.practice.pay.dbcore.jooq.tables.MerchantUser.Companion.MERCHANT_USER
import paytech.practice.pay.dbcore.jooq.tables.Payment.Companion.PAYMENT
import paytech.practice.pay.domain.exchange.ExchangeOrderId
import paytech.practice.pay.domain.identity.InternalUserId
import paytech.practice.pay.domain.identity.MerchantUserId
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.PaymentId

/**
 * 공개 ID(`*_id`, `VARCHAR`)와 내부 PK(`*_seq`, `BIGINT AUTO_INCREMENT`) 사이를 변환하는 공유
 * 헬퍼다 — 애그리게이트가 다른 애그리게이트를 공개 ID로만 참조하는데(도메인 순수성) DB의
 * FK는 seq로 걸려 있어, 저장·복원 시 어댑터가 이 둘을 오가야 한다.
 *
 * 원래 각 Repository Adapter가 `resolveMerchantSeq`류 private 함수로 **같은 조회를 복제**하고
 * 있었다(`resolve*` 33개가 12개 어댑터에 흩어져 있었다). `InstantMapping`의 시각 변환을 모든
 * 어댑터가 공유하는 것과 같은 이유로 여기 [DSLContext] 확장으로 모았다.
 *
 * 없는 값이면 `error(...)`로 즉시 실패한다 — FK 대상이 존재한다는 것은 호출부가 이미 보장한
 * 상태이므로(예: `save`가 방금 상위 애그리게이트를 넣었거나, 복원 중인 행의 FK다), 여기서의
 * 부재는 데이터 정합성이 깨진 예외 상황이다.
 */
fun DSLContext.merchantSeq(merchantId: MerchantId): Long =
	select(MERCHANT.MERCHANT_SEQ)
		.from(MERCHANT)
		.where(MERCHANT.MERCHANT_ID.eq(merchantId.value))
		.fetchOne(MERCHANT.MERCHANT_SEQ)
		?: error("Merchant(${merchantId.value})를 찾을 수 없습니다.")

fun DSLContext.merchantId(merchantSeq: Long): MerchantId =
	select(MERCHANT.MERCHANT_ID)
		.from(MERCHANT)
		.where(MERCHANT.MERCHANT_SEQ.eq(merchantSeq))
		.fetchOne(MERCHANT.MERCHANT_ID)
		?.let { MerchantId(it) }
		?: error("Merchant(seq=$merchantSeq)를 찾을 수 없습니다.")

fun DSLContext.internalUserSeq(internalUserId: InternalUserId): Long =
	select(INTERNAL_USER.INTERNAL_USER_SEQ)
		.from(INTERNAL_USER)
		.where(INTERNAL_USER.INTERNAL_USER_ID.eq(internalUserId.value))
		.fetchOne(INTERNAL_USER.INTERNAL_USER_SEQ)
		?: error("InternalUser(${internalUserId.value})를 찾을 수 없습니다.")

fun DSLContext.internalUserId(internalUserSeq: Long): InternalUserId =
	select(INTERNAL_USER.INTERNAL_USER_ID)
		.from(INTERNAL_USER)
		.where(INTERNAL_USER.INTERNAL_USER_SEQ.eq(internalUserSeq))
		.fetchOne(INTERNAL_USER.INTERNAL_USER_ID)
		?.let { InternalUserId(it) }
		?: error("InternalUser(seq=$internalUserSeq)를 찾을 수 없습니다.")

fun DSLContext.merchantUserSeq(merchantUserId: MerchantUserId): Long =
	select(MERCHANT_USER.MERCHANT_USER_SEQ)
		.from(MERCHANT_USER)
		.where(MERCHANT_USER.MERCHANT_USER_ID.eq(merchantUserId.value))
		.fetchOne(MERCHANT_USER.MERCHANT_USER_SEQ)
		?: error("MerchantUser(${merchantUserId.value})를 찾을 수 없습니다.")

fun DSLContext.merchantUserId(merchantUserSeq: Long): MerchantUserId =
	select(MERCHANT_USER.MERCHANT_USER_ID)
		.from(MERCHANT_USER)
		.where(MERCHANT_USER.MERCHANT_USER_SEQ.eq(merchantUserSeq))
		.fetchOne(MERCHANT_USER.MERCHANT_USER_ID)
		?.let { MerchantUserId(it) }
		?: error("MerchantUser(seq=$merchantUserSeq)를 찾을 수 없습니다.")

fun DSLContext.paymentSeq(paymentId: PaymentId): Long =
	select(PAYMENT.PAYMENT_SEQ)
		.from(PAYMENT)
		.where(PAYMENT.PAYMENT_ID.eq(paymentId.value))
		.fetchOne(PAYMENT.PAYMENT_SEQ)
		?: error("Payment(${paymentId.value})를 찾을 수 없습니다.")

fun DSLContext.paymentId(paymentSeq: Long): PaymentId =
	select(PAYMENT.PAYMENT_ID)
		.from(PAYMENT)
		.where(PAYMENT.PAYMENT_SEQ.eq(paymentSeq))
		.fetchOne(PAYMENT.PAYMENT_ID)
		?.let { PaymentId(it) }
		?: error("Payment(seq=$paymentSeq)를 찾을 수 없습니다.")

fun DSLContext.exchangeOrderSeq(exchangeOrderId: ExchangeOrderId): Long =
	select(EXCHANGE_ORDER.EXCHANGE_ORDER_SEQ)
		.from(EXCHANGE_ORDER)
		.where(EXCHANGE_ORDER.EXCHANGE_ORDER_ID.eq(exchangeOrderId.value))
		.fetchOne(EXCHANGE_ORDER.EXCHANGE_ORDER_SEQ)
		?: error("ExchangeOrder(${exchangeOrderId.value})를 찾을 수 없습니다.")

fun DSLContext.exchangeOrderId(exchangeOrderSeq: Long): ExchangeOrderId =
	select(EXCHANGE_ORDER.EXCHANGE_ORDER_ID)
		.from(EXCHANGE_ORDER)
		.where(EXCHANGE_ORDER.EXCHANGE_ORDER_SEQ.eq(exchangeOrderSeq))
		.fetchOne(EXCHANGE_ORDER.EXCHANGE_ORDER_ID)
		?.let { ExchangeOrderId(it) }
		?: error("ExchangeOrder(seq=$exchangeOrderSeq)를 찾을 수 없습니다.")
