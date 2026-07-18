package paytech.practice.pay.application.identity

import paytech.practice.pay.domain.identity.LoginId
import java.time.Instant

/**
 * [AcceptAccountInvitationUseCase]의 결과다.
 *
 * 활성화된 계정을 다시 조회할 내부 ID는 담지 않는다 — 호출부(inbound Adapter)가
 * 이 결과로 로그인 화면 안내 정도만 하면 충분하다.
 */
data class AcceptAccountInvitationResult(
	val loginId: LoginId,
	val activatedAt: Instant,
)
