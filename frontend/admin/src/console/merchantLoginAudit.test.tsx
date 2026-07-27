import { describe, expect, it } from 'vitest'
import { screen } from '@testing-library/react'
import { renderWithRouter } from '@/test-utils'
import { MerchantLoginAuditTable } from '@/console/MerchantLoginAuditTable'
import { ConsoleShell } from '@/console/ConsoleShell'
import type { MerchantLoginAuditEntry, MeResponse } from '@/api/types'

function entry(overrides: Partial<MerchantLoginAuditEntry> = {}): MerchantLoginAuditEntry {
	return {
		auditId: 'mla_001',
		merchantId: 'mrc_001',
		merchantName: '테스트 가맹점',
		attemptedMerchantCode: 'test-merchant',
		attemptedLoginId: 'owner01',
		userName: '오너',
		outcome: 'SUCCESS',
		clientIp: '203.0.113.7',
		occurredAt: '2026-07-19T00:00:00Z',
		...overrides,
	} as MerchantLoginAuditEntry
}

function me(role: string): MeResponse {
	return { internalUserId: 'iu_me', loginId: 'me01', role } as MeResponse
}

describe('MerchantLoginAuditTable', () => {
	it('성공 시도를 가맹점 이름·코드와 함께 보여준다', () => {
		renderWithRouter(<MerchantLoginAuditTable entries={[entry()]} />)
		expect(screen.getByText('테스트 가맹점')).toBeInTheDocument()
		expect(screen.getByText('test-merchant')).toBeInTheDocument()
		expect(screen.getByText('owner01')).toBeInTheDocument()
		expect(screen.getByText('성공')).toBeInTheDocument()
	})

	it('없는 가맹점 코드 시도는 "알 수 없는 가맹점"으로 구분해 보여준다', () => {
		renderWithRouter(
			<MerchantLoginAuditTable
				entries={[entry({ auditId: 'mla_002', merchantId: null, merchantName: null, attemptedMerchantCode: 'ghost', outcome: 'INVALID_CREDENTIALS' })]}
			/>,
		)
		expect(screen.getByText('알 수 없는 가맹점')).toBeInTheDocument()
		expect(screen.getByText('ghost')).toBeInTheDocument()
		expect(screen.getByText('실패')).toBeInTheDocument()
	})

	it('빈 목록은 안내 문구를 보여준다', () => {
		renderWithRouter(<MerchantLoginAuditTable entries={[]} />)
		expect(screen.getByText('아직 가맹점 로그인 기록이 없습니다.')).toBeInTheDocument()
	})
})

describe('ConsoleShell 가맹점 로그인 감사 내비 권한', () => {
	it('SUPER_ADMIN과 OPERATOR에게 "가맹점 로그인" 탭을 보여준다', () => {
		renderWithRouter(
			<ConsoleShell me={me('OPERATOR')}>
				<div />
			</ConsoleShell>,
		)
		expect(screen.getByRole('link', { name: '가맹점 로그인' })).toBeInTheDocument()
	})

	it('VIEWER에게는 "가맹점 로그인" 탭을 감춘다', () => {
		renderWithRouter(
			<ConsoleShell me={me('VIEWER')}>
				<div />
			</ConsoleShell>,
		)
		expect(screen.queryByRole('link', { name: '가맹점 로그인' })).not.toBeInTheDocument()
	})
})
