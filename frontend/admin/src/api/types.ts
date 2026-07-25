import type { operations } from './schema'

/**
 * 생성된 스펙 타입에서 쓰기 좋은 이름만 뽑아 둔다(merchant 앱의 types.ts와 같은 판단).
 * `schema.d.ts`는 생성물이라 손대지 않는다 — 백엔드가 필드를 바꾸면 이 별칭을 쓰는 화면
 * 코드에서 컴파일 에러가 나는 것이 이 계층의 목적이다.
 */
type ContentOf<T> = T extends { content: infer C } ? C[keyof C] : never

type JsonResponse<T extends keyof operations, S extends keyof operations[T]['responses']> =
	ContentOf<operations[T]['responses'][S]>

type JsonRequest<T extends keyof operations> = ContentOf<NonNullable<operations[T]['requestBody']>>

export type MeResponse = JsonResponse<'admin-me', 200>

export type LoginRequest = JsonRequest<'admin-login'>
export type LoginResponse = JsonResponse<'admin-login', 200>

export type ListMerchantsResponse = JsonResponse<'admin-list-merchants', 200>
export type MerchantSummary = ListMerchantsResponse['merchants'][number]

export type RegisterMerchantRequest = JsonRequest<'admin-register-merchant'>
export type RegisterMerchantResponse = JsonResponse<'admin-register-merchant', 201>

/** 계약(`docs/architecture/identity-access-api-key.md`의 "3.2")의 내부 운영자 역할. */
export type InternalUserRole = 'SUPER_ADMIN' | 'OPERATOR' | 'VIEWER'

/**
 * 가맹점을 등록할 수 있는 역할. `VIEWER`는 조회 전용이라 등록 폼을 보여주지 않는다 —
 * 서버도 403으로 막지만(SecurityConfig의 메서드 스코핑), 누를 수 있게 두고 거부하는 것보다
 * 감추는 편이 낫다.
 */
export function canRegisterMerchant(role: string): boolean {
	return role === 'SUPER_ADMIN' || role === 'OPERATOR'
}
