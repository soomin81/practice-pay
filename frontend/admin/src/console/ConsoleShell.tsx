import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'
import { useLogout } from '@/auth/useAuth'
import { canManageInternalUsers, canManageMerchantAccounts, type MeResponse } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'

/** 로그인 후 콘솔의 바깥 틀(merchant 앱의 `ConsoleShell`과 같은 모양). */
export function ConsoleShell({ me, children }: { me: MeResponse; children: ReactNode }) {
	const logout = useLogout()

	return (
		<div className="min-h-dvh">
			<header className="border-b">
				<div className="mx-auto flex max-w-5xl items-center justify-between gap-3 px-6 py-3">
					<span className="font-heading text-base font-medium">내부 운영자 콘솔</span>
					<div className="flex items-center gap-3 text-sm">
						<span className="text-muted-foreground">{me.loginId}</span>
						<Badge variant="secondary">{String(me.role)}</Badge>
						<Button variant="outline" size="sm" onClick={() => logout.mutate()} disabled={logout.isPending}>
							로그아웃
						</Button>
					</div>
				</div>
				<nav className="mx-auto flex max-w-5xl gap-1 px-6 pb-2">
					<ConsoleNavLink to="/">가맹점</ConsoleNavLink>
					{/* 내부 직원 관리·로그인 감사는 SUPER_ADMIN 전용이라 다른 역할에게는 탭 자체를 감춘다. */}
					{canManageInternalUsers(String(me.role)) && (
						<>
							<ConsoleNavLink to="/internal-users">내부 직원</ConsoleNavLink>
							<ConsoleNavLink to="/login-audit">로그인 감사</ConsoleNavLink>
						</>
					)}
					{/* 가맹점 로그인 감사는 SUPER_ADMIN/OPERATOR가 본다(가맹점 업무 담당). */}
					{canManageMerchantAccounts(String(me.role)) && (
						<ConsoleNavLink to="/merchant-login-audit">가맹점 로그인</ConsoleNavLink>
					)}
				</nav>
			</header>
			<main className="mx-auto max-w-5xl px-6 py-6">{children}</main>
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
