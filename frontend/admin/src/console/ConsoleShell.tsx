import type { ReactNode } from 'react'
import { useLogout } from '@/auth/useAuth'
import type { MeResponse } from '@/api/types'
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
			</header>
			<main className="mx-auto max-w-5xl px-6 py-6">{children}</main>
		</div>
	)
}
