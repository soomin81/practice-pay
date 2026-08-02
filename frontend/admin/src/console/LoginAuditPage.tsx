import { useLoginAudit } from '@/console/useLoginAudit'
import { LoginAuditTable } from '@/console/LoginAuditTable'
import { AdminApiError } from '@/api/client'
import { PageHeader } from '@/components/console/PageHeader'
import { Panel } from '@/components/console/Panel'

/**
 * 로그인 감사 로그 페이지. **이 페이지 전체가 SUPER_ADMIN 전용이다** — 라우트와 내비가
 * 이미 막지만(`App.tsx`/`ConsoleShell`), 서버도 `/admin/login-audit`를 403으로 막는다.
 */
export function LoginAuditPage() {
	const audit = useLoginAudit()

	return (
		<>
			<PageHeader title="로그인 감사" description="내부 운영자 로그인 시도를 최신순으로 조회합니다." />
		<div className="flex flex-col gap-6">
			<Panel title={<>로그인 감사 로그</>} meta={<>내부 운영자 로그인 시도(성공·실패·잠김)를 최신순으로 보여줍니다. 없는 계정을 노린 시도도 함께 남습니다.</>}>
				<div>
					{audit.isPending && <p className="text-sm text-muted-foreground">불러오는 중…</p>}
					{audit.isError && <p className="text-sm text-destructive">{listErrorMessage(audit.error)}</p>}
					{audit.isSuccess && <LoginAuditTable entries={audit.data.entries} />}
				</div>
			</Panel>
		</div>
		</>
	)
}


function listErrorMessage(error: unknown): string {
	if (error instanceof AdminApiError) {
		if (error.isForbidden) return '로그인 감사 로그를 볼 권한이 없습니다(SUPER_ADMIN만 가능).'
		return error.message
	}
	return '로그인 감사 로그를 불러오지 못했습니다.'
}
