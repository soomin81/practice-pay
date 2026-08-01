package paytech.practice.pay.application.payment

import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.PaymentStatus
import java.time.Instant

/**
 * 결제 내역 조회 조건이다. 내부 운영자 콘솔([ListPaymentsUseCase])과 가맹점 콘솔
 * ([ListMerchantPaymentsUseCase])이 함께 쓴다.
 *
 * **[merchantId]는 "필터"이지 "권한"이 아니다** — 가맹점 콘솔에서 자기 가맹점으로 좁히는
 * 책임은 이 Command가 아니라 [ListMerchantPaymentsUseCase]가 진다(그쪽은 `merchantId`를
 * 필수 인자로 따로 받는다). 여기서 `null`은 "전 가맹점"을 뜻한다.
 *
 * @property page 0부터 시작한다.
 * @property size 한 페이지 크기. [PaymentListPaging]의 상한으로 잘린다.
 */
data class ListPaymentsCommand(
	val merchantId: MerchantId? = null,
	val status: PaymentStatus? = null,
	val createdFrom: Instant? = null,
	val createdTo: Instant? = null,
	val page: Int = 0,
	val size: Int = PaymentListPaging.DEFAULT_PAGE_SIZE,
)
