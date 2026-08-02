import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '@/test-utils'
import { internalInvitationUrlFor, merchantInvitationUrlFor } from '@/console/format'
import { InternalUserTable } from '@/console/InternalUserTable'
import { IssueInternalUserForm } from '@/console/IssueInternalUserForm'
import { ConsoleShell } from '@/console/ConsoleShell'
import type { InternalUserSummary, MeResponse } from '@/api/types'

function member(overrides: Partial<InternalUserSummary> = {}): InternalUserSummary {
	return {
		internalUserId: 'iu_001',
		loginId: 'operator01',
		email: 'operator01@example.com',
		userName: '운영자',
		role: 'OPERATOR',
		status: 'ACTIVE',
		lastLoginAt: '2026-07-19T01:00:00Z',
		createdAt: '2026-07-19T00:00:00Z',
		...overrides,
	} as InternalUserSummary
}

function me(role: string): MeResponse {
	return { internalUserId: 'iu_me', loginId: 'me01', role } as MeResponse
}

function fakeResponse(status: number, body: unknown = {}) {
	return { ok: status >= 200 && status < 300, status, json: async () => body }
}

describe('초대 링크가 가리키는 콘솔', () => {
	it('내부 직원 링크는 이 콘솔(현재 origin)을, 가맹점 링크는 가맹점 콘솔을 가리킨다', () => {
		// 이 앱에서 가장 틀리기 쉬운 지점 — 둘을 바꿔 쓰면 상대가 열 수 없는 링크가 되는데
		// 화면상으로는 멀쩡해 보인다.
		const internal = internalInvitationUrlFor('tok')
		const merchant = merchantInvitationUrlFor('tok')

		expect(internal.startsWith(window.location.origin)).toBe(true)
		expect(merchant.startsWith('http://localhost:5174')).toBe(true)
		expect(merchant).not.toContain(window.location.origin)
		expect(internal).not.toBe(merchant)
	})
})

describe('InternalUserTable', () => {
	it('서버 값을 그대로 보여준다', () => {
		renderWithRouter(<InternalUserTable internalUsers={[member()]} />)
		expect(screen.getByText('operator01')).toBeInTheDocument()
		expect(screen.getByText('operator01@example.com')).toBeInTheDocument()
		expect(screen.getByText('활성')).toBeInTheDocument()
	})

	it('INVITED 계정을 구분해 보여주고 로그인 이력이 없으면 대시로 그린다', () => {
		renderWithRouter(
			<InternalUserTable internalUsers={[member({ status: 'INVITED', lastLoginAt: null })]} />,
		)
		expect(screen.getByText('초대됨')).toBeInTheDocument()
		expect(screen.getByText('—')).toBeInTheDocument()
	})

	it('빈 명부는 안내 문구를 보여준다', () => {
		renderWithRouter(<InternalUserTable internalUsers={[]} />)
		expect(screen.getByText('아직 등록된 내부 직원이 없습니다.')).toBeInTheDocument()
	})
})

describe('InternalUserTable 행 액션', () => {
	it('ACTIVE 계정에는 정지·종료·역할 변경이 있다', () => {
		renderWithRouter(<InternalUserTable internalUsers={[member()]} />)
		expect(screen.getByRole('button', { name: '정지' })).toBeInTheDocument()
		expect(screen.getByRole('button', { name: '종료' })).toBeInTheDocument()
		expect(screen.getByRole('button', { name: '역할 변경' })).toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '재개' })).not.toBeInTheDocument()
	})

	it('SUSPENDED 계정에는 재개가 있고 정지는 없다', () => {
		renderWithRouter(<InternalUserTable internalUsers={[member({ status: 'SUSPENDED' })]} />)
		expect(screen.getByRole('button', { name: '재개' })).toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '정지' })).not.toBeInTheDocument()
	})

	it('INVITED 계정에는 종료만 있고 정지·역할 변경은 없다', () => {
		// 내부 운영자에는 초대 재발송·취소가 아직 없다(가맹점 콘솔에만 있다).
		renderWithRouter(<InternalUserTable internalUsers={[member({ status: 'INVITED', lastLoginAt: null })]} />)
		expect(screen.getByRole('button', { name: '종료' })).toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '정지' })).not.toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '역할 변경' })).not.toBeInTheDocument()
	})

	it('TERMINATED 계정에는 액션이 없다', () => {
		renderWithRouter(<InternalUserTable internalUsers={[member({ status: 'TERMINATED' })]} />)
		expect(screen.queryByRole('button', { name: '종료' })).not.toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '재개' })).not.toBeInTheDocument()
	})

	it('SUPER_ADMIN 행에는 역할 변경을 두지 않는다', () => {
		// 강등은 마지막 SUPER_ADMIN 보호에 걸리기 쉽고 승격은 불가능하다 — 화면에서 미리 뺐다.
		renderWithRouter(<InternalUserTable internalUsers={[member({ role: 'SUPER_ADMIN' })]} />)
		expect(screen.queryByRole('button', { name: '역할 변경' })).not.toBeInTheDocument()
	})

	it('자기 자신 행에는 액션 대신 "본인"을 보여준다', () => {
		// 서버도 403으로 막지만, 누를 수 있게 두고 거부하는 것보다 감추는 편이 낫다.
		renderWithRouter(<InternalUserTable internalUsers={[member()]} currentInternalUserId="iu_001" />)
		expect(screen.getByText('본인')).toBeInTheDocument()
		expect(screen.queryByRole('button', { name: '정지' })).not.toBeInTheDocument()
	})

	it('종료는 되돌릴 수 없다는 것을 확인 문구로 알린다', async () => {
		renderWithRouter(<InternalUserTable internalUsers={[member()]} />)
		await userEvent.click(screen.getByRole('button', { name: '종료' }))
		expect(screen.getByText(/되돌릴 수 없습니다/)).toBeInTheDocument()
	})

	it('역할 선택지에 SUPER_ADMIN이 없다', async () => {
		renderWithRouter(<InternalUserTable internalUsers={[member()]} />)
		await userEvent.click(screen.getByRole('button', { name: '역할 변경' }))
		expect(screen.getByLabelText('역할 선택')).toBeInTheDocument()
		expect(screen.queryByRole('option', { name: 'SUPER_ADMIN' })).not.toBeInTheDocument()
		expect(screen.getByRole('option', { name: 'OPERATOR' })).toBeInTheDocument()
		expect(screen.getByRole('option', { name: 'VIEWER' })).toBeInTheDocument()
	})
})

describe('IssueInternalUserForm', () => {
	afterEach(() => {
		vi.unstubAllGlobals()
	})

	it('역할 선택지에 SUPER_ADMIN이 없다(Bootstrap으로만 생성)', () => {
		renderWithRouter(<IssueInternalUserForm />)
		expect(screen.getByLabelText('OPERATOR')).toBeInTheDocument()
		expect(screen.getByLabelText('VIEWER')).toBeInTheDocument()
		expect(screen.queryByLabelText('SUPER_ADMIN')).not.toBeInTheDocument()
	})

	it('발급에 성공하면 이 콘솔 초대 링크를 1회 노출한다', async () => {
		document.cookie = 'XSRF-TOKEN=tok-123'
		vi.stubGlobal(
			'fetch',
			vi.fn().mockResolvedValue(
				fakeResponse(201, {
					internalUserId: 'iu_002',
					loginId: 'new-operator',
					email: 'new-operator@example.com',
					userName: '새 운영자',
					role: 'OPERATOR',
					invitationToken: 'raw-invitation-token',
					invitationExpiresAt: '2026-07-26T00:00:00Z',
				}),
			),
		)

		renderWithRouter(<IssueInternalUserForm />)
		await userEvent.type(screen.getByLabelText('로그인 아이디'), 'new-operator')
		await userEvent.type(screen.getByLabelText('이메일'), 'new-operator@example.com')
		await userEvent.type(screen.getByLabelText('이름'), '새 운영자')
		await userEvent.click(screen.getByRole('button', { name: '내부 직원 초대' }))

		expect(await screen.findByText(/다시 볼 수 없습니다/)).toBeInTheDocument()
		// 내부 직원 링크는 이 콘솔(현재 origin)이어야 한다 — 5174가 아니다.
		expect(screen.getByText(new RegExp(`${window.location.origin}/accept-invitation`))).toBeInTheDocument()
	})
})

describe('ConsoleShell 내비 권한', () => {
	it('SUPER_ADMIN에게만 "내부 직원" 탭을 보여준다', () => {
		renderWithRouter(
			<ConsoleShell me={me('SUPER_ADMIN')}>
				<div />
			</ConsoleShell>,
		)
		expect(screen.getByRole('link', { name: '내부 직원' })).toBeInTheDocument()
	})

	it('OPERATOR에게는 "내부 직원" 탭을 감춘다', () => {
		renderWithRouter(
			<ConsoleShell me={me('OPERATOR')}>
				<div />
			</ConsoleShell>,
		)
		expect(screen.queryByRole('link', { name: '내부 직원' })).not.toBeInTheDocument()
		expect(screen.getByRole('link', { name: '가맹점' })).toBeInTheDocument()
	})
})
