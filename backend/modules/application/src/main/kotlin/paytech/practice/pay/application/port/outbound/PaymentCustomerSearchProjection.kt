package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Money
import java.time.Instant

/**
 * 구매자 정보를 **Blind Index로 찾는** 읽기 전용 Port다
 * (`docs/architecture/admin-console-api.md`의 4.7).
 *
 * `PaymentCustomerRepository`(Command Repository)를 쓰지 않고 전용 Projection을 두는 이유는
 * `PaymentListProjection`이 세운 선례와 같다 — 화면에 필요한 값이 `payment_customer` 하나에
 * 없고 `payment`/`merchant`까지 걸쳐 있다("이 이메일이 어느 결제였나"에 답해야 한다).
 *
 * ## 이 Port는 복호화하지 않는다
 *
 * 돌려주는 것은 `*_masked` 컬럼뿐이다 — 검색은 복호화 경로를 아예 타지 않는다(ADR-008).
 * 원문이 필요하면 `RevealPaymentCustomerUseCase`를 거쳐야 하고, 그 경로에는 감사 기록이 있다.
 *
 * ## 정규화된 값이 아니라 **인덱스**를 받는다
 *
 * 인덱스 계산은 `PaymentCustomerCrypto`(application)가 한다 — 이 Port의 구현
 * (`modules:infra-persistence`)에 Pepper를 주지 않기 위해서다. 어댑터는 받은 문자열로
 * 컬럼을 비교하기만 한다.
 */
interface PaymentCustomerSearchProjection {
	/** 이메일 Blind Index가 정확히 일치하는 결제들. 최신순. 없으면 빈 목록. */
	fun findByEmailIndex(emailIndex: String): List<PaymentCustomerSearchEntry>

	/** 휴대전화 Blind Index가 정확히 일치하는 결제들. 최신순. 없으면 빈 목록. */
	fun findByPhoneIndex(phoneIndex: String): List<PaymentCustomerSearchEntry>
}

/**
 * 검색 결과 한 줄 — **마스킹된 값과 그 결제를 식별할 만큼**만 담는다.
 *
 * 필드 이름은 `PaymentListEntry`와 같은 것을 쓴다(`paymentId`/`merchantName`/`status`…) —
 * 같은 개념에 두 이름을 만들지 않는다(`docs/domain/glossary.md`).
 */
data class PaymentCustomerSearchEntry(
	val paymentId: PaymentId,
	val merchantId: MerchantId,
	val merchantName: String,
	val merchantOrderId: MerchantOrderId,
	val orderName: String,
	val orderAmount: Money,
	val status: PaymentStatus,
	val nameMasked: String,
	val emailMasked: String,
	val phoneMasked: String,
	val paidAt: Instant?,
	val createdAt: Instant,
)
