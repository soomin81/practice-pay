import { useInternalUsers } from '@/console/useInternalUsers'
import { InternalUserTable } from '@/console/InternalUserTable'
import { IssueInternalUserForm } from '@/console/IssueInternalUserForm'
import { AdminApiError } from '@/api/client'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

/**
 * 내부 직원 명부·발급 페이지. **이 페이지 전체가 SUPER_ADMIN 전용이다** — 라우트와
 * 내비게이션이 이미 막지만(`App.tsx`/`ConsoleShell`), 서버도 `GET`/`POST` 둘 다 403으로
 * 막는다(`/admin/internal-users` 규칙이 메서드로 좁혀져 있지 않다).
 */
export function InternalUsersPage() {
	const users = useInternalUsers()

	return (
		<div className="flex flex-col gap-6">
			<Card>
				<CardHeader>
					<CardTitle>내부 직원 초대</CardTitle>
					<CardDescription>
						OPERATOR 또는 VIEWER 계정을 초대합니다. 발급된 링크로 본인이 비밀번호를 설정하면 활성화됩니다.
					</CardDescription>
				</CardHeader>
				<CardContent>
					<IssueInternalUserForm />
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>내부 직원</CardTitle>
					<CardDescription>INVITED는 아직 초대 링크로 비밀번호를 설정하지 않은 계정입니다.</CardDescription>
				</CardHeader>
				<CardContent>
					{users.isPending && <p className="text-sm text-muted-foreground">불러오는 중…</p>}
					{users.isError && <p className="text-sm text-destructive">{listErrorMessage(users.error)}</p>}
					{users.isSuccess && <InternalUserTable internalUsers={users.data.internalUsers} />}
				</CardContent>
			</Card>
		</div>
	)
}

function listErrorMessage(error: unknown): string {
	if (error instanceof AdminApiError) {
		if (error.isForbidden) return '내부 직원 목록을 볼 권한이 없습니다(SUPER_ADMIN만 가능).'
		return error.message
	}
	return '내부 직원 목록을 불러오지 못했습니다.'
}
