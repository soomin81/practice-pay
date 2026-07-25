import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '@/test-utils'
import { MerchantUserTable } from '@/console/MerchantUserTable'
import { InviteSubAccountForm } from '@/console/InviteSubAccountForm'
import type { MerchantUserSummary } from '@/api/types'

function member(overrides: Partial<MerchantUserSummary> = {}): MerchantUserSummary {
	return {
		merchantUserId: 'mu_001',
		loginId: 'member01',
		email: 'member01@example.com',
		userName: '팀원',
		role: 'ADMIN',
		status: 'ACTIVE',
		lastLoginAt: '2026-07-19T01:00:00Z',
		createdAt: '2026-07-19T00:00:00Z',
		...overrides,
	} as MerchantUserSummary
}

function fakeResponse(status: number, body: unknown = {}) {
	return { ok: status >= 200 && status < 300, status, json: async () => body }
}

describe('MerchantUserTable', () => {
	it('서버 값을 그대로 보여준다', () => {
		renderWithRouter(<MerchantUserTable merchantUsers={[member()]} />)
		expect(screen.getByText('member01')).toBeInTheDocument()
		expect(screen.getByText('member01@example.com')).toBeInTheDocument()
		expect(screen.getByText('ACTIVE')).toBeInTheDocument()
	})

	it('INVITED 계정을 그 상태로 구분해 보여준다', () => {
		renderWithRouter(
			<MerchantUserTable merchantUsers={[member({ merchantUserId: 'mu_2', status: 'INVITED', lastLoginAt: null })]} />,
		)
		expect(screen.getByText('INVITED')).toBeInTheDocument()
		// 로그인한 적이 없으면 대시로 그린다.
		expect(screen.getByText('—')).toBeInTheDocument()
	})

	it('빈 명부는 안내 문구를 보여준다', () => {
		renderWithRouter(<MerchantUserTable merchantUsers={[]} />)
		expect(screen.getByText('아직 등록된 사용자가 없습니다.')).toBeInTheDocument()
	})
})

describe('MerchantUserTable 행 액션', () => {
	it('ACTIVE 계정에는 정지·종료·역할 변경이 있다', () => {
		renderWithRouter(<MerchantUserTable merchantUsers={[member()]} />)
		expect(screen.getByRole('button', { name: '정지' })).toBeInTheDocument()
		expect(screen.getByRole('button', { name: '종료' })).toBeInTheDocument()
		expect(screen.getByRole('button', { name: '역할 변경' })).toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '재개' })).not.toBeInTheDocument()
	})

	it('SUSPENDED 계정에는 재개가 있고 정지는 없다', () => {
		renderWithRouter(<MerchantUserTable merchantUsers={[member({ status: 'SUSPENDED' })]} />)
		expect(screen.getByRole('button', { name: '재개' })).toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '정지' })).not.toBeInTheDocument()
	})

	it('INVITED 계정에는 종료만 있다(아직 활성화 전)', () => {
		renderWithRouter(<MerchantUserTable merchantUsers={[member({ status: 'INVITED', lastLoginAt: null })]} />)
		expect(screen.getByRole('button', { name: '종료' })).toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '정지' })).not.toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '역할 변경' })).not.toBeInTheDocument()
	})

	it('TERMINATED 계정에는 액션이 없다', () => {
		renderWithRouter(<MerchantUserTable merchantUsers={[member({ status: 'TERMINATED' })]} />)
		expect(screen.queryByRole('button', { name: '종료' })).not.toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '재개' })).not.toBeInTheDocument()
	})

	it('OWNER 행에는 역할 변경을 두지 않는다', () => {
		renderWithRouter(<MerchantUserTable merchantUsers={[member({ role: 'OWNER' })]} />)
		expect(screen.queryByRole('button', { name: '역할 변경' })).not.toBeInTheDocument()
	})

	it('자기 자신 행에는 액션 대신 "본인"을 보여준다', () => {
		// 서버도 403으로 막지만, 누를 수 있게 두고 거부하는 것보다 감추는 편이 낫다.
		renderWithRouter(<MerchantUserTable merchantUsers={[member()]} currentMerchantUserId="mu_001" />)
		expect(screen.getByText('본인')).toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '정지' })).not.toBeInTheDocument()
	})

	it('종료는 되돌릴 수 없다는 것을 확인 문구로 알린다', async () => {
		renderWithRouter(<MerchantUserTable merchantUsers={[member()]} />)
		await userEvent.click(screen.getByRole('button', { name: '종료' }))
		expect(screen.getByText(/되돌릴 수 없습니다/)).toBeInTheDocument()
	})

	it('역할 선택지에 OWNER가 없다', async () => {
		renderWithRouter(<MerchantUserTable merchantUsers={[member()]} />)
		await userEvent.click(screen.getByRole('button', { name: '역할 변경' }))
		const select = screen.getByLabelText('역할 선택')
		expect(select).toBeInTheDocument()
		expect(screen.queryByRole('option', { name: 'OWNER' })).not.toBeInTheDocument()
	})
})

describe('InviteSubAccountForm', () => {
	afterEach(() => {
		vi.unstubAllGlobals()
	})

	it('역할 선택지에 OWNER가 없다(하위 계정으로 만들 수 없다)', () => {
		renderWithRouter(<InviteSubAccountForm />)
		expect(screen.getByLabelText('ADMIN')).toBeInTheDocument()
		expect(screen.getByLabelText('VIEWER')).toBeInTheDocument()
		expect(screen.queryByLabelText('OWNER')).not.toBeInTheDocument()
	})

	it('초대에 성공하면 초대 링크와 "다시 볼 수 없다" 경고를 보여준다', async () => {
		document.cookie = 'XSRF-TOKEN=tok-123'
		vi.stubGlobal(
			'fetch',
			vi.fn().mockResolvedValue(
				fakeResponse(201, {
					merchantUserId: 'mu_002',
					loginId: 'new-admin',
					email: 'new-admin@example.com',
					userName: '새 계정',
					role: 'ADMIN',
					invitationToken: 'raw-invitation-token',
					invitationExpiresAt: '2026-07-26T00:00:00Z',
				}),
			),
		)

		renderWithRouter(<InviteSubAccountForm />)
		await userEvent.type(screen.getByLabelText('로그인 아이디'), 'new-admin')
		await userEvent.type(screen.getByLabelText('이메일'), 'new-admin@example.com')
		await userEvent.type(screen.getByLabelText('이름'), '새 계정')
		await userEvent.click(screen.getByRole('button', { name: '하위 계정 초대' }))

		expect(await screen.findByText(/다시 볼 수 없습니다/)).toBeInTheDocument()
		// 토큰 문자열이 아니라 바로 쓸 수 있는 링크를 보여준다.
		expect(screen.getByText(/\/accept-invitation\?token=raw-invitation-token/)).toBeInTheDocument()
	})
})
