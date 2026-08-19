import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, expect, test, vi } from 'vitest'
import { checkoutApi, CheckoutApiError } from '@/api/client'
import { CustomerForm } from './CustomerForm'

/**
 * 구매자 정보 입력 단계.
 *
 * 여기서 잡으려는 것은 셋이다.
 *  1. 입력한 값이 **그대로** 서버로 간다(필드가 서로 뒤바뀌지 않는다).
 *  2. 서버가 거절한 이유가 **고객에게 보인다** — 형식 검증을 프론트가 하지 않기로 했으므로,
 *     이 경로가 막히면 고객은 왜 안 되는지 영영 알 수 없다.
 *  3. 접수되면 **마스킹된 값**이 호출부로 넘어간다(원문이 아니라).
 */

const MASKED = {
	checkoutSessionId: 'cs_test_1',
	checkoutSessionStatus: 'OPEN',
	nameMasked: '홍*동',
	emailMasked: 'gi***@example.com',
	phoneMasked: '010-****-5678',
}

afterEach(() => {
	vi.restoreAllMocks()
})

async function fillAndSubmit() {
	const user = userEvent.setup()
	await user.type(screen.getByLabelText('이름'), '홍길동')
	await user.type(screen.getByLabelText('이메일'), 'gildong@example.com')
	await user.type(screen.getByLabelText('휴대전화'), '010-1234-5678')
	await user.click(screen.getByRole('button', { name: '다음' }))
}

test('입력한 세 값을 그대로 서버로 보낸다', async () => {
	const submitCustomer = vi.spyOn(checkoutApi, 'submitCustomer').mockResolvedValue(MASKED)
	render(<CustomerForm sessionId="cs_test_1" onSubmitted={() => {}} />)

	await fillAndSubmit()

	expect(submitCustomer).toHaveBeenCalledWith('cs_test_1', {
		name: '홍길동',
		email: 'gildong@example.com',
		phone: '010-1234-5678',
	})
})

test('접수되면 마스킹된 값을 호출부로 넘긴다', async () => {
	vi.spyOn(checkoutApi, 'submitCustomer').mockResolvedValue(MASKED)
	const onSubmitted = vi.fn()
	render(<CustomerForm sessionId="cs_test_1" onSubmitted={onSubmitted} />)

	await fillAndSubmit()

	expect(onSubmitted).toHaveBeenCalledWith(MASKED)
})

/**
 * 형식 검증은 서버가 한다 — 그 판정이 화면에 닿지 않으면 고객은 막힌 이유를 모른다.
 * 서버가 한글 메시지를 주므로 그대로 보여준다.
 */
test('서버가 거절한 이유를 그대로 보여준다', async () => {
	vi.spyOn(checkoutApi, 'submitCustomer').mockRejectedValue(
		new CheckoutApiError(400, '구매자 이메일 형식이 올바르지 않습니다.')
	)
	render(<CustomerForm sessionId="cs_test_1" onSubmitted={() => {}} />)

	await fillAndSubmit()

	expect(await screen.findByText('구매자 이메일 형식이 올바르지 않습니다.')).toBeInTheDocument()
})

test('실패해도 다시 시도할 수 있다', async () => {
	const submitCustomer = vi
		.spyOn(checkoutApi, 'submitCustomer')
		.mockRejectedValueOnce(new CheckoutApiError(400, '구매자 이름은 공백일 수 없습니다.'))
		.mockResolvedValueOnce(MASKED)
	const onSubmitted = vi.fn()
	render(<CustomerForm sessionId="cs_test_1" onSubmitted={onSubmitted} />)

	await fillAndSubmit()
	expect(await screen.findByText('구매자 이름은 공백일 수 없습니다.')).toBeInTheDocument()

	// 버튼이 잠긴 채로 남으면 고객은 여기서 갇힌다.
	await userEvent.setup().click(screen.getByRole('button', { name: '다음' }))

	expect(submitCustomer).toHaveBeenCalledTimes(2)
	expect(onSubmitted).toHaveBeenCalledWith(MASKED)
})
