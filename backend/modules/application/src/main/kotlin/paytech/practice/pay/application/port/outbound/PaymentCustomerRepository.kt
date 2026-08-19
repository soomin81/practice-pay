package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.customer.PaymentCustomerId
import paytech.practice.pay.domain.payment.PaymentId
import java.time.Instant

/**
 * 구매자 개인정보를 저장·복원하는 Command Repository Outbound Port다.
 *
 * ## 이 Port는 **평문을 주고받지 않는다**
 *
 * 다른 Repository와 달리 도메인 Aggregate(`PaymentCustomer`)가 아니라 이미 암호화된
 * [EncryptedPaymentCustomer]를 오간다. 그래서 이 Port의 구현(`modules:infra-persistence`)은
 * **암호 키를 아예 갖지 않고, 복호화할 방법도 없다** — 옮기기만 한다.
 *
 * 이유는 배선에 있다: 네 앱 모두 `infra.persistence.jooq`를 통째로 컴포넌트 스캔하므로,
 * 어댑터가 [PiiEncryptor]를 주입받으면 개인정보를 다루지 않는 `api-merchant`/`batch`까지
 * AES 키 설정을 갖게 된다. **키가 닿는 앱을 늘리지 않는 것**이 ADR-008이 말하는 "읽는
 * 경로를 좁힌다"의 실제 모습이다.
 *
 * 평문 ↔ 암호문 변환은 `application.customer.PaymentCustomerCrypto`가 맡고, 그 Bean은 실제로
 * 필요한 앱(`api-payment`가 쓰고, `api-admin`이 읽는다)에서만 조립된다.
 */
interface PaymentCustomerRepository {
	/**
	 * 저장한다 — 없으면 INSERT, 있으면 UPDATE다.
	 *
	 * 결제 1건당 1건이라(`uk_payment_customer_payment`) 멱등성 키는 [EncryptedPaymentCustomer.paymentId]다.
	 */
	fun save(customer: EncryptedPaymentCustomer)

	/** 결제에 붙은 구매자 정보를 찾는다. 없으면 `null`(입력 전이거나 파기됐다). */
	fun findByPaymentId(paymentId: PaymentId): EncryptedPaymentCustomer?
}

/**
 * `payment_customer` 한 행을 **암호문 그대로** 표현한다.
 *
 * 도메인 `PaymentCustomer`가 언제나 평문만 아는 것과 정확히 대칭이다 — 이쪽은 평문을 결코
 * 담지 않는다. 항목마다 셋을 갖는 이유(ADR-008):
 *
 * - `*Encrypted` — AES-256-GCM 암호문(값마다 랜덤 IV). 검색·비교에 쓸 수 없다.
 * - `*Masked` — 목록·상세·엑셀이 읽는 값. **쓸 때 함께 저장**해서 읽기 경로가 복호화를
 *   아예 타지 않게 한다.
 * - `*Index` — `HMAC(pepper, 정규화된 값)`. 정확 일치 검색 전용이고 **이름에는 없다.**
 */
data class EncryptedPaymentCustomer(
	val id: PaymentCustomerId,
	val paymentId: PaymentId,
	val nameEncrypted: String,
	val nameMasked: String,
	val emailEncrypted: String,
	val emailMasked: String,
	val emailIndex: String,
	val phoneEncrypted: String,
	val phoneMasked: String,
	val phoneIndex: String,
	val createdAt: Instant,
	val updatedAt: Instant,
)
