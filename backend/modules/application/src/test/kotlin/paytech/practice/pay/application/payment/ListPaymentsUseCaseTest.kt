package paytech.practice.pay.application.payment

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import paytech.practice.pay.application.port.outbound.PaymentListPage
import paytech.practice.pay.application.port.outbound.PaymentListProjection
import paytech.practice.pay.application.port.outbound.PaymentListQuery
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.payment.PaymentStatus

private val MERCHANT_ID = MerchantId("mrc_test_001")

private fun projectionReturningEmpty(querySlot: CapturingSlot<PaymentListQuery>): PaymentListProjection {
	val projection = mockk<PaymentListProjection>()
	every { projection.find(capture(querySlot)) } returns PaymentListPage(entries = emptyList(), totalCount = 0L)
	return projection
}

class ListPaymentsUseCaseTest :
	FunSpec({

		test("passes the filters through and reports the applied page and size") {
			val querySlot = slot<PaymentListQuery>()
			val projection = projectionReturningEmpty(querySlot)

			val result =
				ListPaymentsUseCase(projection).execute(
					ListPaymentsCommand(
						merchantId = MERCHANT_ID,
						status = PaymentStatus.SUCCEEDED,
						page = 2,
						size = 25,
					),
				)

			querySlot.captured.merchantId shouldBe MERCHANT_ID
			querySlot.captured.status shouldBe PaymentStatus.SUCCEEDED
			querySlot.captured.page shouldBe 2
			querySlot.captured.size shouldBe 25
			result.page shouldBe 2
			result.size shouldBe 25
		}

		// 클라이언트가 정하는 페이지 크기를 그대로 믿으면 조회 하나로 DB와 응답 직렬화를
		// 모두 밀어버릴 수 있다. 잘린 값을 결과에도 실어 호출부가 알 수 있게 한다.
		test("caps an oversized page size and floors a negative page") {
			val querySlot = slot<PaymentListQuery>()
			val projection = projectionReturningEmpty(querySlot)

			val result = ListPaymentsUseCase(projection).execute(ListPaymentsCommand(page = -3, size = 10_000))

			querySlot.captured.page shouldBe 0
			querySlot.captured.size shouldBe PaymentListPaging.MAX_PAGE_SIZE
			result.size shouldBe PaymentListPaging.MAX_PAGE_SIZE
		}

		test("a null merchantId means every merchant") {
			val querySlot = slot<PaymentListQuery>()
			val projection = projectionReturningEmpty(querySlot)

			ListPaymentsUseCase(projection).execute(ListPaymentsCommand())

			querySlot.captured.merchantId shouldBe null
		}
	})

class ListMerchantPaymentsUseCaseTest :
	FunSpec({

		/**
		 * **이 Use Case가 따로 있는 이유 전체가 이 테스트다** — 호출부가 Command에 다른
		 * 가맹점을 넣거나 아예 비워도, 조회 범위는 인자로 받은 가맹점이어야 한다.
		 */
		test("always scopes to the given merchant, ignoring the merchantId in the command") {
			val querySlot = slot<PaymentListQuery>()
			val projection = projectionReturningEmpty(querySlot)

			ListMerchantPaymentsUseCase(projection).execute(
				merchantId = MERCHANT_ID,
				command = ListPaymentsCommand(merchantId = MerchantId("mrc_someone_else")),
			)

			querySlot.captured.merchantId shouldBe MERCHANT_ID
		}

		test("scopes to the given merchant even when the command carries no merchantId") {
			val querySlot = slot<PaymentListQuery>()
			val projection = projectionReturningEmpty(querySlot)

			ListMerchantPaymentsUseCase(projection).execute(merchantId = MERCHANT_ID, command = ListPaymentsCommand())

			querySlot.captured.merchantId shouldBe MERCHANT_ID
		}

		test("applies the same paging limits as the admin use case") {
			val querySlot = slot<PaymentListQuery>()
			val projection = projectionReturningEmpty(querySlot)

			ListMerchantPaymentsUseCase(projection).execute(
				merchantId = MERCHANT_ID,
				command = ListPaymentsCommand(page = -1, size = 10_000),
			)

			querySlot.captured.page shouldBe 0
			querySlot.captured.size shouldBe PaymentListPaging.MAX_PAGE_SIZE
		}
	})
