package paytech.practice.pay.domain.identity

/**
 * 계정 초대를 식별하는 공개(외부 노출용) ID를 표현하는 Value Object다.
 *
 * DB의 `account_invitation.account_invitation_id` 컬럼(`VARCHAR(50)`, `UNIQUE`)과
 * 대응하며, 내부 전용 PK인 `account_invitation_seq`(`BIGINT AUTO_INCREMENT`)와는
 * 별개의 값이다.
 *
 * @property value 계정 초대 공개 ID 문자열. 공백일 수 없고 [MAX_LENGTH]자를 넘을 수 없다.
 */
@JvmInline
value class AccountInvitationId(val value: String) {

	init {
		require(value.isNotBlank()) { "AccountInvitationId는 공백일 수 없습니다." }
		require(value.length <= MAX_LENGTH) {
			"AccountInvitationId는 ${MAX_LENGTH}자를 초과할 수 없습니다: $value"
		}
	}

	companion object {
		/** `account_invitation.account_invitation_id` 컬럼의 최대 길이(`VARCHAR(50)`)와 동일하게 맞춘 값이다. */
		private const val MAX_LENGTH = 50
	}
}
