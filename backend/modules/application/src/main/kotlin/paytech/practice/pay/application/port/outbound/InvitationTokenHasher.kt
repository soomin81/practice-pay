package paytech.practice.pay.application.port.outbound

/**
 * 계정 초대(AccountInvitation) Token Hash·검증을 위한 Outbound Port다.
 *
 * [ApiKeySecretHasher]와 같은 이유로 분리한다 — 초대 Token은 사람이 입력하는
 * 비밀번호가 아니라 서버가 생성하는 고 Entropy 랜덤 값이라 느린 적응형 해시가
 * 필요 없다. `AccountInvitation`의 KDoc("초대 토큰 원문은 저장하지 않고 Hash만
 * 저장한다")을 만족시키기 위한 것이며, API Key의 Pepper와는 별도의 Pepper를
 * 쓴다 — 한쪽 비밀값이 새도 다른 쪽까지 같이 위험해지지 않도록 하기 위해서다.
 */
interface InvitationTokenHasher {
	/** 초대 Token 원문을 저장 가능한 Hash로 변환한다. */
	fun hash(rawToken: String): String

	/** 초대 Token 원문이 저장된 Hash와 일치하는지 확인한다. */
	fun matches(
		rawToken: String,
		hash: String,
	): Boolean
}
