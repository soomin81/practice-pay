package paytech.practice.pay.domain.identity

/**
 * 가맹점 관리자 로그인 시도의 결과다([MerchantLoginAudit]에 남긴다).
 *
 * [InternalLoginOutcome]과 값이 동일하지만 **공유하지 않고 병렬로 둔다** — 내부 운영자와
 * 가맹점 사용자가 의도적으로 생명주기를 나눈 별개의 자격증명 영역(ADR-006)이라, 로그인
 * 감사도 각자의 타입을 갖게 해 한쪽 변경이 다른 쪽에 새지 않게 한다. 실제로 통합할
 * 필요가 생기면 그때 공유 타입으로 올린다.
 */
enum class MerchantLoginOutcome {
	/** 인증 성공. */
	SUCCESS,

	/** 자격증명 실패 — 없는 merchantCode, 없는 loginId, 비밀번호 불일치, 비활성 계정을 뭉뚱그린다. */
	INVALID_CREDENTIALS,

	/** 잠긴 계정으로의 시도(잠금 시각이 아직 지나지 않음). */
	LOCKED,
}
