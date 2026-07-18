package paytech.practice.pay.batch.job

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.stereotype.Component
import paytech.practice.pay.application.exchange.SellToFakeExchangeCommand
import paytech.practice.pay.application.exchange.SellToFakeExchangeUseCase
import paytech.practice.pay.application.port.outbound.PaymentRepository

private val logger = KotlinLogging.logger {}

/**
 * Fake Exchange 매도 폴링 한 회차다 — `PaymentRepository.findPendingExchangeSettlement()`로
 * 대상을 뽑아 `SellToFakeExchangeUseCase`를 하나씩 호출한다.
 *
 * `ConfirmPendingBlockchainTransactionsTasklet`/`PublishPendingOutboxEventsTasklet`과
 * 같은 이유로 하나가 실패해도 나머지를 계속 처리하고(개별 `try/catch`), Step
 * 트랜잭션에 기대지 않는다 — 각 `SellToFakeExchangeUseCase.execute()` 호출이 이미
 * 자기 트랜잭션으로 저장까지 끝낸다.
 */
@Component
class SellPendingPaymentsToFakeExchangeTasklet(
	private val paymentRepository: PaymentRepository,
	private val sellToFakeExchangeUseCase: SellToFakeExchangeUseCase,
) : Tasklet {
	override fun execute(
		contribution: StepContribution,
		chunkContext: ChunkContext,
	): RepeatStatus {
		val pending = paymentRepository.findPendingExchangeSettlement()
		logger.info { "Fake Exchange 매도 대상 ${pending.size}건" }

		for (payment in pending) {
			try {
				sellToFakeExchangeUseCase.execute(SellToFakeExchangeCommand(payment.id))
			} catch (ex: RuntimeException) {
				logger.warn(ex) { "Payment(${payment.id.value}) Fake Exchange 매도 실패 — 다음 폴링에서 재시도한다." }
			}
		}

		return RepeatStatus.FINISHED
	}
}
