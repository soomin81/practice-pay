package paytech.practice.pay.application.settlement

import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import java.time.LocalDate

/**
 * 정산 채권 조회 조건이다. 내부 운영자 콘솔([ListSettlementReceivablesUseCase])과 가맹점
 * 콘솔([ListMerchantSettlementReceivablesUseCase])이 함께 쓴다.
 *
 * **[merchantId]는 "필터"이지 "권한"이 아니다** — 가맹점 콘솔에서 자기 가맹점으로 좁히는
 * 책임은 이 Command가 아니라 [ListMerchantSettlementReceivablesUseCase]가 진다.
 *
 * 기간이 `LocalDate`인 이유는 [paytech.practice.pay.application.port.outbound.SettlementReceivableListQuery]의
 * KDoc 참고 — 정산은 "언제 정산되나"가 질문이라 정산 예정일이 축이다.
 */
data class ListSettlementReceivablesCommand(
	val merchantId: MerchantId? = null,
	val status: SettlementReceivableStatus? = null,
	val eligibleFrom: LocalDate? = null,
	val eligibleTo: LocalDate? = null,
	val page: Int = 0,
	val size: Int = SettlementListPaging.DEFAULT_PAGE_SIZE,
)
