package paytech.practice.pay.application.payment

import paytech.practice.pay.application.port.outbound.OnChainTransaction
import paytech.practice.pay.domain.blockchain.BlockchainTransaction
import paytech.practice.pay.domain.blockchain.ContractAddress
import paytech.practice.pay.domain.payment.Payment
import paytech.practice.pay.domain.payment.PaymentFailureReason

/**
 * `Payment.CONFIRMING → SUCCEEDED`로 전이해도 되는지 검증하는 도메인 서비스다
 * (`docs/domain/domain-model.md`의 "Domain Service" 절: "Network, Chain ID,
 * Contract, Wallet, Amount, Receipt, Confirm, 중복 여부를 검증한다").
 *
 * **`modules:domain`이 아니라 `modules:application`에 둔 이유**: 개념적으로는
 * 순수한 검증 로직(부수효과 없음, 자기 자신은 어떤 I/O도 하지 않음)이라 도메인
 * 서비스가 맞지만, 검증 대상인 [OnChainTransaction]이 [paytech.practice.pay.application.port.outbound.BlockchainClient]
 * Port의 반환 타입이라 `modules:application`에 있다. 의존 방향은 `application →
 * domain`으로만 흐를 수 있어서(`modules:domain`은 `modules:application`에 의존할
 * 수 없다), 이 타입에 의존하는 순간 `modules:domain`에는 둘 수 없다. 도메인
 * 순수성 원칙(부수효과 없는 순수 함수, Spring/jOOQ 미의존)은 그대로 지키되
 * 물리적 위치만 옮긴 것이다.
 *
 * 검증하지 않는 것 두 가지:
 * - **Confirm 충족 여부** — 부족하다고 해서 결제가 실패하는 게 아니라 다음 폴링을
 *   기다리면 되는 정상적인 대기 상태라, "Invalid"가 아니라 호출부
 *   ([ConfirmBlockchainTransactionUseCase])가 `confirmationCount`를 직접 비교해서
 *   처리한다.
 * - **중복 Transaction Hash 여부** — `blockchain_transaction`의
 *   `uk_blockchain_network_hash` Unique 제약이 그 BlockchainTransaction이
 *   생성되던 시점에 이미 보장했다(그 생성 Use Case는 이 Use Case의 범위 밖이다).
 *   여기서 다시 확인할 근거 데이터가 없다.
 *
 * **`Invalid`가 "돈이 오지 않았다"를 뜻하지 않는다.** 온체인 전송은 되돌릴 수 없어서, 여기서
 * 실패로 판정해도 자금은 이미 움직인 뒤다 — 특히 `AMOUNT_INSUFFICIENT`(금액 부족)와
 * `TOKEN_CONTRACT_NOT_ALLOWED`(허용되지 않은 토큰)는 **자금이 PG 수취 지갑에 들어온 상태**다.
 * MVP는 이런 입금을 자동 반환하거나 정산에 반영하지 않고 수령 사실만 `blockchain_transaction`에
 * 남긴다(ADR-007). 금액 비교가 `>=`인 것도 같은 맥락이다 — 초과 지급은 결제를 막지 않고,
 * 초과분은 정산(견적 금액 기준)에 반영되지 않은 채 수취 지갑에 남는다.
 */
object PaymentTransactionValidator {
	fun validate(
		payment: Payment,
		blockchainTransaction: BlockchainTransaction,
		onChainTransaction: OnChainTransaction,
		expectedTokenContractAddress: ContractAddress,
	): PaymentTransactionValidationResult {
		if (!onChainTransaction.receiptSucceeded) {
			return PaymentTransactionValidationResult.Invalid(PaymentFailureReason.TRANSACTION_RECEIPT_FAILED)
		}
		if (onChainTransaction.chainId != blockchainTransaction.chainId) {
			return PaymentTransactionValidationResult.Invalid(PaymentFailureReason.NETWORK_MISMATCH)
		}

		val onContract =
			onChainTransaction.tokenTransfers.filter {
				it.contractAddress.value.equals(expectedTokenContractAddress.value, ignoreCase = true)
			}
		if (onContract.isEmpty()) {
			return PaymentTransactionValidationResult.Invalid(PaymentFailureReason.TOKEN_CONTRACT_NOT_ALLOWED)
		}

		val toExpectedWallet =
			onContract.filter {
				it.to.value.equals(payment.receivingWallet.value, ignoreCase = true)
			}
		if (toExpectedWallet.isEmpty()) {
			return PaymentTransactionValidationResult.Invalid(PaymentFailureReason.RECEIVING_WALLET_MISMATCH)
		}

		val sufficientAmount = toExpectedWallet.any { it.amount >= payment.paymentAmount }
		if (!sufficientAmount) {
			return PaymentTransactionValidationResult.Invalid(PaymentFailureReason.AMOUNT_INSUFFICIENT)
		}

		return PaymentTransactionValidationResult.Valid
	}
}

/** [PaymentTransactionValidator.validate]의 결과다. */
sealed interface PaymentTransactionValidationResult {
	data object Valid : PaymentTransactionValidationResult

	data class Invalid(
		val reason: PaymentFailureReason,
	) : PaymentTransactionValidationResult
}
