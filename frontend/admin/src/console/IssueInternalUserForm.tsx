import { useState, type FormEvent } from 'react'
import { useIssueInternalUser } from '@/console/useInternalUsers'
import { internalInvitationUrlFor } from '@/console/format'
import { InvitationReveal } from '@/console/InvitationReveal'
import { AdminApiError } from '@/api/client'
import { ISSUABLE_INTERNAL_ROLES, type InternalUserRole, type IssueInternalUserResponse } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * 내부 운영자 계정 발급 폼(SUPER_ADMIN 전용 화면 안에 있다).
 *
 * 발급에 성공하면 초대 링크를 1회만 보여준다 — **이 링크는 admin 콘솔 자신을 가리킨다**
 * (가맹점 등록이 만드는 링크가 가맹점 콘솔을 가리키는 것과 대비된다,
 * `internalInvitationUrlFor` 참고).
 */
export function IssueInternalUserForm() {
	const [loginId, setLoginId] = useState('')
	const [email, setEmail] = useState('')
	const [userName, setUserName] = useState('')
	const [role, setRole] = useState<InternalUserRole>('OPERATOR')
	const issue = useIssueInternalUser()

	function handleSubmit(event: FormEvent) {
		event.preventDefault()
		issue.mutate({ loginId, email, userName, role })
	}

	function reset() {
		issue.reset()
		setLoginId('')
		setEmail('')
		setUserName('')
		setRole('OPERATOR')
	}

	if (issue.isSuccess) {
		return <IssuedInternalUser issued={issue.data} onDone={reset} />
	}

	return (
		<form className="flex flex-col gap-4" onSubmit={handleSubmit}>
			<div className="grid gap-4 sm:grid-cols-3">
				<div className="flex flex-col gap-1.5">
					<Label htmlFor="internal-loginId">로그인 아이디</Label>
					<Input
						id="internal-loginId"
						value={loginId}
						onChange={(event) => setLoginId(event.target.value)}
						required
					/>
				</div>
				<div className="flex flex-col gap-1.5">
					<Label htmlFor="internal-email">이메일</Label>
					<Input
						id="internal-email"
						type="email"
						value={email}
						onChange={(event) => setEmail(event.target.value)}
						required
					/>
				</div>
				<div className="flex flex-col gap-1.5">
					<Label htmlFor="internal-userName">이름</Label>
					<Input
						id="internal-userName"
						value={userName}
						onChange={(event) => setUserName(event.target.value)}
						required
					/>
				</div>
			</div>

			<fieldset className="flex flex-col gap-2">
				{/* SUPER_ADMIN은 선택지에 없다 — Bootstrap으로만 생성한다(ISSUABLE_INTERNAL_ROLES). */}
				<legend className="mb-1 text-sm font-medium">역할</legend>
				{ISSUABLE_INTERNAL_ROLES.map((issuableRole) => (
					<label key={issuableRole} className="flex items-center gap-2 text-sm">
						<input
							type="radio"
							name="internal-role"
							value={issuableRole}
							checked={role === issuableRole}
							onChange={() => setRole(issuableRole)}
						/>
						{issuableRole}
					</label>
				))}
			</fieldset>

			{issue.isError && (
				<p role="alert" className="text-sm text-destructive">
					{issueErrorMessage(issue.error)}
				</p>
			)}

			<Button type="submit" disabled={issue.isPending}>
				{issue.isPending ? '발급 중…' : '내부 직원 초대'}
			</Button>
		</form>
	)
}

function IssuedInternalUser({ issued, onDone }: { issued: IssueInternalUserResponse; onDone: () => void }) {
	return (
		<InvitationReveal
			title="내부 직원이 초대되었습니다 — 이 링크는 다시 볼 수 없습니다"
			description={
				<>
					<strong>{issued.loginId}</strong>({String(issued.role)}) 계정이 초대되었습니다. 아래{' '}
					<strong>이 콘솔</strong> 링크를 본인에게 전달하세요 — 이 화면을 벗어나면 확인할 수 없습니다.
				</>
			}
			invitationToken={issued.invitationToken}
			buildUrl={internalInvitationUrlFor}
			onDone={onDone}
		/>
	)
}

function issueErrorMessage(error: unknown): string {
	if (error instanceof AdminApiError) {
		if (error.isConflict) return '이미 사용 중인 로그인 아이디 또는 이메일입니다.'
		if (error.isForbidden) return '내부 직원을 초대할 권한이 없습니다(SUPER_ADMIN만 가능).'
		return error.message
	}
	return '내부 직원 초대에 실패했습니다.'
}
