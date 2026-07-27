package paytech.practice.pay.api.admin.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.application.identity.ListInternalLoginAuditUseCase

/**
 * 내부 운영자 로그인 감사 로그 조회 API를 노출하는 inbound Adapter다
 * (`docs/architecture/admin-console-api.md` 4절, 계약은 그 문서 참고).
 *
 * **`SUPER_ADMIN` 전용**이다 — 실패 시도·클라이언트 IP·누가 로그인했는지가 담겨 내부 직원
 * 명부와 같은 민감도다. `SecurityConfig`가 `/admin/login-audit`를 `SUPER_ADMIN`으로 잠그므로
 * `ListInternalLoginAuditUseCase`는 요청자를 받지 않는다(그 KDoc 참고).
 */
@RestController
@RequestMapping("/admin/login-audit")
class LoginAuditController(
	private val listInternalLoginAuditUseCase: ListInternalLoginAuditUseCase,
) {
	@GetMapping
	fun listLoginAudit(): ListLoginAuditResponse {
		val result = listInternalLoginAuditUseCase.execute()

		return ListLoginAuditResponse(
			entries =
				result.entries.map { entry ->
					LoginAuditEntryResponse(
						auditId = entry.auditId.value,
						internalUserId = entry.internalUserId?.value,
						attemptedLoginId = entry.attemptedLoginId,
						userName = entry.userName,
						outcome = entry.outcome.name,
						clientIp = entry.clientIp,
						occurredAt = entry.occurredAt,
					)
				},
		)
	}
}
