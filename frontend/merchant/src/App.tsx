import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useMe } from '@/auth/useAuth'
import { MerchantApiError } from '@/api/client'
import { LoginPage } from '@/console/LoginPage'
import { ConsoleShell } from '@/console/ConsoleShell'
import { ApiKeysPage } from '@/console/ApiKeysPage'
import { PaymentDetailPage } from '@/console/PaymentDetailPage'
import { PaymentsPage } from '@/console/PaymentsPage'
import { SettlementPage } from '@/console/SettlementPage'
import { TeamPage } from '@/console/TeamPage'
import { WebhookPage } from '@/console/WebhookPage'
import { AcceptInvitationPage } from '@/invitation/AcceptInvitationPage'
import { Button } from '@/components/ui/button'

/**
 * 라우트를 나눈다. **초대 수락(`/accept-invitation`)만 인증 게이트 밖에 있다** —
 * 초대 링크로 도달하는 사람은 아직 계정이 활성화되지 않아 로그인할 수 없으므로,
 * 이 경로가 로그인 화면으로 튕기면 흐름 자체가 성립하지 않는다. 이 슬라이스에서
 * 가장 깨지기 쉬운 지점이라 `routing.test.tsx`가 회귀로 지킨다.
 *
 * 나머지 경로는 [ConsoleRoutes]가 `useMe()`로 게이트한다 — **다음 상태를 스스로
 * 추론하지 않고** 서버의 `GET /merchant/me` 결과(사용자 or 401→null)만 따른다.
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
		const message = error instanceof MerchantApiError ? error.message : '콘솔 서버에 연결하지 못했습니다.'
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
				<Route path="/" element={<ApiKeysPage />} />
				<Route path="/team" element={<TeamPage />} />
				<Route path="/webhook" element={<WebhookPage />} />
				<Route path="/payments" element={<PaymentsPage />} />
				<Route path="/settlements" element={<SettlementPage />} />
				<Route path="/payments/:paymentId" element={<PaymentDetailPage />} />
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
