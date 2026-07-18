package paytech.practice.pay.application.identity

/**
 * 초대 Token이 존재하지 않거나, 기대한 `accountType`과 다르거나, `PENDING`이
 * 아니거나(이미 수락/만료/폐기됨), 만료 시각이 지났을 때 던진다.
 *
 * 네 경우를 하나의 예외로 묶는다 — [InvalidCredentialsException]과 같은 이유로,
 * 어느 조건에서 실패했는지 호출부에 노출하지 않는다(Token 존재 여부를 드러내면
 * 다른 사람의 초대 Token을 무차별 대입으로 찾아낼 여지가 생긴다).
 */
class InvalidInvitationException : RuntimeException("초대가 유효하지 않거나 만료되었습니다.")
