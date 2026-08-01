package paytech.practice.pay.application.port.outbound

import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.MerchantOrderId
import paytech.practice.pay.domain.payment.PaymentId
import paytech.practice.pay.domain.settlement.SettlementReceivableId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * 백오피스의 **정산 채권 조회**를 위한 전용 jOOQ Projection Outbound Port다
 * ([PaymentListProjection]의 정산판 — 같은 이유·같은 모양).
 *
 * 화면에 필요한 값이 `SettlementReceivable` 하나에 없다 — 가맹점 이름(`merchant`)과
 * 주문 식별자(`payment`)까지 걸쳐 있어서 애그리게이트 Repository가 아니라 Projection이다.
 */
fun interface SettlementReceivableListProjection {
	fun find(query: SettlementReceivableListQuery): SettlementReceivableListPage
}

/**
 * 정산 채권 조회 조건이다.
 *
 * **기간 필터가 `eligibleDate`(정산 예정일) 기준이다** — 결제 내역이 `created_at`을 쓰는
 * 것과 다르다. 정산에서 사람이 묻는 질문은 "언제 만들어졌나"가 아니라 "언제 정산되나"라서,
 * 화면의 기간도 그 축이어야 한다. 날짜(`LocalDate`)라 시각·시간대 경계 문제가 아예 없다.
 *
 * @property merchantId 이 가맹점의 채권만. **`null`이면 전 가맹점**이므로 가맹점 콘솔
 * 경로에서는 절대 `null`이 되면 안 된다 — 그래서 그쪽은 `merchantId`를 필수로 받는 별도
 * Use Case를 거친다.
 */
data class SettlementReceivableListQuery(
	val merchantId: MerchantId?,
	val status: SettlementReceivableStatus?,
	val eligibleFrom: LocalDate?,
	val eligibleTo: LocalDate?,
	val page: Int,
	val size: Int,
)

/**
 * 한 페이지의 결과와 **필터 전체에 걸린 합계**다.
 *
 * [totalNetAmount]를 함께 주는 것이 이 화면의 핵심이다 — 정산 화면에서 사람이 가장 먼저
 * 묻는 것은 "그래서 얼마를 받나"이고, 현재 페이지의 합만 보여주면 그 질문에 답할 수 없다.
 */
data class SettlementReceivableListPage(
	val entries: List<SettlementReceivableListEntry>,
	val totalCount: Long,
	val totalNetAmount: Long,
)

/**
 * [SettlementReceivableListProjection]이 돌려주는 조회 전용 읽기 모델이다.
 *
 * 금액은 전부 KRW 원 단위 정수다(`Money`) — 토큰 금액과 달리 JavaScript `Number`의 안전
 * 정수 범위를 넘지 않아 응답에서도 숫자로 내보낸다.
 *
 * @property netAmount 실제로 정산될 금액. `gross - fee + adjustment`이며 그 공식은
 * `SettlementReceivable`이 `require`로 직접 검증한다.
 * @property exchangeReceivedAmount 환전으로 확보한 KRW. `READY` 전에는 `null`이다.
 * @property exchangeProfitLossAmount 확보액과 정산 기준 금액의 차이(PG 마진). 음수일 수 있다.
 */
data class SettlementReceivableListEntry(
	val settlementReceivableId: SettlementReceivableId,
	val merchantId: MerchantId,
	val merchantName: String,
	val paymentId: PaymentId,
	val merchantOrderId: MerchantOrderId,
	val status: SettlementReceivableStatus,
	val settlementCurrency: String,
	val grossAmount: Long,
	val feeRate: BigDecimal,
	val feeAmount: Long,
	val adjustmentAmount: Long,
	val netAmount: Long,
	val exchangeReceivedAmount: Long?,
	val exchangeProfitLossAmount: Long?,
	val eligibleDate: LocalDate,
	val createdAt: Instant,
)
