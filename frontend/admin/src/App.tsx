import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useMe } from '@/auth/useAuth'
import { AdminApiError } from '@/api/client'
import { canManageInternalUsers, canManageMerchantAccounts } from '@/api/types'
import { LoginPage } from '@/console/LoginPage'
import { ConsoleShell } from '@/console/ConsoleShell'
import { MerchantsPage } from '@/console/MerchantsPage'
import { MerchantDetailPage } from '@/console/MerchantDetailPage'
import { InternalUsersPage } from '@/console/InternalUsersPage'
import { LoginAuditPage } from '@/console/LoginAuditPage'
import { MerchantLoginAuditPage } from '@/console/MerchantLoginAuditPage'
import { AcceptInvitationPage } from '@/invitation/AcceptInvitationPage'
import { Button } from '@/components/ui/button'

/**
 * 라우트를 나눈다. **초대 수락(`/accept-invitation`)만 인증 게이트 밖에 있다** — 초대
 * 링크로 도달하는 내부 직원은 아직 계정이 활성화되지 않아 로그인할 수 없으므로, 이 경로가
 * 로그인 화면으로 튕기면 흐름 자체가 성립하지 않는다(merchant 콘솔과 같은 구조이고,
 * `routing.test.tsx`가 회귀로 지킨다).
 */
export default function App() {
	return (
		<Routes>
			<Route path="/accept-invitation" element={<AcceptInvitationPage />} />
			<Route path="*" element={<ConsoleRoutes />} />
		</Routes>
	)
}

function ConsoleRoutes() {
	const { data: me, isPending, isError, error, refetch } = useMe()

	if (isPending) {
		return <CenteredNotice>불러오는 중…</CenteredNotice>
	}

	// 401은 client가 null로 바꿔 주므로 여기 도달하지 않는다 — 네트워크/서버 오류만 온다.
	if (isError) {
		const message = error instanceof AdminApiError ? error.message : '콘솔 서버에 연결하지 못했습니다.'
		return (
			<CenteredNotice>
				<p className="text-destructive">{message}</p>
				<Button variant="outline" size="sm" onClick={() => void refetch()}>
					다시 시도
				</Button>
			</CenteredNotice>
		)
	}

	if (!me) {
		return <LoginPage />
	}

	return (
		<ConsoleShell me={me}>
			<Routes>
				<Route path="/" element={<MerchantsPage me={me} />} />
				<Route path="/merchants/:merchantId" element={<MerchantDetailPage me={me} />} />
				{/* 내부 직원 관리는 SUPER_ADMIN 전용 — 서버도 403이므로 라우트 자체를 막는다. */}
				{canManageInternalUsers(String(me.role)) && (
					<Route path="/internal-users" element={<InternalUsersPage />} />
				)}
				{canManageInternalUsers(String(me.role)) && (
					<Route path="/login-audit" element={<LoginAuditPage />} />
				)}
				{canManageMerchantAccounts(String(me.role)) && (
					<Route path="/merchant-login-audit" element={<MerchantLoginAuditPage />} />
				)}
				<Route path="*" element={<Navigate to="/" replace />} />
			</Routes>
		</ConsoleShell>
	)
}

function CenteredNotice({ children }: { children: ReactNode }) {
	return (
		<div className="flex min-h-dvh flex-col items-center justify-center gap-3 p-6 text-sm text-muted-foreground">
			{children}
		</div>
	)
}
