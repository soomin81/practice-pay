import { describe, expect, it } from 'vitest'
import { formatKrw, formatTokenAmount, remainingTime, shortenHex } from './format'

describe('formatTokenAmount', () => {
	it('USDC(6 decimals) Minor Unit을 소수 표기로 바꾼다', () => {
		expect(formatTokenAmount('35893755', 6)).toBe('35.893755')
	})

	it('1 미만도 정수부 0을 채운다', () => {
		expect(formatTokenAmount('123', 6)).toBe('0.000123')
	})

	it('꼬리 0을 지운다', () => {
		expect(formatTokenAmount('35000000', 6)).toBe('35')
		expect(formatTokenAmount('35500000', 6)).toBe('35.5')
	})

	it('천 단위 구분자를 넣는다', () => {
		expect(formatTokenAmount('1234567890123', 6)).toBe('1,234,567.890123')
	})

	it('Number의 안전 정수 범위를 넘겨도 정확하다', () => {
		// 2^53 = 9007199254740992. Number로 변환했다면 마지막 자리가 뭉개진다.
		const beyondSafeInteger = '9007199254740993'
		expect(formatTokenAmount(beyondSafeInteger, 6)).toBe('9,007,199,254.740993')

		// 실제로 Number를 거치면 값이 달라진다는 것 자체를 남겨 둔다 —
		// 이 함수가 왜 문자열만 다루는지에 대한 근거다.
		expect(String(Number(beyondSafeInteger))).not.toBe(beyondSafeInteger)
	})

	it('18 decimals 토큰(대부분의 ERC-20)에서도 정확하다', () => {
		// 1 토큰 = 10^18. Long 범위(약 9.2 * 10^18)에 가까운 값도 문자열이라 안전하다.
		expect(formatTokenAmount('1000000000000000000', 18)).toBe('1')
		expect(formatTokenAmount('1500000000000000000', 18)).toBe('1.5')
	})

	it('decimals가 0이면 소수부가 없다', () => {
		expect(formatTokenAmount('42', 0)).toBe('42')
	})
})

describe('formatKrw', () => {
	it('원 단위 정수에 천 단위 구분자를 넣는다', () => {
		expect(formatKrw(50000)).toBe('50,000')
	})
})

describe('shortenHex', () => {
	it('긴 주소를 줄인다', () => {
		expect(shortenHex('0x036CbD53842c5426634e7929541eC2318f3dCF7e')).toBe('0x036C…CF7e')
	})

	it('짧은 값은 그대로 둔다', () => {
		expect(shortenHex('0x1234')).toBe('0x1234')
	})
})

describe('remainingTime', () => {
	const now = new Date('2026-07-19T10:00:00Z').getTime()

	it('남은 시간을 mm:ss로 준다', () => {
		expect(remainingTime('2026-07-19T10:05:30Z', now)).toBe('5:30')
	})

	it('한 자리 초에 0을 채운다', () => {
		expect(remainingTime('2026-07-19T10:00:05Z', now)).toBe('0:05')
	})

	it('이미 지났으면 null', () => {
		expect(remainingTime('2026-07-19T09:59:59Z', now)).toBeNull()
	})
})
