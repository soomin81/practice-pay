import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import { DevMerchantReturn } from './DevMerchantReturn'
import { readDevReturnFromUrl } from './devReturn'

/**
 * 결제가 끝나면 고객은 체크아웃을 떠나 가맹점 사이트로 이동한다. 로컬에는 그 사이트가
 * 없어 이 화면이 그 자리를 대신한다 — 예전에는 `merchant.example.com`을 가리켜
 * **마지막 단계가 죽은 도메인으로 끝났다.**
 */

test.each([
	['성공 복귀', '?dev-return=success', 'success'],
	['취소 복귀', '?dev-return=cancel', 'cancel'],
	['세션 파라미터가 함께 있어도 읽는다', '?session=cs_1&dev-return=success', 'success'],
])('%s', (_, search, expected) => {
	expect(readDevReturnFromUrl(search)).toBe(expected)
})

test.each([
	['값이 없음', '?session=cs_1'],
	['모르는 값', '?dev-return=maybe'],
	['빈 쿼리', ''],
])('복귀가 아닌 주소(%s)는 null이다', (_, search) => {
	expect(readDevReturnFromUrl(search)).toBeNull()
})

test('성공 복귀는 주문 완료로, 취소 복귀는 취소로 보여준다', () => {
	const { unmount } = render(<DevMerchantReturn kind="success" onRestart={() => {}} />)
	expect(screen.getByText('주문이 완료되었습니다')).toBeInTheDocument()
	unmount()

	render(<DevMerchantReturn kind="cancel" onRestart={() => {}} />)
	expect(screen.getByText('주문을 취소했습니다')).toBeInTheDocument()
})

/**
 * **이 화면이 결제 화면으로 오해되면 안 된다.** 가맹점 사이트를 대신하는 자리라는 표시와,
 * 실제 가맹점은 이 화면이 아니라 Webhook으로 주문을 확정해야 한다는 경고를 함께 둔다
 * (고객 복귀와 Webhook의 순서는 보장되지 않는다 — 계약 8절).
 */
test('가맹점 사이트를 대신하는 자리임과 Webhook 기준임을 밝힌다', () => {
	render(<DevMerchantReturn kind="success" onRestart={() => {}} />)

	// `<strong>`으로 강조한 조각까지 각각 잡히므로 개수는 세지 않고 존재만 확인한다.
	expect(screen.getAllByText(/가맹점 사이트/).length).toBeGreaterThan(0)
	expect(screen.getAllByText(/Webhook/).length).toBeGreaterThan(0)
})

test('다시 시작 버튼이 호출부에 알린다', async () => {
	const onRestart = vi.fn()
	render(<DevMerchantReturn kind="success" onRestart={onRestart} />)

	await userEvent.click(screen.getByRole('button', { name: '새 테스트 결제 시작하기' }))

	expect(onRestart).toHaveBeenCalledOnce()
})
