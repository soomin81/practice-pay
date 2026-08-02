package paytech.practice.pay.api.admin.web

import paytech.practice.pay.application.port.outbound.PaymentDetailView

/**
 * 읽기 모델을 응답 DTO로 옮긴다. 컨트롤러 본문에 두면 매핑이 길어 인가·오류 처리 같은
 * 실제 의도가 묻혀서 파일을 나눴다.
 *
 * 토큰 금액(Minor Unit)만 문자열로 바꾼다 — 나머지는 그대로다.
 */
fun toResponse(view: PaymentDetailView): PaymentDetailResponse =
	PaymentDetailResponse(
		payment =
			PaymentDetailPaymentResponse(
				paymentId = view.payment.paymentId.value,
				merchantId = view.payment.merchantId.value,
				merchantName = view.payment.merchantName,
				merchantOrderId = view.payment.merchantOrderId.value,
				orderName = view.payment.orderName,
				orderAmount = view.payment.orderAmount,
				orderCurrency = view.payment.orderCurrency,
				paymentAsset = view.payment.paymentAsset,
				paymentAmount = view.payment.paymentAmountMinor.toString(),
				tokenDecimals = view.payment.tokenDecimals,
				network = view.payment.network,
				receivingWallet = view.payment.receivingWallet,
				customerWallet = view.payment.customerWallet,
				status = view.payment.status.name,
				failureReason = view.payment.failureReason?.name,
				expiresAt = view.payment.expiresAt,
				paidAt = view.payment.paidAt,
				createdAt = view.payment.createdAt,
			),
		quote =
			PaymentDetailQuoteResponse(
				marketProviderCode = view.quote.marketProviderCode,
				marketRate = view.quote.marketRate,
				appliedRate = view.quote.appliedRate,
				spreadRate = view.quote.spreadRate,
				quotedAt = view.quote.quotedAt,
				expiresAt = view.quote.expiresAt,
			),
		checkoutSession =
			PaymentDetailCheckoutSessionResponse(
				checkoutSessionId = view.checkoutSession.checkoutSessionId,
				status = view.checkoutSession.status.name,
				connectedWallet = view.checkoutSession.connectedWallet,
				expiresAt = view.checkoutSession.expiresAt,
			),
		blockchainTransaction =
			view.blockchainTransaction?.let {
				PaymentDetailBlockchainTransactionResponse(
					blockchainTransactionId = it.blockchainTransactionId,
					transactionHash = it.transactionHash.value,
					status = it.status.name,
					blockNumber = it.blockNumber,
					confirmationCount = it.confirmationCount,
					requiredConfirmationCount = it.requiredConfirmationCount,
					fromAddress = it.fromAddress,
					toAddress = it.toAddress,
					tokenContractAddress = it.tokenContractAddress,
					amountMinor = it.amountMinor?.toString(),
					failureCode = it.failureCode,
					submittedAt = it.submittedAt,
					detectedAt = it.detectedAt,
					confirmedAt = it.confirmedAt,
				)
			},
		exchangeOrder =
			view.exchangeOrder?.let {
				PaymentDetailExchangeOrderResponse(
					exchangeOrderId = it.exchangeOrderId,
					providerCode = it.providerCode,
					status = it.status.name,
					executedAmount = it.executedAmountMinor?.toString(),
					averageExecutionRate = it.averageExecutionRate,
					receivedAmount = it.receivedAmount,
					feeAmount = it.feeAmount,
					completedAt = it.completedAt,
				)
			},
		settlementReceivable =
			view.settlementReceivable?.let {
				PaymentDetailSettlementResponse(
					settlementReceivableId = it.settlementReceivableId,
					status = it.status.name,
					grossAmount = it.grossAmount,
					feeRate = it.feeRate,
					feeAmount = it.feeAmount,
					adjustmentAmount = it.adjustmentAmount,
					netAmount = it.netAmount,
					exchangeProfitLossAmount = it.exchangeProfitLossAmount,
					eligibleDate = it.eligibleDate,
				)
			},
		webhookDeliveries =
			view.webhookDeliveries.map {
				PaymentDetailWebhookDeliveryResponse(
					webhookDeliveryId = it.webhookDeliveryId,
					eventType = it.eventType,
					destinationUrl = it.destinationUrl,
					status = it.status.name,
					attemptCount = it.attemptCount,
					lastHttpStatus = it.lastHttpStatus,
					lastErrorMessage = it.lastErrorMessage,
					nextRetryAt = it.nextRetryAt,
					deliveredAt = it.deliveredAt,
					createdAt = it.createdAt,
				)
			},
	)
