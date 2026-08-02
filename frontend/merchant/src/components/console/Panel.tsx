import type { ReactNode } from 'react'

/**
 * 밝은 바탕 위에 떠 있는 흰 카드. 참고 디자인의 기본 담는 그릇이다.
 *
 * shadcn의 `Card`를 쓰지 않은 이유는 이 화면들이 필요로 하는 조합이
 * **제목 + 메타 한 줄 + 우측 액션**으로 고정돼 있어서다 — `CardHeader`/`CardTitle`/
 * `CardAction`을 페이지마다 다시 조립하는 것보다 이 형태를 한 번 정하는 쪽이
 * 화면끼리 어긋날 여지가 적다.
 */
export function Panel({
	title,
	meta,
	action,
	children,
	bodyClassName = 'px-5 pb-5',
}: {
	title?: ReactNode
	meta?: ReactNode
	action?: ReactNode
	children: ReactNode
	bodyClassName?: string
}) {
	return (
		<section className="rounded-xl border bg-card shadow-xs">
			{title || action ? (
				<header className="flex flex-wrap items-start justify-between gap-3 px-5 pt-5 pb-4">
					<div className="min-w-0">
						<h2 className="font-heading text-base font-semibold">{title}</h2>
						{meta ? <p className="mt-1 text-sm text-muted-foreground">{meta}</p> : null}
					</div>
					{action ? <div className="flex shrink-0 items-center gap-2">{action}</div> : null}
				</header>
			) : null}
			<div className={bodyClassName}>{children}</div>
		</section>
	)
}
