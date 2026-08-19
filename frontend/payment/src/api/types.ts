import type { operations } from './schema'

/**
 * 생성된 스펙 타입에서 쓰기 좋은 이름만 뽑아 둔다.
 *
 * `schema.d.ts`는 손대지 않는다(생성물) — 스펙이 바뀌면 다시 생성해서 덮어쓴다.
 * 여기 별칭은 그 생성물을 가리키기만 하므로, 백엔드가 필드를 바꾸면 이 별칭을 쓰는
 * 화면 코드에서 컴파일 에러가 난다. 그게 이 계층을 두는 이유다.
 */
type Json200<T extends keyof operations> = operations[T]['responses'][200]['content']['application/json']

export type CheckoutSession = Json200<'checkout-get-session'>
export type CheckoutStatus = Json200<'checkout-get-status'>
export type SubmitCustomerResponse = Json200<'checkout-submit-customer'>
export type ConnectWalletResponse = Json200<'checkout-connect-wallet'>
export type SubmitTransactionResponse = Json200<'checkout-submit-transaction'>
export type CancelResponse = Json200<'checkout-cancel'>

/** 계약(`docs/domain/state-transitions.md`)의 상태 값. 화면 분기는 이 값들이 이끈다. */
export type CheckoutSessionStatus =
	| 'CREATED'
	| 'OPEN'
	| 'WALLET_CONNECTED'
	| 'PAYMENT_SUBMITTED'
	| 'COMPLETED'
	| 'EXPIRED'
	| 'CANCELLED'

export type PaymentStatus = 'CREATED' | 'READY' | 'PROCESSING' | 'CONFIRMING' | 'SUCCEEDED' | 'EXPIRED' | 'FAILED'
