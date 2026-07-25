import { afterEach, describe, expect, it, vi } from 'vitest'
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { renderWithRouter } from '@/test-utils'
import { merchantInvitationUrlFor } from '@/console/format'
import { MerchantTable } from '@/console/MerchantTable'
import { MerchantsPage } from '@/console/MerchantsPage'
import { RegisterMerchantForm } from '@/console/RegisterMerchantForm'
import type { MeResponse, MerchantSummary } from '@/api/types'

function merchant(overrides: Partial<MerchantSummary> = {}): MerchantSummary {
	return {
		merchantId: 'mrc_001',
		merchantCode: 'TEST_MERCHANT',
		merchantName: '테스트 가맹점',
		status: 'ACTIVE',
		createdAt: '2026-07-19T00:00:00Z',
		...overrides,
	} as MerchantSummary
}

function me(role: string): MeResponse {
	return { internalUserId: 'iu_001', loginId: 'operator01', role } as MeResponse
}

function fakeResponse(status: number, body: unknown = {}) {
	return { ok: status >= 200 && status < 300, status, json: async () => body }
}

describe('merchantInvitationUrlFor', () => {
	it('가맹점 콘솔 주소로 링크를 만든다(이 콘솔 주소가 아니다)', () => {
		// 이 슬라이스에서 가장 틀리기 쉬운 지점 — OWNER가 활성화할 곳은 merchant 콘솔이다.
		const url = merchantInvitationUrlFor('raw-token')
		expect(url).toContain('/accept-invitation?token=raw-token')
		expect(url.startsWith('http://localhost:5174')).toBe(true)
		// 테스트 환경(jsdom)의 origin이 섞여 들어가면 안 된다.
		expect(url).not.toContain(window.location.origin)
	})
})

describe('MerchantTable', () => {
	it('서버 값을 그대로 보여준다', () => {
		renderWithRouter(<MerchantTable merchants={[merchant()]} />)
		expect(screen.getByText('TEST_MERCHANT')).toBeInTheDocument()
		expect(screen.getByText('테스트 가맹점')).toBeInTheDocument()
		expect(screen.getByText('ACTIVE')).toBeInTheDocument()
	})

	it('빈 목록은 안내 문구를 보여준다', () => {
		renderWithRouter(<MerchantTable merchants={[]} />)
		expect(screen.getByText('아직 등록된 가맹점이 없습니다.')).toBeInTheDocument()
	})
})

describe('MerchantsPage 권한 분기', () => {
	afterEach(() => {
		vi.unstubAllGlobals()
	})

	it('VIEWER에게는 등록 폼을 보여주지 않는다', () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fakeResponse(200, { merchants: [] })))
		renderWithRouter(<MerchantsPage me={me('VIEWER')} />)
		expect(screen.queryByRole('button', { name: '가맹점 등록' })).not.toBeInTheDocument()
	})

	it('OPERATOR에게는 등록 폼을 보여준다', () => {
		vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fakeResponse(200, { merchants: [] })))
		renderWithRouter(<MerchantsPage me={me('OPERATOR')} />)
		expect(screen.getByRole('button', { name: '가맹점 등록' })).toBeInTheDocument()
	})
})

describe('RegisterMerchantForm', () => {
	afterEach(() => {
		vi.unstubAllGlobals()
	})

	it('등록에 성공하면 가맹점 콘솔 초대 링크를 1회 노출한다', async () => {
		document.cookie = 'XSRF-TOKEN=tok-123'
		vi.stubGlobal(
			'fetch',
			vi.fn().mockResolvedValue(
				fakeResponse(201, {
					merchantId: 'mrc_002',
					merchantCode: 'NEW_MERCHANT',
					merchantName: '새 가맹점',
					ownerMerchantUserId: 'mu_001',
					ownerLoginId: 'new-owner',
					ownerEmail: 'new-owner@example.com',
					invitationToken: 'raw-invitation-token',
					invitationExpiresAt: '2026-07-26T00:00:00Z',
				}),
			),
		)

		renderWithRouter(<RegisterMerchantForm />)
		await userEvent.type(screen.getByLabelText('가맹점 코드'), 'NEW_MERCHANT')
		await userEvent.type(screen.getByLabelText('가맹점 이름'), '새 가맹점')
		await userEvent.type(screen.getByLabelText('로그인 아이디'), 'new-owner')
		await userEvent.type(screen.getByLabelText('이메일'), 'new-owner@example.com')
		await userEvent.type(screen.getByLabelText('이름'), '새 오너')
		await userEvent.click(screen.getByRole('button', { name: '가맹점 등록' }))

		expect(await screen.findByText(/다시 볼 수 없습니다/)).toBeInTheDocument()
		// 링크가 merchant 콘솔(5174)을 가리켜야 한다.
		expect(screen.getByText(/localhost:5174\/accept-invitation\?token=raw-invitation-token/)).toBeInTheDocument()
	})
})
