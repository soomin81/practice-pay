package paytech.practice.pay.domain.identity

/**
 * 로그인 시도의 결과다 — 내부 운영자([InternalLoginAudit])와 가맹점 관리자([MerchantLoginAudit])
 * 감사 로그가 **함께 쓴다**.
 *
 * 내부/가맹점은 의도적으로 생명주기를 나눈 별개의 자격증명 영역이지만(ADR-006), 로그인
 * *결과*의 의미는 같아서 값이 완전히 동일하다 — `AccountStatus`/`LoginId`를 두 영역이 공유하는
 * 것과 같은 판단이다(값이 갈리는 `InternalUserRole`/`MerchantUserRole`은 공유하지 않는 것과 대비).
 *
 * 로그인 Use Case는 **클라이언트에게는** 실패 사유를 구분해 알려주지 않지만(계정 존재 여부·
 * 비밀번호 불일치·비활성을 전부 `InvalidCredentialsException`으로 뭉갠다), 내부 전용 감사
 * 로그에는 이 세 값만큼은 구분해 남긴다 — 잠금(`LOCKED`)만은 그 자체로
 * `AccountLockedException`으로 분리돼 있어 따로 기록할 가치가 있다.
 */
enum class LoginOutcome {
	/** 인증 성공. */
	SUCCESS,

	/** 자격증명 실패 — 없는 계정(내부: loginId, 가맹점: merchantCode/loginId)·비밀번호 불일치·비활성 계정을 뭉뚱그린다. */
	INVALID_CREDENTIALS,

	/** 잠긴 계정으로의 시도(잠금 시각이 아직 지나지 않음). */
	LOCKED,
}
