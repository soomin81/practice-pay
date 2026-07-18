package paytech.practice.pay.batch.job

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.StepContribution
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.infrastructure.repeat.RepeatStatus
import org.springframework.stereotype.Component
import paytech.practice.pay.application.payment.ConfirmBlockchainTransactionCommand
import paytech.practice.pay.application.payment.ConfirmBlockchainTransactionUseCase
import paytech.practice.pay.application.port.outbound.BlockchainTransactionRepository

private val logger = KotlinLogging.logger {}

/**
 * BlockchainTransaction 감지·Confirm 폴링 한 회차다 — `BlockchainTransactionRepository.
 * findPendingConfirmation()`으로 대상 목록을 뽑아 `ConfirmBlockchainTransactionUseCase`를
 * 하나씩 호출한다(`docs/database/database-design.md`의 "Confirm Worker" 인덱스가
 * 암시하는 그 Worker, `ConfirmBlockchainTransactionUseCase`의 KDoc이 범위 밖으로
 * 남겨뒀던 지점).
 *
 * **하나가 실패해도 나머지를 계속 처리한다.** `BlockchainClientException`(RPC
 * 일시 실패) 같은 예외 하나 때문에 이번 회차의 다른 BlockchainTransaction까지
 * confirm이 밀리면 안 된다 — 실패한 항목은 로그만 남기고 다음 항목으로 넘어간다.
 * 다음 폴링에서 같은 항목을 다시 시도하게 된다(Repository가 상태를 바꾸지
 * 않았으므로 여전히 대상 목록에 남아 있다).
 *
 * 이 Tasklet 자체는 Spring Batch의 Step 트랜잭션에 기대지 않는다 — 각
 * `ConfirmBlockchainTransactionUseCase.execute()` 호출이 이미 자기 안에서
 * `TransactionManager.runInTransaction { }`으로 저장을 묶는다(`ConfirmBlockchainTransactionJobConfiguration`이
 * Step에 `ResourcelessTransactionManager`를 쓰는 이유이기도 하다 — Step 레벨에서
 * 또 감싸면 이중으로 트랜잭션을 거는 셈이라 의미가 없다).
 */
@Component
class ConfirmPendingBlockchainTransactionsTasklet(
	private val blockchainTransactionRepository: BlockchainTransactionRepository,
	private val confirmBlockchainTransactionUseCase: ConfirmBlockchainTransactionUseCase,
) : Tasklet {
	override fun execute(
		contribution: StepContribution,
		chunkContext: ChunkContext,
	): RepeatStatus {
		val pending = blockchainTransactionRepository.findPendingConfirmation()
		logger.info { "Confirm 폴링 대상 ${pending.size}건" }

		for (blockchainTransaction in pending) {
			try {
				confirmBlockchainTransactionUseCase.execute(ConfirmBlockchainTransactionCommand(blockchainTransaction.id))
			} catch (ex: RuntimeException) {
				logger.warn(ex) { "BlockchainTransaction(${blockchainTransaction.id.value}) Confirm 실패 — 다음 폴링에서 재시도한다." }
			}
		}

		return RepeatStatus.FINISHED
	}
}
