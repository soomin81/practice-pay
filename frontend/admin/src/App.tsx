import type { ReactNode } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { useMe } from '@/auth/useAuth'
import { AdminApiError } from '@/api/client'
import { LoginPage } from '@/console/LoginPage'
import { ConsoleShell } from '@/console/ConsoleShell'
import { MerchantsPage } from '@/console/MerchantsPage'
import { Button } from '@/components/ui/button'

/**
 * 인증 상태로 화면을 나눈다 — 서버의 `GET /admin/me` 결과(사용자 or 401→null)만 따르고
 * 다음 상태를 스스로 추론하지 않는다(merchant 콘솔과 같은 구조).
 *
 * 지금은 페이지가 하나뿐이라 라우터가 꼭 필요하진 않지만, 다음 슬라이스(내부 직원 계정
 * 발급 등)에서 바로 늘어나고 merchant가 이미 같은 구조라 처음부터 넣었다.
 */
export default function App() {
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
