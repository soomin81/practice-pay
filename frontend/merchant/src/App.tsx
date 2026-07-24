import type { ReactNode } from 'react'
import { useMe } from '@/auth/useAuth'
import { MerchantApiError } from '@/api/client'
import { LoginPage } from '@/console/LoginPage'
import { ConsoleShell } from '@/console/ConsoleShell'
import { ApiKeysPage } from '@/console/ApiKeysPage'
import { Button } from '@/components/ui/button'

/**
 * 인증 상태로 화면을 나눈다 — 라우터는 두지 않는다(payment가 단일 화면이라 라우터를
 * 뺀 판단과 같은 결). `useMe()`가 `null`이면 로그인, 사용자가 있으면 콘솔이다. 다음
 * 슬라이스에서 페이지가 늘면 그때 react-router를 도입한다.
 *
 * **다음 상태를 스스로 추론하지 않는다** — 로그인/로그아웃 여부는 서버의
 * `GET /merchant/me` 응답(사용자 or 401)이 정하고, 화면은 그 결과만 따른다.
 */
export default function App() {
	const { data: me, isPending, isError, error, refetch } = useMe()

	if (isPending) {
		return <CenteredNotice>불러오는 중…</CenteredNotice>
	}

	// 401은 client가 null로 바꿔 주므로 여기 도달하지 않는다 — 네트워크/서버 오류만 온다.
	if (isError) {
		const message =
			error instanceof MerchantApiError ? error.message : '콘솔 서버에 연결하지 못했습니다.'
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
			<ApiKeysPage />
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
