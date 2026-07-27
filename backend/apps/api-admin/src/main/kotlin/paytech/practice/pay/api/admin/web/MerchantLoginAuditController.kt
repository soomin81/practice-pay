package paytech.practice.pay.api.admin.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import paytech.practice.pay.application.identity.ListMerchantLoginAuditUseCase

/**
 * 가맹점 관리자 로그인 감사 로그 조회 API를 노출하는 inbound Adapter다
 * (`docs/architecture/admin-console-api.md` 4절). 기록은 api-merchant의 로그인 Use Case가
 * 하고, 조회는 내부 운영자 콘솔이 **전 가맹점**을 대상으로 한다(지원·보안 감독).
 *
 * **`SUPER_ADMIN`/`OPERATOR`가 조회할 수 있다** — 내부 직원 로그인 감사(`SUPER_ADMIN` 전용)와
 * 달리 OPERATOR도 포함하는 이유는 OPERATOR가 "가맹점·결제·운영 업무"를 맡고 가맹점 계정
 * 관리도 하기 때문이다(`identity-access-api-key.md`의 3.2·4.6). `SecurityConfig`가
 * `/admin/merchant-login-audit`를 그 역할로 잠그므로 Use Case는 요청자를 받지 않는다.
 */
@RestController
@RequestMapping("/admin/merchant-login-audit")
class MerchantLoginAuditController(
	private val listMerchantLoginAuditUseCase: ListMerchantLoginAuditUseCase,
) {
	@GetMapping
	fun listMerchantLoginAudit(): ListMerchantLoginAuditResponse {
		val result = listMerchantLoginAuditUseCase.execute()

		return ListMerchantLoginAuditResponse(
			entries =
				result.entries.map { entry ->
					MerchantLoginAuditEntryResponse(
						auditId = entry.auditId.value,
						merchantId = entry.merchantId?.value,
						merchantName = entry.merchantName,
						attemptedMerchantCode = entry.attemptedMerchantCode,
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
