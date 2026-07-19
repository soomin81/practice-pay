/**
 * ERC-20에서 이 앱이 실제로 부르는 함수만 담은 ABI.
 *
 * 전체 ABI를 가져다 쓰지 않는 이유는 번들 크기가 아니라 **의도**다 — 이 화면은
 * `transfer` 말고 아무것도 하지 않는다(`approve`도, `transferFrom`도 쓰지 않는다).
 * ABI에 없는 함수는 실수로도 호출할 수 없다.
 *
 * `as const`가 필요하다. viem이 이 리터럴 타입에서 인자 타입을 추론하기 때문에,
 * 빠뜨리면 `writeContract`의 `args`가 타입 검사를 받지 못한다.
 */
export const erc20Abi = [
	{
		type: 'function',
		name: 'transfer',
		stateMutability: 'nonpayable',
		inputs: [
			{ name: 'to', type: 'address' },
			{ name: 'amount', type: 'uint256' },
		],
		outputs: [{ name: '', type: 'bool' }],
	},
] as const
