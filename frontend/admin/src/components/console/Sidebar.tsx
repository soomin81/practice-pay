import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'

/**
 * 좌측 고정 사이드바 — 브랜드, 그룹으로 묶인 메뉴, 하단 사용자 블록.
 *
 * **메뉴를 그룹으로 묶는 것이 이 레이아웃의 핵심**이다. 상단 가로 네비였을 때는
 * 항목이 평평하게 나열돼 "API Key"와 "정산"이 같은 무게로 보였는데, 실제로는 성격이
 * 다르다 — 그래서 참고 디자인처럼 운영/자금/설정으로 나눠 어느 갈래의 일인지 먼저
 * 알 수 있게 했다.
 *
 * 사이드바는 테마와 무관하게 항상 어둡다(`index.css`의 `--sidebar*` 주석 참고).
 */
export type NavItem = { to: string; label: string; icon: ReactNode; badge?: ReactNode }
export type NavGroup = { label: string; items: NavItem[] }

export function Sidebar({
	brand,
	subtitle,
	initials,
	groups,
	footer,
}: {
	brand: string
	subtitle: string
	initials: string
	groups: NavGroup[]
	footer: ReactNode
}) {
	return (
		<aside className="flex w-60 shrink-0 flex-col bg-sidebar text-sidebar-foreground">
			<div className="flex items-center gap-3 px-5 py-5">
				<span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-sidebar-primary text-sm font-semibold text-sidebar-primary-foreground">
					{initials}
				</span>
				<span className="min-w-0">
					<span className="block truncate font-heading text-base font-semibold">{brand}</span>
					<span className="block truncate text-xs text-sidebar-foreground/60">{subtitle}</span>
				</span>
			</div>

			<nav className="flex-1 overflow-y-auto px-3 py-2">
				{groups.map((group) => (
					<div key={group.label} className="mb-5">
						<p className="px-2 pb-1.5 text-xs font-medium text-sidebar-foreground/50">{group.label}</p>
						<ul className="flex flex-col gap-0.5">
							{group.items.map((item) => (
								<li key={item.to}>
									<SidebarLink item={item} />
								</li>
							))}
						</ul>
					</div>
				))}
			</nav>

			<div className="border-t border-sidebar-border px-3 py-3">{footer}</div>
		</aside>
	)
}

function SidebarLink({ item }: { item: NavItem }) {
	return (
		<NavLink
			to={item.to}
			end
			className={({ isActive }) =>
				`flex items-center gap-2.5 rounded-lg px-2.5 py-2 text-sm transition-colors ${
					isActive
						? 'bg-sidebar-accent font-medium text-sidebar-accent-foreground'
						: 'text-sidebar-foreground/75 hover:bg-sidebar-accent/50 hover:text-sidebar-foreground'
				}`
			}
		>
			<span className="shrink-0 opacity-80" aria-hidden>
				{item.icon}
			</span>
			<span className="min-w-0 flex-1 truncate">{item.label}</span>
			{item.badge ? <span className="mono-cell shrink-0 text-xs opacity-70">{item.badge}</span> : null}
		</NavLink>
	)
}
