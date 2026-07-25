import { useState } from 'react'
import { invitationUrlFor } from '@/console/format'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'

/**
 * 초대 링크를 **1회만** 보여주는 공용 컴포넌트. 최초 발급(`InviteSubAccountForm`)과
 * 재발송(`MerchantUserActions`)이 함께 쓴다 — 링크 형식 자체는 `format.ts`의
 * `invitationUrlFor`가 유일한 출처다.
 *
 * 토큰 원문은 응답에서만 볼 수 있고 서버에는 Hash만 남는다(계약 6.4와 같은 규칙).
 */
export function InvitationReveal({
	title,
	description,
	invitationToken,
	onDone,
}: {
	title: string
	description: React.ReactNode
	invitationToken: string
	onDone: () => void
}) {
	const [copied, setCopied] = useState(false)
	const invitationUrl = invitationUrlFor(invitationToken)

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
