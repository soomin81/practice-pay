import { useState, type ReactNode } from 'react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'

/**
 * 초대 링크를 **1회만** 보여주는 공용 컴포넌트. 가맹점 등록과 내부 직원 발급이 함께 쓴다.
 *
 * **링크를 만드는 함수를 주입받는다** — 이 콘솔이 만드는 초대 링크는 두 종류이고 가리키는
 * 곳이 다르기 때문이다(가맹점 OWNER → merchant 콘솔, 내부 직원 → admin 콘솔 자신).
 * 컴포넌트가 스스로 정하게 두면 호출부에서 어느 쪽인지 보이지 않아 바꿔 쓰기 쉬워진다 —
 * `console/format.ts`의 `merchantInvitationUrlFor`/`internalInvitationUrlFor` 참고.
 *
 * 토큰 원문은 응답에서만 볼 수 있고 서버에는 Hash만 남는다.
 */
export function InvitationReveal({
	title,
	description,
	invitationToken,
	buildUrl,
	onDone,
}: {
	title: string
	description: ReactNode
	invitationToken: string
	buildUrl: (token: string) => string
	onDone: () => void
}) {
	const [copied, setCopied] = useState(false)
	const invitationUrl = buildUrl(invitationToken)

	async function copy() {
		try {
			await navigator.clipboard.writeText(invitationUrl)
			setCopied(true)
		} catch {
			// 클립보드 접근이 막힌 환경 — 사용자가 직접 선택해 복사한다.
			setCopied(false)
		}
	}

	return (
		<Alert variant="destructive" className="flex flex-col gap-3">
			<AlertTitle>{title}</AlertTitle>
			<AlertDescription>
				<p className="mb-2">{description}</p>
				<code className="block w-full break-all rounded-md bg-muted px-2 py-1.5 font-mono text-xs text-foreground">
					{invitationUrl}
				</code>
			</AlertDescription>
			<div className="flex items-center gap-2">
				<Button size="sm" variant="outline" onClick={() => void copy()}>
					{copied ? '복사됨' : '링크 복사'}
				</Button>
				<Button size="sm" onClick={onDone}>
					확인했습니다
				</Button>
			</div>
		</Alert>
	)
}
