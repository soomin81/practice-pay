package paytech.practice.pay.api.merchant.web

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * `GET /merchant/settlement-receivables`의 응답이다
 * (`docs/architecture/merchant-console-api.md`).
 *
 * @property totalNetAmount 필터 전체의 정산 예정 금액 합계. 현재 페이지 합이 아니다 —
 * 이 화면의 핵심 숫자라 목록과 함께 준다. **지급 경로에 살아 있는 것만 더한다**
 * (`PENDING`/`READY`) — 막아 두거나(`HELD`) 끝낸(`CANCELLED`) 돈까지 더하면 실제로 나갈
 * 금액보다 큰 답이 된다.
 * @property heldCount / @property heldNetAmount 그렇게 빠진 돈이다. 합계에서 빼기만 하고
 * 어디로 갔는지 말해주지 않으면 숫자가 달라진 이유를 찾을 수 없다.
 */
data class ListSettlementReceivablesResponse(
	val settlementReceivables: List<SettlementReceivableSummaryResponse>,
	val totalCount: Long,
	val totalNetAmount: Long,
	val heldCount: Long,
	val heldNetAmount: Long,
	val page: Int,
	val size: Int,
)

/**
 * 정산 채권 한 건의 목록용 요약이다.
 *
 * 내부 운영자 콘솔의 같은 이름 응답과 달리 `merchantId`/`merchantName`이 없다 — 이 콘솔은
 * 언제나 자기 가맹점 하나만 본다(결제 목록과 같은 판단).
 *
 * **금액은 전부 숫자로 준다** — KRW 원 단위 정수라 JavaScript `Number`의 안전 정수 범위를
 * 넘지 않는다(토큰 금액을 문자열로 주는 것과 다른 점).
 *
 * @property exchangeReceivedAmount 환전으로 확보한 KRW. `READY` 전에는 `null`이다.
 * @property exchangeProfitLossAmount 확보액과 정산 기준 금액의 차이(PG 마진). 음수일 수 있다.
 * @property holdReasonCode 정산이 막힌 이유. `HELD`가 아니면 `null`이다.
 *
 * **가맹점에게도 보여준다** — 자기 돈이 멈춘 이유를 모르면 결국 문의로 돌아온다. 다만 이
 * 콘솔에는 **푸는 수단이 없다**(보류·해제·취소는 전부 내부 운영자의 SUPER_ADMIN 전용이고
 * 이력 조회도 admin에만 있다).
 */
data class SettlementReceivableSummaryResponse(
	val settlementReceivableId: String,
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
