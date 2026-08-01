package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.blockchain.TransactionHash
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentFailureReason
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.payment.PaymentStatus
import paytech.practice.pay.domain.shared.Asset
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.Money
import paytech.practice.pay.domain.shared.TokenAmount
import java.time.Instant

/**
 * 백오피스의 **결제 내역 조회**를 위한 전용 jOOQ Projection Outbound Port다.
 *
 * 애그리게이트 Repository가 아니라 Projection인 이유는 화면에 필요한 값이 `Payment`
 * 하나에 없기 때문이다 — 가맹점 이름(`merchant`)과 Transaction Hash
 * (`blockchain_transaction`)까지 걸쳐 있다(`MerchantListProjection`이 세운 선례,
 * `backend/CLAUDE.md`의 "영속성 레벨의 CQRS").
 *
 * 내부 운영자 콘솔(전 가맹점)과 가맹점 콘솔(자기 것만)이 **같은 Port를 공유**하고,
 * 범위는 [PaymentListQuery.merchantId]로 좁힌다.
 */
fun interface PaymentListProjection {
	fun find(query: PaymentListQuery): PaymentListPage
}

/**
 * 결제 내역 조회 조건이다. 모든 필터는 선택이고, 지정하지 않으면 그 축으로는 좁히지 않는다.
 *
 * @property merchantId 이 가맹점의 결제만. **`null`이면 전 가맹점**이므로, 가맹점 콘솔
 * 경로에서는 절대 `null`이 되면 안 된다 — 그래서 그쪽은 `merchantId`를 필수로 받는
 * 별도 Use Case(`ListMerchantPaymentsUseCase`)를 거친다.
 * @property status 결제 상태.
 * @property createdFrom 생성 시각 하한(이상). 백오피스의 "기간" 필터가 이 축을 쓴다 —
 * 결제가 완료되지 않은 건도 내역에 나와야 해서 `paid_at`이 아니라 `created_at`이 기준이다.
 * @property createdTo 생성 시각 상한(이하).
 * @property page 0부터 시작하는 페이지 번호.
 * @property size 한 페이지 크기. 상한은 호출부(Use Case)가 강제한다.
 */
data class PaymentListQuery(
	val merchantId: MerchantId?,
	val status: PaymentStatus?,
	val createdFrom: Instant?,
	val createdTo: Instant?,
	val page: Int,
	val size: Int,
)

/**
 * 한 페이지의 결과와 **필터 전체에 걸린 총 건수**다. 총 건수를 함께 주는 것은 백오피스가
 * 페이지 이동 UI를 그리려면 필요해서다(기존 목록 Projection들이 `findRecent(limit)`으로
 * 끝나는 것과 다른 점).
 */
data class PaymentListPage(
	val entries: List<PaymentListEntry>,
	val totalCount: Long,
)

/**
 * [PaymentListProjection]이 돌려주는 조회 전용 읽기 모델이다.
 *
 * @property merchantName 조회 결과에 가맹점 이름을 함께 준다 — 내부 운영자 콘솔이
 * 가맹점 식별자만으로는 목록을 읽을 수 없기 때문이다.
 * @property transactionHash 고객이 제출한 온체인 거래 Hash. **제출 전에는 `null`**이다
 * (`blockchain_transaction`을 LEFT JOIN한다). 운영자가 온체인 탐색기와 대조하는 데
 * 쓰는 값이라 목록에 함께 싣는다.
 * @property failureReason `FAILED`일 때만 값이 있다.
 * @property paidAt `SUCCEEDED`일 때만 값이 있다.
 */
data class PaymentListEntry(
	val paymentId: PaymentId,
	val merchantId: MerchantId,
	val merchantName: String,
	val merchantOrderId: MerchantOrderId,
	val orderName: String,
	val orderAmount: Money,
	val paymentAsset: Asset,
	val paymentAmount: TokenAmount,
	val tokenDecimals: Int,
	val network: BlockchainNetwork,
	val status: PaymentStatus,
	val failureReason: PaymentFailureReason?,
	val transactionHash: TransactionHash?,
	val paidAt: Instant?,
	val createdAt: Instant,
)
