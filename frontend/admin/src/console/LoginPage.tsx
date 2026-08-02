import { useState, type FormEvent } from 'react'
import { useLogin } from '@/auth/useAuth'
import { AdminApiError } from '@/api/client'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * 내부 운영자 로그인 화면. **가맹점 콘솔과 달리 `merchantCode`가 없다** — 내부 운영자는
 * 특정 가맹점에 속하지 않고 `login_id`가 전 시스템에서 유일하다.
 */
export function LoginPage() {
	const [loginId, setLoginId] = useState('')
	const [password, setPassword] = useState('')
	const login = useLogin()

	function handleSubmit(event: FormEvent) {
		event.preventDefault()
		login.mutate({ loginId, password })
	}

	return (
		<div className="flex min-h-dvh flex-col items-center justify-center gap-5 p-6">
			{/* 사이드바의 브랜드 블록과 같은 표식 — 로그인 전에도 어느 서비스인지 보이게 한다. */}
			<div className="flex items-center gap-2.5">
				<span className="flex size-8 items-center justify-center rounded-lg bg-sidebar text-sm font-semibold text-sidebar-foreground">
					PP
				</span>
				<span className="font-heading text-base font-semibold">PracticePay</span>
			</div>
			<Card className="w-full max-w-sm">
				<CardHeader>
					<CardTitle>내부 운영자 콘솔</CardTitle>
					<CardDescription>PG 내부 운영자 계정으로 로그인합니다.</CardDescription>
				</CardHeader>
				<CardContent>
					<form className="flex flex-col gap-4" onSubmit={handleSubmit}>
						<div className="flex flex-col gap-1.5">
							<Label htmlFor="loginId">로그인 아이디</Label>
							<Input
								id="loginId"
								value={loginId}
								onChange={(event) => setLoginId(event.target.value)}
								autoComplete="username"
								required
							/>
						</div>
						<div className="flex flex-col gap-1.5">
							<Label htmlFor="password">비밀번호</Label>
							<Input
								id="password"
								type="password"
								value={password}
								onChange={(event) => setPassword(event.target.value)}
								autoComplete="current-password"
								required
							/>
						</div>

						{login.isError && (
							<p role="alert" className="text-sm text-destructive">
								{loginErrorMessage(login.error)}
							</p>
						)}

						<Button type="submit" disabled={login.isPending}>
							{login.isPending ? '로그인 중…' : '로그인'}
						</Button>
					</form>
				</CardContent>
			</Card>
		</div>
	)
}

function loginErrorMessage(error: unknown): string {
	if (error instanceof AdminApiError) {
		// 401은 아이디/비밀번호 불일치 또는 계정 잠금 — 원인을 세분해 알려주지 않는다.
		if (error.isUnauthorized) return '로그인 정보가 올바르지 않거나 계정을 사용할 수 없습니다.'
		return error.message
	}
	return '로그인에 실패했습니다.'
}
