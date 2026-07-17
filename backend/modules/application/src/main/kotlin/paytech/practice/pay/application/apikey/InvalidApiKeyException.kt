package paytech.practice.pay.application.apikey

/**
 * API Key가 없거나, 형식이 잘못됐거나, 존재하지 않거나, Secret이 일치하지 않거나,
 * `ACTIVE`가 아니거나, 만료됐거나, `TEST` 환경이 아니거나, 소속 Merchant가 결제를
 * 받을 수 없는 상태일 때 던진다.
 *
 * [paytech.practice.pay.application.identity.InvalidCredentialsException]과 같은
 * 이유로 이 모든 경우를 하나로 묶는다 — Key/Merchant의 존재나 상태를 호출부에
 * 노출하지 않기 위해서다.
 */
class InvalidApiKeyException : RuntimeException("API Key가 유효하지 않습니다.")
