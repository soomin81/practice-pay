import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { useLogout } from '@/auth/useAuth'
import type { MeResponse } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'

/**
 * 로그인 후 콘솔의 바깥 틀. 상단 바에 현재 사용자(loginId + 역할)와 로그아웃 버튼을
 * 두고, 아래에 페이지 내용을 담는다.
 *
 * 표시 값은 `MeResponse`가 주는 것(`loginId`/`role`)으로 한정한다 — `userName`은 이
 * 슬라이스의 `/merchant/me`에 없다(그 컨트롤러의 단순화 주석 참고).
 */
export function ConsoleShell({ me, children }: { me: MeResponse; children: ReactNode }) {
	const logout = useLogout()

	return (
		<div className="min-h-dvh">
			<header className="border-b">
				<div className="mx-auto flex max-w-4xl items-center justify-between gap-3 px-6 py-3">
					<span className="font-heading text-base font-medium">가맹점 콘솔</span>
					<div className="flex items-center gap-3 text-sm">
						<span className="text-muted-foreground">{me.loginId}</span>
						<Badge variant="secondary">{me.role}</Badge>
						<Button
							variant="outline"
							size="sm"
							onClick={() => logout.mutate()}
							disabled={logout.isPending}
						>
							로그아웃
						</Button>
					</div>
				</div>
				<nav className="mx-auto flex max-w-4xl gap-1 px-6 pb-2">
					<ConsoleNavLink to="/">API Key</ConsoleNavLink>
					<ConsoleNavLink to="/team">팀 계정</ConsoleNavLink>
				</nav>
			</header>
			<main className="mx-auto max-w-4xl px-6 py-6">{children}</main>
		</div>
	)
}

/** 현재 경로면 강조한다. `end`를 줘서 "/"가 하위 경로에서도 활성으로 남지 않게 한다. */
function ConsoleNavLink({ to, children }: { to: string; children: ReactNode }) {
	return (
		<NavLink
			to={to}
			end
			className={({ isActive }) =>
				`rounded-md px-2.5 py-1.5 text-sm transition-colors ${
					isActive ? 'bg-muted font-medium text-foreground' : 'text-muted-foreground hover:text-foreground'
				}`
			}
		>
			{children}
		</NavLink>
	)
}
