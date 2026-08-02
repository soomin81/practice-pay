import type { ReactNode } from 'react'
import { CreditCard, KeyRound, Users, Wallet, Webhook } from 'lucide-react'
import { useLogout } from '@/auth/useAuth'
import type { MeResponse } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Sidebar, type NavGroup } from '@/components/console/Sidebar'

/**
 * 로그인 후 콘솔의 바깥 틀 — 좌측 고정 사이드바 + 밝은 본문.
 *
 * 표시 값은 `MeResponse`가 주는 것(`loginId`/`role`)으로 한정한다 — `userName`은 이
 * 슬라이스의 `/merchant/me`에 없다(그 컨트롤러의 단순화 주석 참고).
 */
export function ConsoleShell({ me, children }: { me: MeResponse; children: ReactNode }) {
	const logout = useLogout()

	// **"자금"과 "설정"을 나눈다.** 결제·정산은 매일 보는 값이고, API Key·Webhook·팀
	// 계정은 한 번 맞춰 두고 잘 건드리지 않는 것이다 — 성격이 달라서 같은 목록에
	// 섞여 있으면 매번 눈으로 걸러내야 했다.
	const groups: NavGroup[] = [
		{
			label: '자금',
			items: [
				{ to: '/payments', label: '결제 내역', icon: <CreditCard className="size-4" /> },
				{ to: '/settlements', label: '정산', icon: <Wallet className="size-4" /> },
			],
		},
		{
			label: '설정',
			items: [
				{ to: '/', label: 'API Key', icon: <KeyRound className="size-4" /> },
				{ to: '/webhook', label: 'Webhook', icon: <Webhook className="size-4" /> },
				{ to: '/team', label: '팀 계정', icon: <Users className="size-4" /> },
			],
		},
	]

	return (
		<div className="flex min-h-dvh">
			<Sidebar
				brand="PracticePay"
				subtitle="가맹점 콘솔"
				initials="PP"
				groups={groups}
				footer={
					<div className="flex items-center justify-between gap-2 px-1.5 py-1">
						<span className="min-w-0">
							<span className="block truncate text-sm text-sidebar-foreground">{me.loginId}</span>
							<span className="block truncate text-xs text-sidebar-foreground/60">{me.role}</span>
						</span>
						<Button
							variant="ghost"
							size="sm"
							onClick={() => logout.mutate()}
							disabled={logout.isPending}
							className="shrink-0 text-sidebar-foreground/70 hover:bg-sidebar-accent hover:text-sidebar-foreground"
						>
							로그아웃
						</Button>
					</div>
				}
			/>
			<main className="min-w-0 flex-1 px-8 py-7">
				<div className="mx-auto max-w-6xl">{children}</div>
			</main>
		</div>
	)
}
