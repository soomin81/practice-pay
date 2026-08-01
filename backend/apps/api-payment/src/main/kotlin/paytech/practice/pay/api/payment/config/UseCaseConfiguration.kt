package paytech.practice.pay.api.payment.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import paytech.practice.pay.application.apikey.AuthenticateApiKeyUseCase
import paytech.practice.pay.application.checkout.CancelCheckoutSessionUseCase
import paytech.practice.pay.application.checkout.ConnectCheckoutWalletUseCase
import paytech.practice.pay.application.checkout.GetCheckoutSessionUseCase
import paytech.practice.pay.application.checkout.GetCheckoutStatusUseCase
import paytech.practice.pay.application.payment.CreatePaymentUseCase
import paytech.practice.pay.application.payment.ReceivingWalletRegistry
import paytech.practice.pay.application.payment.SubmitPaymentTransactionUseCase
import paytech.practice.pay.application.port.outbound.ApiKeySecretHasher
import paytech.practice.pay.application.port.outbound.BlockchainTransactionRepository
import paytech.practice.pay.application.port.outbound.CheckoutSessionRepository
import paytech.practice.pay.application.port.outbound.CheckoutViewProjection
import paytech.practice.pay.application.port.outbound.ExchangeRateProvider
import paytech.practice.pay.application.port.outbound.IdGenerator
import paytech.practice.pay.application.port.outbound.MerchantApiKeyRepository
import paytech.practice.pay.application.port.outbound.MerchantRepository
import paytech.practice.pay.application.port.outbound.OutboxEventRepository
import paytech.practice.pay.application.port.outbound.PaymentQuoteRepository
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.application.port.outbound.WalletAddressChecksum
import paytech.practice.pay.domain.shared.BlockchainNetwork
import paytech.practice.pay.domain.shared.WalletAddress
import java.time.Clock

/**
 * `modules:application`의 Use Case를 Spring Bean으로 조립하는 Composition Root다.
 *
 * `CreatePaymentUseCase`는 `modules:application`에 있고 그 모듈은 Spring에 의존하지
 * 않으므로(도메인 순수성 규칙이 application 계층까지 이어짐 — `backend/CLAUDE.md`
 * 참고) `@Component`를 직접 붙일 수 없다. 이 앱(inbound adapter) 쪽에서 `@Bean`
 * 메서드로 필요한 outbound port Bean들을 주입받아 대신 조립한다.
 */
@Configuration
class UseCaseConfiguration {
	@Bean
	fun clock(): Clock = Clock.systemUTC()

	@Bean
	fun authenticateApiKeyUseCase(
		merchantApiKeyRepository: MerchantApiKeyRepository,
		merchantRepository: MerchantRepository,
		apiKeySecretHasher: ApiKeySecretHasher,
		transactionManager: TransactionManager,
		clock: Clock,
	): AuthenticateApiKeyUseCase =
		AuthenticateApiKeyUseCase(
			merchantApiKeyRepository = merchantApiKeyRepository,
			merchantRepository = merchantRepository,
			apiKeySecretHasher = apiKeySecretHasher,
			transactionManager = transactionManager,
			clock = clock,
		)

	/**
	 * 네트워크별 PG 수취 지갑을 설정에서 읽어 조립한다.
	 *
	 * **값이 비어 있으면 그 네트워크를 등록하지 않는다** — 그럴듯한 기본값을 둘 수 없는
	 * 종류의 설정이라서다. 실제 테스트넷 USDC가 그 주소로 전송되므로, 저장소에 적어 둔
	 * 주소가 기본값으로 조용히 쓰이는 것보다 결제 생성이 503으로 실패하는 편이 낫다.
	 * 그래서 설정이 없어도 앱은 정상 기동한다(`backend/CLAUDE.md`의 "환경변수 없이도
	 * bootRun이 동작한다"를 지키면서, 실패는 실제로 결제를 만들 때 드러나게 한다).
	 */
	@Bean
	fun receivingWalletRegistry(
		walletAddressChecksum: WalletAddressChecksum,
		@Value("\${app.payment.receiving-wallets.base-sepolia:}") baseSepoliaWallet: String,
	): ReceivingWalletRegistry =
		ReceivingWalletRegistry(
			buildMap {
				if (baseSepoliaWallet.isNotBlank()) {
					put(
						BlockchainNetwork.BASE_SEPOLIA,
						checksummedReceivingWallet(walletAddressChecksum, baseSepoliaWallet, "base-sepolia"),
					)
				}
			},
		)

	/**
	 * 설정된 수취 지갑이 **EIP-55 체크섬 형태인지 확인하고, 아니면 기동을 실패시킨다.**
	 *
	 * 이 값은 사람이 손으로 옮겨 적는 주소이고, 틀리면 **고객이 보낸 USDC가 아무도 통제하지
	 * 못하는 주소로 가서 되돌릴 수 없다.** 그런데 형식 검증(`0x` + 40 hex)은 한 글자 오타를
	 * 잡지 못한다 — EIP-55는 정확히 그 오타를 잡으려고 대소문자에 체크섬을 실은 규약이다.
	 *
	 * **틀렸을 때 정규 형태를 메시지에 찍지 않는다.** 찍으면 운영자가 그 값을 그대로
	 * 복사해 넣게 되는데, 오타가 있었다면 그건 "체크섬만 맞는 남의 주소"라 오히려 오타를
	 * 확정시킨다. 지갑에서 다시 복사하라고 안내하는 것이 유일하게 옳은 대응이다.
	 *
	 * 소문자로만 적힌 주소도 거부한다 — 체크섬 정보가 없어 오타를 검증할 수 없기 때문이다.
	 * 지갑은 언제나 체크섬 형태로 보여주므로 복사해 넣으면 그대로 통과한다.
	 */
	private fun checksummedReceivingWallet(
		checksum: WalletAddressChecksum,
		configured: String,
		networkKey: String,
	): WalletAddress {
		val address = WalletAddress(configured)
		check(checksum.isChecksummed(configured)) {
			"app.payment.receiving-wallets.$networkKey 주소의 EIP-55 체크섬이 맞지 않습니다. " +
				"오타이거나 체크섬 없이 적힌 주소입니다 — 지갑에서 주소를 다시 복사해 넣으세요."
		}
		return address
	}

	@Bean
	fun createPaymentUseCase(
		merchantRepository: MerchantRepository,
		paymentRepository: PaymentRepository,
		paymentQuoteRepository: PaymentQuoteRepository,
		checkoutSessionRepository: CheckoutSessionRepository,
		outboxEventRepository: OutboxEventRepository,
		exchangeRateProvider: ExchangeRateProvider,
		receivingWalletRegistry: ReceivingWalletRegistry,
		idGenerator: IdGenerator,
		transactionManager: TransactionManager,
		clock: Clock,
	): CreatePaymentUseCase =
		CreatePaymentUseCase(
			merchantRepository = merchantRepository,
			paymentRepository = paymentRepository,
			paymentQuoteRepository = paymentQuoteRepository,
			checkoutSessionRepository = checkoutSessionRepository,
			outboxEventRepository = outboxEventRepository,
			exchangeRateProvider = exchangeRateProvider,
			receivingWalletRegistry = receivingWalletRegistry,
			idGenerator = idGenerator,
			transactionManager = transactionManager,
			clock = clock,
		)

	// ── 고객 대면 체크아웃(docs/architecture/checkout-api.md) ─────────────────
	// ConnectCheckoutWalletUseCase와 SubmitPaymentTransactionUseCase는 이전부터
	// modules:application에 구현돼 있었지만 어떤 앱도 배선하지 않아 호출된 적이
	// 없었다 — 이 앱이 그 둘을 처음 노출하는 자리다.

	@Bean
	fun getCheckoutSessionUseCase(checkoutViewProjection: CheckoutViewProjection): GetCheckoutSessionUseCase =
		GetCheckoutSessionUseCase(checkoutViewProjection = checkoutViewProjection)

	@Bean
	fun getCheckoutStatusUseCase(checkoutViewProjection: CheckoutViewProjection): GetCheckoutStatusUseCase =
		GetCheckoutStatusUseCase(checkoutViewProjection = checkoutViewProjection)

	@Bean
	fun connectCheckoutWalletUseCase(
		checkoutSessionRepository: CheckoutSessionRepository,
		transactionManager: TransactionManager,
		clock: Clock,
	): ConnectCheckoutWalletUseCase =
		ConnectCheckoutWalletUseCase(
			checkoutSessionRepository = checkoutSessionRepository,
			transactionManager = transactionManager,
			clock = clock,
		)

	@Bean
	fun submitPaymentTransactionUseCase(
		checkoutSessionRepository: CheckoutSessionRepository,
		paymentRepository: PaymentRepository,
		blockchainTransactionRepository: BlockchainTransactionRepository,
		idGenerator: IdGenerator,
		transactionManager: TransactionManager,
		clock: Clock,
	): SubmitPaymentTransactionUseCase =
		SubmitPaymentTransactionUseCase(
			checkoutSessionRepository = checkoutSessionRepository,
			paymentRepository = paymentRepository,
			blockchainTransactionRepository = blockchainTransactionRepository,
			idGenerator = idGenerator,
			transactionManager = transactionManager,
			clock = clock,
		)

	@Bean
	fun cancelCheckoutSessionUseCase(
		checkoutSessionRepository: CheckoutSessionRepository,
		transactionManager: TransactionManager,
		clock: Clock,
	): CancelCheckoutSessionUseCase =
		CancelCheckoutSessionUseCase(
			checkoutSessionRepository = checkoutSessionRepository,
			transactionManager = transactionManager,
			clock = clock,
		)
}
