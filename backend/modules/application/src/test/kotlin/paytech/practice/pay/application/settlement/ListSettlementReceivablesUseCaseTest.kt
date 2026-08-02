package paytech.practice.pay.application.settlement

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import paytech.practice.pay.application.port.outbound.SettlementReceivableListPage
import paytech.practice.pay.application.port.outbound.SettlementReceivableListProjection
import paytech.practice.pay.application.port.outbound.SettlementReceivableListQuery
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.settlement.SettlementReceivableStatus
import java.time.LocalDate

private val MERCHANT_ID = MerchantId("mrc_test_001")

private fun projection(
	querySlot: CapturingSlot<SettlementReceivableListQuery>,
	totalNetAmount: Long = 0L,
): SettlementReceivableListProjection {
	val projection = mockk<SettlementReceivableListProjection>()
	every { projection.find(capture(querySlot)) } returns
		SettlementReceivableListPage(
			entries = emptyList(),
			totalCount = 0L,
			totalNetAmount = totalNetAmount,
			heldCount = 0L,
			heldNetAmount = 0L,
		)
	return projection
}

class ListSettlementReceivablesUseCaseTest :
	FunSpec({

		test("passes the filters through and reports the applied paging") {
			val querySlot = slot<SettlementReceivableListQuery>()

			val result =
				ListSettlementReceivablesUseCase(projection(querySlot)).execute(
					ListSettlementReceivablesCommand(
						merchantId = MERCHANT_ID,
						status = SettlementReceivableStatus.READY,
						eligibleFrom = LocalDate.parse("2026-08-01"),
						eligibleTo = LocalDate.parse("2026-08-31"),
						page = 2,
						size = 25,
					),
				)

			querySlot.captured.merchantId shouldBe MERCHANT_ID
			querySlot.captured.status shouldBe SettlementReceivableStatus.READY
			querySlot.captured.eligibleFrom shouldBe LocalDate.parse("2026-08-01")
			querySlot.captured.eligibleTo shouldBe LocalDate.parse("2026-08-31")
			result.page shouldBe 2
			result.size shouldBe 25
		}

		test("caps an oversized page size and floors a negative page") {
			val querySlot = slot<SettlementReceivableListQuery>()

			val result =
				ListSettlementReceivablesUseCase(projection(querySlot))
					.execute(ListSettlementReceivablesCommand(page = -3, size = 10_000))

			querySlot.captured.page shouldBe 0
			querySlot.captured.size shouldBe SettlementListPaging.MAX_PAGE_SIZE
			result.size shouldBe SettlementListPaging.MAX_PAGE_SIZE
		}

		// "그래서 얼마를 받나"가 이 화면의 질문이라 합계를 그대로 실어 보낸다.
		test("passes the total net amount through") {
			val querySlot = slot<SettlementReceivableListQuery>()

			val result =
				ListSettlementReceivablesUseCase(projection(querySlot, totalNetAmount = 59_100))
					.execute(ListSettlementReceivablesCommand())

			result.totalNetAmount shouldBe 59_100L
		}

		test("a null merchantId means every merchant") {
			val querySlot = slot<SettlementReceivableListQuery>()

			ListSettlementReceivablesUseCase(projection(querySlot)).execute(ListSettlementReceivablesCommand())

			querySlot.captured.merchantId shouldBe null
		}
	})

class ListMerchantSettlementReceivablesUseCaseTest :
	FunSpec({

		/**
		 * **정산은 결제보다 민감도가 한 단계 높다** — 범위가 새면 남의 매출과 수취 예정
		 * 금액이 드러난다. Command에 다른 가맹점이 실려 와도 인자로 받은 가맹점이 이긴다.
		 */
		test("always scopes to the given merchant, ignoring the merchantId in the command") {
			val querySlot = slot<SettlementReceivableListQuery>()

			ListMerchantSettlementReceivablesUseCase(projection(querySlot)).execute(
				merchantId = MERCHANT_ID,
				command = ListSettlementReceivablesCommand(merchantId = MerchantId("mrc_someone_else")),
			)

			querySlot.captured.merchantId shouldBe MERCHANT_ID
		}

		test("scopes to the given merchant even when the command carries no merchantId") {
			val querySlot = slot<SettlementReceivableListQuery>()

			ListMerchantSettlementReceivablesUseCase(projection(querySlot))
				.execute(merchantId = MERCHANT_ID, command = ListSettlementReceivablesCommand())

			querySlot.captured.merchantId shouldBe MERCHANT_ID
		}

		test("applies the same paging limits as the admin use case") {
			val querySlot = slot<SettlementReceivableListQuery>()

			ListMerchantSettlementReceivablesUseCase(projection(querySlot))
				.execute(merchantId = MERCHANT_ID, command = ListSettlementReceivablesCommand(page = -1, size = 10_000))

			querySlot.captured.page shouldBe 0
			querySlot.captured.size shouldBe SettlementListPaging.MAX_PAGE_SIZE
		}
	})
