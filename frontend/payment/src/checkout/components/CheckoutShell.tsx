import type { ReactNode } from 'react'

/**
 * 체크아웃 화면의 바깥 틀.
 *
 * 모든 상태 화면이 이 안에 들어간다 — 결제 화면과 완료/실패 화면의 폭과 여백이
 * 달라지면 상태가 바뀔 때 카드가 튀어 보이기 때문이다.
 *
 * 고객이 가맹점 사이트에서 넘어와 이 페이지만 보게 되므로(Hosted Checkout) 좁은
 * 단일 컬럼으로 두고, 모바일을 기준으로 잡은 뒤 데스크톱에서 가운데 정렬한다.
 */
export function CheckoutShell({ children }: { children: ReactNode }) {
	return (
		<div className="flex min-h-dvh flex-col items-center bg-muted/40 px-4 py-6 sm:py-12">
			<div className="w-full max-w-md space-y-4">
				<header className="text-center">
					<h1 className="text-sm font-medium tracking-widest text-muted-foreground uppercase">
						Checkout
					</h1>
				</header>
				{children}
			</div>
		</div>
	)
}
