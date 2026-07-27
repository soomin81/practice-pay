package paytech.practice.pay.domain.identity

/**
 * 내부 운영자 로그인 시도의 결과다([InternalLoginAudit]에 남긴다).
 *
 * `AuthenticateInternalUserUseCase`는 **클라이언트에게는** 실패 사유를 구분해 알려주지
 * 않지만(계정 존재 여부·비밀번호 불일치·비활성을 전부 `InvalidCredentialsException`으로
 * 뭉갠다), 내부 전용 감사 로그에는 이 세 값만큼은 구분해 남긴다 — 잠금(`LOCKED`)만은
 * 그 자체로 `AccountLockedException`으로 분리돼 있어 따로 기록할 가치가 있다.
 */
enum class InternalLoginOutcome {
	/** 인증 성공. */
	SUCCESS,

	/** 자격증명 실패 — 없는 loginId, 비밀번호 불일치, 비활성 계정을 뭉뚱그린다. */
	INVALID_CREDENTIALS,

	/** 잠긴 계정으로의 시도(잠금 시각이 아직 지나지 않음). */
	LOCKED,
}
