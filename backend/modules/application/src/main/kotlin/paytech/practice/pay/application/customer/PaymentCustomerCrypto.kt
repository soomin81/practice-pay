package paytech.practice.pay.application.customer

import paytech.practice.pay.application.port.outbound.EncryptedPaymentCustomer
import paytech.practice.pay.application.port.outbound.PiiBlindIndexer
import paytech.practice.pay.application.port.outbound.PiiEncryptor
import paytech.practice.pay.domain.customer.CustomerEmail
import paytech.practice.pay.domain.customer.CustomerName
import paytech.practice.pay.domain.customer.CustomerPhone
import paytech.practice.pay.domain.customer.PaymentCustomer

/**
 * 도메인 [PaymentCustomer](평문)와 저장 형태인 [EncryptedPaymentCustomer](암호문)를 오가는
 * **application 계층의 유일한 변환 지점**이다.
 *
 * ## 왜 어댑터가 아니라 여기인가
 *
 * 네 앱 모두 `infra.persistence.jooq`를 통째로 컴포넌트 스캔하므로, Repository 어댑터가
 * [PiiEncryptor]를 주입받으면 개인정보를 다루지 않는 `api-merchant`/`batch`까지 AES 키
 * 설정을 갖게 된다. 이 클래스는 Use Case와 함께 각 앱의 `UseCaseConfiguration`에서
 * 조립되므로 **실제로 필요한 앱에만 존재한다**(`api-payment`가 쓰고, `api-admin`이 읽는다).
 *
 * ## [decrypt]를 부르는 곳을 세어 볼 수 있게 유지한다
 *
 * [encrypt]는 쓰기 경로마다 필요하지만 [decrypt]는 **원본 열람 Use Case 하나**만 불러야
 * 한다(ADR-008의 6 — 그리고 그 호출은 감사 기록과 같은 트랜잭션 안에 있어야 한다). 목록·
 * 상세·엑셀은 [EncryptedPaymentCustomer]의 `*Masked`를 그대로 읽으므로 이 클래스를 아예
 * 타지 않는다.
 */
class PaymentCustomerCrypto(
	private val piiEncryptor: PiiEncryptor,
	private val piiBlindIndexer: PiiBlindIndexer,
) {
	/**
	 * 평문 Aggregate를 저장 형태로 바꾼다.
	 *
	 * **마스킹 값은 도메인 VO에서 가져온다** — 여기서 다시 계산하지 않는다. 마스킹 규칙이
	 * 두 곳에 있으면 갈린다(ADR-008의 4).
	 *
	 * Blind Index에는 `normalized`를 넘긴다 — 원문을 그대로 넘기면 `A@b.com`과 `a@b.com`이
	 * 다른 인덱스를 가져 같은 사람이 검색에 걸리지 않는다.
	 */
	fun encrypt(customer: PaymentCustomer): EncryptedPaymentCustomer =
		EncryptedPaymentCustomer(
			id = customer.id,
			paymentId = customer.paymentId,
			nameEncrypted = piiEncryptor.encrypt(customer.name.value),
			nameMasked = customer.name.masked,
			emailEncrypted = piiEncryptor.encrypt(customer.email.value),
			emailMasked = customer.email.masked,
			emailIndex = piiBlindIndexer.index(customer.email.normalized),
			phoneEncrypted = piiEncryptor.encrypt(customer.phone.value),
			phoneMasked = customer.phone.masked,
			phoneIndex = piiBlindIndexer.index(customer.phone.normalized),
			createdAt = customer.createdAt,
			updatedAt = customer.updatedAt,
		)

	/**
	 * 저장 형태를 평문 Aggregate로 되돌린다 — **원본 열람에서만 부른다.**
	 *
	 * 암호문이 변조됐거나 키가 다르면 [PiiEncryptor.decrypt]가 예외로 실패한다(AES-GCM은
	 * 조용히 다른 평문을 내놓지 않는다).
	 */
	fun decrypt(encrypted: EncryptedPaymentCustomer): PaymentCustomer =
		PaymentCustomer.reconstitute(
			id = encrypted.id,
			paymentId = encrypted.paymentId,
			name = CustomerName(piiEncryptor.decrypt(encrypted.nameEncrypted)),
			email = CustomerEmail(piiEncryptor.decrypt(encrypted.emailEncrypted)),
			phone = CustomerPhone(piiEncryptor.decrypt(encrypted.phoneEncrypted)),
			createdAt = encrypted.createdAt,
			updatedAt = encrypted.updatedAt,
		)

	/**
	 * 검색용 Blind Index를 만든다 — 저장할 때 쓴 것과 **같은 계산**이어야 찾을 수 있다.
	 *
	 * `SearchPaymentCustomersUseCase`가 쓴다. 계산을 여기 두는 이유는 [encrypt]와 같다:
	 * `modules:infra-persistence`에 Pepper를 주지 않으려는 것이다 — 검색 Port는 이미 만들어진
	 * 인덱스 문자열만 받아 컬럼을 비교한다.
	 */
	fun emailIndex(email: CustomerEmail): String = piiBlindIndexer.index(email.normalized)

	/** 검색용 Blind Index를 만든다([emailIndex]와 같은 이유). */
	fun phoneIndex(phone: CustomerPhone): String = piiBlindIndexer.index(phone.normalized)
}
