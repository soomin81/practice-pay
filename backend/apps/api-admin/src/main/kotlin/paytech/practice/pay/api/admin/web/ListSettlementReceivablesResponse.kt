package paytech.practice.pay.api.admin.web

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * `GET /admin/settlement-receivables`의 응답이다
 * (`docs/architecture/admin-console-api.md`).
 *
 * @property totalNetAmount 필터 전체의 정산 예정 금액 합계. 현재 페이지 합이 아니다 —
 * 이 화면의 핵심 숫자라 목록과 함께 준다.
 */
data class ListSettlementReceivablesResponse(
	val settlementReceivables: List<SettlementReceivableSummaryResponse>,
	val totalCount: Long,
	val totalNetAmount: Long,
	val page: Int,
	val size: Int,
)

/**
 * 정산 채권 한 건의 목록용 요약이다.
 *
 * **금액은 전부 숫자로 준다** — KRW 원 단위 정수라 JavaScript `Number`의 안전 정수 범위를
 * 넘지 않는다(토큰 금액을 문자열로 주는 것과 다른 점).
 *
 * @property exchangeReceivedAmount 환전으로 확보한 KRW. `READY` 전에는 `null`이다.
 * @property exchangeProfitLossAmount 확보액과 정산 기준 금액의 차이(PG 마진). 음수일 수 있다.
 * @property holdReasonCode **지금 왜 막혀 있나.** `HELD`가 아니면 `null`이다 — 화면이 "보류"만
 * 보여주고 이유를 감추면 운영자가 풀어도 되는지 판단할 수 없다. 막혔던 *이력*은 이 값이 아니라
 * `GET /admin/settlement-receivables/{id}/hold-history`에 있다(해제하면 여기는 지워진다).
 */
data class SettlementReceivableSummaryResponse(
	val settlementReceivableId: String,
	val merchantId: String,
	val merchantName: String,
	val paymentId: String,
	val merchantOrderId: String,
	val status: String,
	val settlementCurrency: String,
	val grossAmount: Long,
	val feeRate: BigDecimal,
	val feeAmount: Long,
	val adjustmentAmount: Long,
	val netAmount: Long,
	val exchangeReceivedAmount: Long?,
	val exchangeProfitLossAmount: Long?,
	val eligibleDate: LocalDate,
	val holdReasonCode: String?,
	val createdAt: Instant,
)
