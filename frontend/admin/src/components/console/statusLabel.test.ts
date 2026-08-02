import { describe, expect, it } from 'vitest'
import { labelFor } from '@/components/console/statusLabel'

describe('상태 한글 표기', () => {
	/**
	 * **이 테스트가 `kind`를 두는 이유 전체다.** 같은 코드가 애그리게이트마다 다른 뜻이라,
	 * 코드 하나로 한글을 정하면 정산 화면에 "결제 대기"가 뜬다.
	 */
	it('같은 코드라도 상태 집합에 따라 다른 말을 고른다', () => {
		expect(labelFor('payment', 'READY')).toBe('결제 대기')
		expect(labelFor('settlement', 'READY')).toBe('정산 준비됨')

		expect(labelFor('settlement', 'PENDING')).toBe('정산 대기')
		expect(labelFor('webhook', 'PENDING')).toBe('전송 대기')

		expect(labelFor('payment', 'SUCCEEDED')).toBe('결제 완료')
		expect(labelFor('webhook', 'SUCCEEDED')).toBe('전송 성공')

		expect(labelFor('account', 'ACTIVE')).toBe('활성')
		expect(labelFor('apiKey', 'ACTIVE')).toBe('사용 중')
	})

	/**
	 * **모르는 코드는 코드 그대로 보여준다.** 서버에 새 상태가 생겼을 때 화면이 비거나
	 * "알 수 없음"으로 뭉개지면 운영자가 무슨 일인지 알 수 없다 — 코드라도 보이면 문서를
	 * 찾아볼 수 있고, 동시에 "한글을 붙여 달라"는 신호가 된다.
	 */
	it('아직 한글이 없는 코드는 코드를 그대로 보여준다', () => {
		expect(labelFor('payment', 'BRAND_NEW_STATUS')).toBe('BRAND_NEW_STATUS')
	})

	/**
	 * 잠김은 정지·종료와 **다른 말**을 써야 한다 — 관리자가 의도해서 닫은 것이 아니라
	 * 로그인 실패가 쌓여 저절로 걸린 것이라, 대응이 다르다.
	 */
	it('잠김과 정지를 구분해 부른다', () => {
		expect(labelFor('account', 'LOCKED')).toBe('잠김')
		expect(labelFor('account', 'SUSPENDED')).toBe('정지')
	})
})
