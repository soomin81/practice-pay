import { useState, type FormEvent } from 'react'
import { useInviteSubAccount } from '@/console/useMerchantUsers'
import { INVITABLE_ROLES, type InviteSubAccountResponse, type MerchantUserRole } from '@/api/types'
import { MerchantApiError } from '@/api/client'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * 하위 계정 발급(초대) 폼. 발급에 성공하면 [InvitationReveal]이 **초대 링크**를 1회만
 * 보여준다 — `invitationToken`은 이 응답에서만 원문으로 보이고 다시 조회할 수 없다
 * (API Key의 `rawApiKey`와 같은 규칙).
 */
export function InviteSubAccountForm() {
	const [loginId, setLoginId] = useState('')
	const [email, setEmail] = useState('')
	const [userName, setUserName] = useState('')
	const [role, setRole] = useState<MerchantUserRole>('ADMIN')
	const invite = useInviteSubAccount()

	function handleSubmit(event: FormEvent) {
		event.preventDefault()
		invite.mutate({ loginId, email, userName, role })
	}

	function reset() {
		invite.reset()
		setLoginId('')
		setEmail('')
		setUserName('')
		setRole('ADMIN')
	}

	if (invite.isSuccess) {
		return <InvitationReveal invited={invite.data} onDone={reset} />
	}

	return (
		<form className="flex flex-col gap-4" onSubmit={handleSubmit}>
			<div className="flex flex-col gap-1.5">
				<Label htmlFor="invite-loginId">로그인 아이디</Label>
				<Input id="invite-loginId" value={loginId} onChange={(event) => setLoginId(event.target.value)} required />
			</div>
			<div className="flex flex-col gap-1.5">
				<Label htmlFor="invite-email">이메일</Label>
				<Input
					id="invite-email"
					type="email"
					value={email}
					onChange={(event) => setEmail(event.target.value)}
					required
				/>
			</div>
			<div className="flex flex-col gap-1.5">
				<Label htmlFor="invite-userName">이름</Label>
				<Input id="invite-userName" value={userName} onChange={(event) => setUserName(event.target.value)} required />
			</div>

			<fieldset className="flex flex-col gap-2">
				{/* OWNER는 선택지에 없다 — 하위 계정 발급으로는 만들 수 없다(INVITABLE_ROLES). */}
				<legend className="mb-1 text-sm font-medium">역할</legend>
				{INVITABLE_ROLES.map((invitableRole) => (
					<label key={invitableRole} className="flex items-center gap-2 text-sm">
						<input
							type="radio"
							name="role"
							value={invitableRole}
							checked={role === invitableRole}
							onChange={() => setRole(invitableRole)}
						/>
						{invitableRole}
					</label>
				))}
			</fieldset>

			{invite.isError && (
				<p role="alert" className="text-sm text-destructive">
					{inviteErrorMessage(invite.error)}
				</p>
			)}

			<Button type="submit" disabled={invite.isPending}>
				{invite.isPending ? '초대 중…' : '하위 계정 초대'}
			</Button>
		</form>
	)
}

/**
 * 발급 결과. **토큰 문자열이 아니라 바로 쓸 수 있는 초대 링크를 보여준다** — MVP에는
 * 초대 메일 발송이 없어서 발급한 사람이 이 링크를 직접 전달해야 하기 때문이다.
 */
function InvitationReveal({ invited, onDone }: { invited: InviteSubAccountResponse; onDone: () => void }) {
	const [copied, setCopied] = useState(false)
	const invitationUrl = `${window.location.origin}/accept-invitation?token=${encodeURIComponent(invited.invitationToken)}`

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
			<AlertTitle>초대 링크가 발급되었습니다 — 이 링크는 다시 볼 수 없습니다</AlertTitle>
			<AlertDescription>
				<p className="mb-2">
					<strong>{invited.loginId}</strong>({String(invited.role)}) 계정이 초대되었습니다. 아래 링크를 본인에게
					전달하세요. 이 화면을 벗어나면 다시 확인할 수 없습니다.
				</p>
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

function inviteErrorMessage(error: unknown): string {
	if (error instanceof MerchantApiError) {
		if (error.isConflict) return '이미 사용 중인 로그인 아이디 또는 이메일입니다.'
		if (error.isForbidden) return '하위 계정을 초대할 권한이 없습니다(OWNER/ADMIN만 가능).'
		return error.message
	}
	return '하위 계정 초대에 실패했습니다.'
}
