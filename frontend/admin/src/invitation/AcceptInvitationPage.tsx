import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAcceptInvitation } from '@/invitation/useAcceptInvitation'
import { AdminApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * 내부 운영자 초대 수락(계정 활성화) 화면. **인증 게이트 밖에 있다** — 초대받은 직원은
 * 아직 계정이 활성화되지 않아 로그인할 수 없다(`App.tsx`의 라우트 주석 참고).
 *
 * Token은 쿼리스트링으로 받는다(`/accept-invitation?token=…`) — 내부 직원 발급 화면이
 * 만들어 주는 링크 형식이다. 성공해도 로그인 상태가 되지는 않으므로 로그인으로 안내한다.
 */
export function AcceptInvitationPage() {
	const [searchParams] = useSearchParams()
	const token = searchParams.get('token') ?? ''
	const [password, setPassword] = useState('')
	const [confirmation, setConfirmation] = useState('')
	const accept = useAcceptInvitation()

	const passwordsMatch = password === confirmation

	function handleSubmit(event: FormEvent) {
		event.preventDefault()
		if (!passwordsMatch) return
		accept.mutate({ invitationToken: token, newPassword: password })
	}

	return (
		<div className="flex min-h-dvh items-center justify-center p-6">
			<Card className="w-full max-w-sm">
				<CardHeader>
					<CardTitle>계정 활성화</CardTitle>
					<CardDescription>초대받은 내부 운영자 계정의 비밀번호를 설정합니다.</CardDescription>
				</CardHeader>
				<CardContent>
					{!token ? (
						<p className="text-sm text-muted-foreground">
							초대 토큰이 없습니다. 전달받은 초대 링크로 다시 접속해 주세요.
						</p>
					) : accept.isSuccess ? (
						<div className="flex flex-col gap-3 text-sm">
							<p>
								<strong>{accept.data.loginId}</strong> 계정이 활성화되었습니다. 이제 로그인할 수 있습니다.
							</p>
							<Button asChild size="sm">
								<Link to="/">로그인하러 가기</Link>
							</Button>
						</div>
					) : (
						<form className="flex flex-col gap-4" onSubmit={handleSubmit}>
							<div className="flex flex-col gap-1.5">
								<Label htmlFor="new-password">새 비밀번호</Label>
								<Input
									id="new-password"
									type="password"
									value={password}
									onChange={(event) => setPassword(event.target.value)}
									autoComplete="new-password"
									required
								/>
							</div>
							<div className="flex flex-col gap-1.5">
								<Label htmlFor="confirm-password">비밀번호 확인</Label>
								<Input
									id="confirm-password"
									type="password"
									value={confirmation}
									onChange={(event) => setConfirmation(event.target.value)}
									autoComplete="new-password"
									required
								/>
							</div>

							{confirmation.length > 0 && !passwordsMatch && (
								<p role="alert" className="text-sm text-destructive">
									비밀번호가 일치하지 않습니다.
								</p>
							)}
							{accept.isError && (
								<p role="alert" className="text-sm text-destructive">
									{acceptErrorMessage(accept.error)}
								</p>
							)}

							<Button type="submit" disabled={accept.isPending || !passwordsMatch}>
								{accept.isPending ? '설정 중…' : '비밀번호 설정하고 활성화'}
							</Button>
						</form>
					)}
				</CardContent>
			</Card>
		</div>
	)
}

function acceptErrorMessage(error: unknown): string {
	if (error instanceof AdminApiError) {
		// 400은 유효하지 않거나 만료·이미 사용된 초대다 — 서버가 원인을 구분해 주지 않는다.
		if (error.status === 400) return '유효하지 않거나 만료된 초대입니다. 관리자에게 새 초대를 요청하세요.'
		return error.message
	}
	return '계정 활성화에 실패했습니다.'
}
