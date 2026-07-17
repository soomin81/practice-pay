package paytech.practice.pay.api.merchant.web

/** 이 API의 모든 에러 응답 본문이 공유하는 최소 형태다. */
data class ErrorResponse(
	val message: String,
)
