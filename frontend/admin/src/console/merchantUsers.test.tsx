import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '@/test-utils'
import { AdminMerchantUserTable } from '@/console/AdminMerchantUserTable'
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
		pendingInvitationExpiresAt: null,
		...overrides,
	} as MerchantUserSummary
}

function renderTable(users: MerchantUserSummary[], canManage = true) {
	return renderWithRouter(<AdminMerchantUserTable merchantId="mrc_001" merchantUsers={users} canManage={canManage} />)
}

describe('AdminMerchantUserTable', () => {
	it('서버 값을 그대로 보여준다', () => {
		renderTable([member()])
		expect(screen.getByText('member01')).toBeInTheDocument()
		expect(screen.getByText('member01@example.com')).toBeInTheDocument()
		expect(screen.getByText('ACTIVE')).toBeInTheDocument()
	})

	it('빈 명부는 안내 문구를 보여준다', () => {
		renderTable([])
		expect(screen.getByText('아직 등록된 사용자가 없습니다.')).toBeInTheDocument()
	})
})

describe('AdminMerchantUserTable 행 액션', () => {
	it('ACTIVE 계정에는 정지·종료·역할 변경이 있다', () => {
		renderTable([member()])
		expect(screen.getByRole('button', { name: '정지' })).toBeInTheDocument()
		expect(screen.getByRole('button', { name: '종료' })).toBeInTheDocument()
		expect(screen.getByRole('button', { name: '역할 변경' })).toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '재개' })).not.toBeInTheDocument()
	})

	it('SUSPENDED 계정에는 재개가 있고 정지는 없다', () => {
		renderTable([member({ status: 'SUSPENDED' })])
		expect(screen.getByRole('button', { name: '재개' })).toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '정지' })).not.toBeInTheDocument()
	})

	it('INVITED 계정에는 종료만 있다', () => {
		renderTable([member({ status: 'INVITED', lastLoginAt: null })])
		expect(screen.getByRole('button', { name: '종료' })).toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '정지' })).not.toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '역할 변경' })).not.toBeInTheDocument()
	})

	it('TERMINATED 계정에는 액션이 없다', () => {
		renderTable([member({ status: 'TERMINATED' })])
		expect(screen.queryByRole('button', { name: '종료' })).not.toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '재개' })).not.toBeInTheDocument()
	})

	it('OWNER 행에는 역할 변경을 두지 않는다', () => {
		// 강등이 마지막 OWNER 보호에 걸리기 쉽고 승격은 불가능하다 — 화면에서 미리 뺐다.
		renderTable([member({ role: 'OWNER' })])
		expect(screen.queryByRole('button', { name: '역할 변경' })).not.toBeInTheDocument()
	})

	it('역할 선택지에 OWNER가 없다', async () => {
		renderTable([member()])
		await userEvent.click(screen.getByRole('button', { name: '역할 변경' }))
		expect(screen.getByLabelText('역할 선택')).toBeInTheDocument()
		expect(screen.queryByRole('option', { name: 'OWNER' })).not.toBeInTheDocument()
		expect(screen.getByRole('option', { name: 'ADMIN' })).toBeInTheDocument()
		expect(screen.getByRole('option', { name: 'VIEWER' })).toBeInTheDocument()
	})

	it('종료는 되돌릴 수 없다는 것을 확인 문구로 알린다', async () => {
		renderTable([member()])
		await userEvent.click(screen.getByRole('button', { name: '종료' }))
		expect(screen.getByText(/되돌릴 수 없습니다/)).toBeInTheDocument()
	})

	it('canManage=false(VIEWER)면 액션을 아예 그리지 않는다', () => {
		renderTable([member()], false)
		expect(screen.getByText('member01')).toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '정지' })).not.toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '종료' })).not.toBeInTheDocument()
	})
})
