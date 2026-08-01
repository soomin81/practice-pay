import { expect, test } from 'vitest'
import { baseSepolia } from 'wagmi/chains'
import { asAddress, asSupportedChainId } from './useWalletPayment'

/**
 * 전송 직전에 거는 두 가드의 테스트.
 *
 * 둘 다 **타입만 맞추려면 캐스팅으로 끝낼 수 있는 자리**라 테스트로 못 박아 둔다.
 * 캐스팅으로 바꿔 놓으면 여기서 깨진다 — 잘못된 값이 지갑까지 흘러가면 되돌릴 수 없다.
 * (실제 전송은 자동 테스트가 불가능하므로, 검증할 수 있는 부분만이라도 남긴다.)
 */

test('설정에 있는 체인은 그대로 통과한다', () => {
	expect(asSupportedChainId(baseSepolia.id)).toBe(baseSepolia.id)
})

test('설정에 없는 체인은 거부한다 — 추측해서 보내지 않는다', () => {
	// 1 = Ethereum mainnet. 여기로 USDC를 보내면 되돌릴 수 없다.
	expect(() => asSupportedChainId(1)).toThrow(/지원하지 않는 네트워크/)
})

test('올바른 EVM 주소는 그대로 통과한다', () => {
	const address = '0x036CbD53842c5426634e7929541eC2318f3dCF7e'
	expect(asAddress(address, '토큰 Contract 주소')).toBe(address)
})

/**
 * **백엔드는 EIP-55 체크섬을 검증하지 않는다**(의도적). 그래서 수취 지갑이 소문자로
 * 설정돼 내려오는 일이 실제로 있는데, 정규화하지 않으면 viem이 인코딩 단계에서
 * "Address must match its checksum counterpart"로 거부해 **결제가 지갑 단계에서 막힌다.**
 * 실물 검증에서 실제로 걸렸던 회귀다.
 */
test.each([
	['소문자', '0x036cbd53842c5426634e7929541ec2318f3dcf7e'],
	['대문자', '0x036CBD53842C5426634E7929541EC2318F3DCF7E'],
])('체크섬이 맞지 않는 주소(%s)는 정규화해서 통과시킨다', (_, value) => {
	expect(asAddress(value, '수취 지갑 주소')).toBe('0x036CbD53842c5426634e7929541eC2318f3dCF7e')
})

test.each([
	['0x 접두사 없음', '036CbD53842c5426634e7929541eC2318f3dCF7e'],
	['자리수 부족', '0x036CbD53842c5426634e7929541eC2318f3dCF'],
	['16진수가 아닌 문자', '0x036CbD53842c5426634e7929541eC2318f3dCFZZ'],
	['빈 문자열', ''],
])('잘못된 주소(%s)는 거부한다', (_, value) => {
	expect(() => asAddress(value, '수취 지갑 주소')).toThrow(/수취 지갑 주소 형식/)
})
