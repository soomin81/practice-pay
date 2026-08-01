package paytech.practice.pay.application.checkout

import paytech.practice.pay.application.port.outbound.CheckoutSessionRepository
import paytech.practice.pay.application.port.outbound.TransactionManager
import paytech.practice.pay.domain.checkout.CheckoutSessionStatus
import java.time.Clock

/**
 * "체크아웃 지갑 연결" Use Case다 — 고객이 체크아웃 페이지에서 외부 EVM 지갑을
 * 연결하는 시점을 구현한다. [paytech.practice.pay.application.payment.SubmitPaymentTransactionUseCase]가
 * "이미 `WALLET_CONNECTED`인 CheckoutSession"을 전제하고 시작했던 지점을, 이
 * Use Case가 그보다 앞서 채운다.
 *
 * `CheckoutSession`만 다루는 단일 Aggregate Use Case라 `TransactionManager`가
 * 필요 없다 — `CheckoutSessionRepository.save` 한 번으로 끝난다(`Payment`나
 * `BlockchainTransaction`은 건드리지 않는다).
 *
 * **`CREATED` 상태였다면 `open()`을 먼저 호출한 뒤 `connectWallet()`으로 넘어간다.**
 * `CheckoutSession.open()`을 부르는 별도의 "체크아웃 페이지 조회" Use Case/API가
 * 없다 — 페이지 조회 자체는 상태를 바꾸지 않는 `GET` 요청으로 두고(REST 관례),
 * 고객이 실제로 처음 행동을 취하는 순간(지갑 연결)을 `open()`이 뜻하는 "체크아웃
 * 페이지를 열었다"로 간주했다. 이미 `OPEN`이면 `open()`을 다시 부르지 않는다
 * (`open()`은 `CREATED`에서만 허용된다).
 *
 * `WALLET_CONNECTED` 이후 상태(제출/완료/취소/만료)에서 다시 호출하면
 * `CheckoutSession.connectWallet()`의 `checkTransition`이 그대로
 * `IllegalStateException`을 던진다 — 이미 연결된 지갑을 다른 지갑으로 바꾸는
 * 흐름은 도메인에 없다(범위 밖).
 */
class ConnectCheckoutWalletUseCase(
	private val checkoutSessionRepository: CheckoutSessionRepository,
	private val transactionManager: TransactionManager,
	private val clock: Clock,
) {
	/**
	 * 로드부터 저장까지를 **한 트랜잭션 안에서** 수행한다 — 변경할 목적의 읽기를 잠그고
	 * ([CheckoutSessionRepository.findByIdForUpdate]) 그 잠금을 저장까지 유지하기 위해서다.
	 * 잠금이 없으면 같은 세션에 대한 동시 요청(재연결·취소·만료 Sweep)이 서로를 덮어쓴다.
	 */
	fun execute(command: ConnectCheckoutWalletCommand): ConnectCheckoutWalletResult =
		transactionManager.runInTransaction {
			val checkoutSession =
				checkoutSessionRepository.findByIdForUpdate(command.checkoutSessionId)
					?: throw CheckoutSessionNotFoundException(command.checkoutSessionId)

			val now = clock.instant()

			if (checkoutSession.status == CheckoutSessionStatus.CREATED) {
				checkoutSession.open(now)
			}
			checkoutSession.connectWallet(command.walletAddress, now)

			checkoutSessionRepository.save(checkoutSession)

			ConnectCheckoutWalletResult(
				checkoutSessionId = checkoutSession.id,
				checkoutSessionStatus = checkoutSession.status,
				connectedWallet = checkNotNull(checkoutSession.connectedWallet),
			)
		}
}
