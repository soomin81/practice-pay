import type { ReactNode } from 'react'
import { Building2, CreditCard, ScrollText, ShieldCheck, UserCog, UserSearch, Wallet } from 'lucide-react'
import { useLogout } from '@/auth/useAuth'
import { canManageInternalUsers, canManageMerchantAccounts, canSearchPaymentCustomers, type MeResponse } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Sidebar, type NavGroup } from '@/components/console/Sidebar'

/** 로그인 후 콘솔의 바깥 틀(merchant 앱의 `ConsoleShell`과 같은 모양). */
export function ConsoleShell({ me, children }: { me: MeResponse; children: ReactNode }) {
	const logout = useLogout()
	const role = String(me.role)

	// **역할에 따라 그룹째로 사라진다.** 상단 가로 네비였을 때는 항목만 감췄는데,
	// 사이드바에서는 감사 항목이 전부 빠지면 "통제" 그룹에 제목만 남는다 — 항목이
	// 없는 그룹은 아예 그리지 않는다.
	const groups: NavGroup[] = [
		{
			label: '운영',
			items: [
				{ to: '/', label: '가맹점', icon: <Building2 className="size-4" /> },
				{ to: '/payments', label: '결제 내역', icon: <CreditCard className="size-4" /> },
				// 구매자 조회는 SUPER_ADMIN/OPERATOR다 — VIEWER에게는 메뉴 자체를 감춘다(서버도 403).
				...(canSearchPaymentCustomers(role)
					? [{ to: '/payment-customers', label: '구매자 조회', icon: <UserSearch className="size-4" /> }]
					: []),
			],
		},
		{
			label: '자금',
			items: [{ to: '/settlements', label: '정산', icon: <Wallet className="size-4" /> }],
		},
		{
			label: '통제',
			items: [
				// 내부 직원 관리·로그인 감사는 SUPER_ADMIN 전용이다.
				...(canManageInternalUsers(role)
					? [
							{ to: '/internal-users', label: '내부 직원', icon: <UserCog className="size-4" /> },
							{ to: '/login-audit', label: '로그인 감사', icon: <ShieldCheck className="size-4" /> },
						]
					: []),
				// 가맹점 로그인 감사는 SUPER_ADMIN/OPERATOR가 본다(가맹점 업무 담당).
				...(canManageMerchantAccounts(role)
					? [{ to: '/merchant-login-audit', label: '가맹점 로그인', icon: <ScrollText className="size-4" /> }]
					: []),
			],
		},
	].filter((group) => group.items.length > 0)

	return (
		<div className="flex min-h-dvh">
			<Sidebar
				brand="PracticePay"
				subtitle="내부 운영자 콘솔"
				initials="PP"
				groups={groups}
				footer={
					<div className="flex items-center justify-between gap-2 px-1.5 py-1">
						<span className="min-w-0">
							<span className="block truncate text-sm text-sidebar-foreground">{me.loginId}</span>
							<span className="block truncate text-xs text-sidebar-foreground/60">{role}</span>
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
