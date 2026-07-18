package paytech.practice.pay.api.admin.web

import jakarta.validation.constraints.NotBlank

/**
 * `POST /admin/merchants`의 요청 본문이다(`docs/architecture/identity-access-api-key.md`의
 * "4.3 가맹점 등록과 OWNER 생성"). [webhookUrl]은 선택값이다 — 가맹점이 나중에
 * 자기 관리 화면에서 설정할 수도 있어서 등록 시점에 필수로 요구하지 않는다.
 */
data class RegisterMerchantRequest(
	@field:NotBlank
	val merchantCode: String,
	@field:NotBlank
	val merchantName: String,
	val webhookUrl: String? = null,
	@field:NotBlank
	val ownerLoginId: String,
	@field:NotBlank
	val ownerEmail: String,
	@field:NotBlank
	val ownerUserName: String,
)
