package paytech.practice.pay.application.port.outbound

/**
 * 여러 Aggregate에 걸친 쓰기를 하나의 DB 트랜잭션으로 묶어 실행하는 Outbound Port다.
 *
 * `docs/architecture/persistence-jooq.md`의 "트랜잭션 경계"(예: 결제 생성은
 * `Payment + PaymentQuote + CheckoutSession + OutboxEvent`를 한 트랜잭션으로 묶는다)를
 * 만족시키기 위한 것이다. Command Repository는 한 번에 Aggregate 하나만 저장·복원하는
 * 게 원칙이라(`docs/architecture/persistence-jooq.md`의 "Command와 Query"),
 * 여러 Repository 호출을 하나의 트랜잭션으로 묶는 책임은 그 Repository들이 아니라
 * 이 Port가 진다. 실제 구현(예: jOOQ의 `DSLContext.transaction { }`)은 영속성
 * 어댑터의 책임이며, Application 계층은 어떤 트랜잭션 프레임워크를 쓰는지 알지 못한다.
 *
 * 메서드가 제네릭(`<T>`)이라 Kotlin의 SAM 변환(`fun interface`)을 쓸 수 없다 —
 * `fun interface`의 추상 메서드는 타입 파라미터를 가질 수 없다는 언어 제약 때문에
 * 평범한 `interface`로 선언한다.
 */
interface TransactionManager {
	/** [block]을 하나의 트랜잭션 안에서 실행하고 그 결과를 반환한다. */
	fun <T> runInTransaction(block: () -> T): T
}
