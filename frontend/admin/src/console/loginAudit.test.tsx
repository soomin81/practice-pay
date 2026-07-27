import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithRouter } from '@/test-utils'
import { LoginAuditTable } from '@/console/LoginAuditTable'
import { ConsoleShell } from '@/console/ConsoleShell'
import type { LoginAuditEntry, MeResponse } from '@/api/types'

function entry(overrides: Partial<LoginAuditEntry> = {}): LoginAuditEntry {
	return {
		auditId: 'ila_001',
		internalUserId: 'iu_001',
		attemptedLoginId: 'operator01',
		userName: '운영자',
		outcome: 'SUCCESS',
		clientIp: '203.0.113.7',
		occurredAt: '2026-07-19T00:00:00Z',
		...overrides,
	} as LoginAuditEntry
}

function me(role: string): MeResponse {
	return { internalUserId: 'iu_me', loginId: 'me01', role } as MeResponse
}

describe('LoginAuditTable', () => {
	it('성공 시도를 서버 값 그대로 보여준다', () => {
		renderWithRouter(<LoginAuditTable entries={[entry()]} />)
		expect(screen.getByText('operator01')).toBeInTheDocument()
		expect(screen.getByText('운영자')).toBeInTheDocument()
		expect(screen.getByText('성공')).toBeInTheDocument()
		expect(screen.getByText('203.0.113.7')).toBeInTheDocument()
	})

	it('실패 시도는 "실패" 배지로 보여준다', () => {
		renderWithRouter(<LoginAuditTable entries={[entry({ auditId: 'ila_002', outcome: 'INVALID_CREDENTIALS' })]} />)
		expect(screen.getByText('실패')).toBeInTheDocument()
	})

	it('없는 계정 시도는 "알 수 없는 계정"으로 구분해 보여준다', () => {
		renderWithRouter(
			<LoginAuditTable
				entries={[entry({ auditId: 'ila_003', internalUserId: null, userName: null, attemptedLoginId: 'ghost', clientIp: null })]}
			/>,
		)
		expect(screen.getByText('알 수 없는 계정')).toBeInTheDocument()
		expect(screen.getByText('ghost')).toBeInTheDocument()
	})

	it('빈 목록은 안내 문구를 보여준다', () => {
		renderWithRouter(<LoginAuditTable entries={[]} />)
		expect(screen.getByText('아직 로그인 기록이 없습니다.')).toBeInTheDocument()
	})
})

describe('ConsoleShell 로그인 감사 내비 권한', () => {
	it('SUPER_ADMIN에게만 "로그인 감사" 탭을 보여준다', () => {
		renderWithRouter(
			<ConsoleShell me={me('SUPER_ADMIN')}>
				<div />
			</ConsoleShell>,
		)
		expect(screen.getByRole('link', { name: '로그인 감사' })).toBeInTheDocument()
	})

	it('OPERATOR에게는 "로그인 감사" 탭을 감춘다', () => {
		renderWithRouter(
			<ConsoleShell me={me('OPERATOR')}>
				<div />
			</ConsoleShell>,
		)
		expect(screen.queryByRole('link', { name: '로그인 감사' })).not.toBeInTheDocument()
	})
})
