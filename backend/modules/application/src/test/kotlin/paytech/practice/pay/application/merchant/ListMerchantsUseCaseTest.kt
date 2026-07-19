package paytech.practice.pay.application.merchant

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import paytech.practice.pay.application.port.outbound.MerchantListProjection
import paytech.practice.pay.application.port.outbound.MerchantSummary
import paytech.practice.pay.domain.merchant.MerchantCode
import paytech.practice.pay.domain.merchant.MerchantId
import paytech.practice.pay.domain.merchant.MerchantStatus
import java.time.Instant

class ListMerchantsUseCaseTest :
	FunSpec({

		test("returns whatever the projection returns, unchanged") {
			val summaries =
				listOf(
					MerchantSummary(
						merchantId = MerchantId("mrc_001"),
						merchantCode = MerchantCode("MERCHANT_ONE"),
						merchantName = "First Merchant",
						status = MerchantStatus.ACTIVE,
						createdAt = Instant.parse("2026-07-19T00:00:00Z"),
					),
					MerchantSummary(
						merchantId = MerchantId("mrc_002"),
						merchantCode = MerchantCode("MERCHANT_TWO"),
						merchantName = "Second Merchant",
						status = MerchantStatus.SUSPENDED,
						createdAt = Instant.parse("2026-07-18T00:00:00Z"),
					),
				)
			val merchantListProjection = mockk<MerchantListProjection>()
			every { merchantListProjection.findAll() } returns summaries

			val result = ListMerchantsUseCase(merchantListProjection).execute()

			result.merchants shouldBe summaries
		}

		test("returns an empty list when there are no merchants") {
			val merchantListProjection = mockk<MerchantListProjection>()
			every { merchantListProjection.findAll() } returns emptyList()

			val result = ListMerchantsUseCase(merchantListProjection).execute()

			result.merchants shouldBe emptyList()
		}
	})
