package paytech.practice.pay.application.port.outbound

/**
 * 비밀번호 해시·검증을 위한 Outbound Port다.
 *
 * 어떤 해시 알고리즘을 쓰는지(BCrypt 등)는 어댑터의 책임이다 — application/domain은
 * 해시 문자열을 그대로 다룰 뿐 어떻게 만들어졌는지 알지 못한다
 * (`docs/architecture/persistence-jooq.md`의 "비밀번호 원문을 저장하거나 로그에
 * 기록하지 않는다" 규칙과 같은 맥락).
 */
interface PasswordEncoder {
	/** 원문 비밀번호를 저장 가능한 해시로 변환한다. */
	fun encode(rawPassword: String): String

	/** 원문 비밀번호가 저장된 해시와 일치하는지 확인한다. */
	fun matches(
		rawPassword: String,
		encodedPassword: String,
	): Boolean
}
