package paytech.practice.pay.application.port.outbound

/**
 * 새 Aggregate 공개 ID에 쓸 고유 토큰을 발급하는 Outbound Port다.
 *
 * 어떤 방식으로 고유성을 보장하는지(UUID, ULID 등)는 어댑터의 책임이다 — Use Case는
 * 반환값을 그대로 신뢰하고, 여기에 각 Aggregate ID의 접두어(`pay_`, `cs_` 등)를 붙여
 * 최종 ID VO를 만든다. 접두어 규칙이 Port 자체에 들어가지 않는 이유는, 접두어는
 * Use Case마다 다르고 순수한 고유 토큰 발급과는 무관한 관심사이기 때문이다.
 */
fun interface IdGenerator {
	/** 새로운 고유 토큰을 반환한다. */
	fun newId(): String
}
