package paytech.practice.pay.domain.identity

/**
 * [AccountInvitation]이 어떤 계정을 활성화하기 위한 것인지를 표현한다.
 *
 * DB의 `account_invitation.account_type` 컬럼과 대응하며, 정확히 하나의 대상
 * ID(`internal_user_seq` 또는 `merchant_user_seq`)와 함께 쓰인다.
 */
enum class InvitationAccountType {
	INTERNAL_USER,
	MERCHANT_USER,
}
