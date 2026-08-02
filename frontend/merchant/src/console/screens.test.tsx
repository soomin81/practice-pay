import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithQuery } from '@/test-utils'
import { LoginPage } from '@/console/LoginPage'
import { ApiKeyTable } from '@/console/ApiKeyTable'
import type { ApiKeySummary } from '@/api/types'

function activeKey(overrides: Partial<ApiKeySummary> = {}): ApiKeySummary {
	return {
		merchantApiKeyId: 'mak_1',
		keyName: '운영 서버용',
		environment: 'TEST',
		keyPrefix: 'sk_test_ab12cd34',
		scopes: ['PAYMENT_CREATE', 'PAYMENT_READ'],
		status: 'ACTIVE',
		createdAt: '2026-07-19T00:00:00Z',
		lastUsedAt: null,
		revokedAt: null,
		...overrides,
	} as ApiKeySummary
}

describe('LoginPage', () => {
	it('가맹점 코드/로그인 아이디/비밀번호 입력과 로그인 버튼을 그린다', () => {
		renderWithQuery(<LoginPage />)
		expect(screen.getByLabelText('가맹점 코드')).toBeInTheDocument()
		expect(screen.getByLabelText('로그인 아이디')).toBeInTheDocument()
		expect(screen.getByLabelText('비밀번호')).toBeInTheDocument()
		expect(screen.getByRole('button', { name: '로그인' })).toBeInTheDocument()
	})
})

describe('ApiKeyTable', () => {
	it('서버 값을 그대로 보여주고 ACTIVE Key에 폐기 버튼을 둔다', () => {
		renderWithQuery(<ApiKeyTable apiKeys={[activeKey()]} />)
		expect(screen.getByText('운영 서버용')).toBeInTheDocument()
		expect(screen.getByText('sk_test_ab12cd34')).toBeInTheDocument()
		expect(screen.getByText('사용 중')).toBeInTheDocument()
		expect(screen.getByRole('button', { name: '폐기' })).toBeInTheDocument()
	})

	it('폐기된 Key에는 폐기 버튼을 두지 않는다', () => {
		renderWithQuery(
			<ApiKeyTable
				apiKeys={[activeKey({ merchantApiKeyId: 'mak_2', status: 'REVOKED', revokedAt: '2026-07-20T00:00:00Z' })]}
			/>,
		)
		expect(screen.getByText('폐기됨')).toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '폐기' })).not.toBeInTheDocument()
	})

	it('빈 목록은 안내 문구를 보여준다', () => {
		renderWithQuery(<ApiKeyTable apiKeys={[]} />)
		expect(screen.getByText('아직 발급된 API Key가 없습니다.')).toBeInTheDocument()
	})
})
