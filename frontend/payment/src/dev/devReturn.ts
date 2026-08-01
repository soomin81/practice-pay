/**
 * 결제 완료·취소 후 **가맹점 사이트로 되돌아온 자리**를 나타내는 쿼리 파라미터다
 * (`?dev-return=success` / `?dev-return=cancel`).
 *
 * 세션 식별자를 `?session=`으로 받는 것과 같은 방식이다(`App.tsx`) — 화면이 몇 개 안 돼
 * 라우터를 들이지 않는다.
 *
 * 컴포넌트 파일이 아니라 여기 있는 이유는 lint 규칙(`only-export-components`) 때문이다:
 * 한 파일이 컴포넌트와 일반 함수를 함께 내보내면 Fast Refresh가 동작하지 않는다.
 */
export type DevReturnKind = 'success' | 'cancel'

export function readDevReturnFromUrl(search: string): DevReturnKind | null {
	const value = new URLSearchParams(search).get('dev-return')
	return value === 'success' || value === 'cancel' ? value : null
}
