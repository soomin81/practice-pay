package paytech.practice.pay.batch.job

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.stereotype.Component
import paytech.practice.pay.application.port.outbound.PaymentRepository
import paytech.practice.pay.application.sweep.ExpireCheckoutCommand
import paytech.practice.pay.application.sweep.ExpireCheckoutUseCase
import java.time.Clock

private val logger = KotlinLogging.logger {}

/**
 * 체크아웃 만료 Sweep 한 회차다 — `PaymentRepository.findExpirable(now)`로 만료된 Payment를
 * 뽑아 `ExpireCheckoutUseCase`를 하나씩 호출한다(그 Use Case가 Payment와 딸린 CheckoutSession을
 * 함께 만료시킨다). 다른 Tasklet과 같은 이유로 하나가 실패해도 나머지를 계속 처리한다.
 */
@Component
class ExpireExpiredCheckoutsTasklet(
	private val paymentRepository: PaymentRepository,
	private val expireCheckoutUseCase: ExpireCheckoutUseCase,
	private val clock: Clock,
) : Tasklet {
	override fun execute(
		contribution: StepContribution,
		chunkContext: ChunkContext,
	): RepeatStatus {
		val expirable = paymentRepository.findExpirable(clock.instant())
		logger.info { "체크아웃 만료 대상 ${expirable.size}건" }

		for (payment in expirable) {
			try {
				expireCheckoutUseCase.execute(ExpireCheckoutCommand(payment.id))
			} catch (ex: RuntimeException) {
				logger.warn(ex) { "Payment(${payment.id.value}) 체크아웃 만료 실패 — 다음 폴링에서 재시도한다." }
			}
		}

		return RepeatStatus.FINISHED
	}
}
