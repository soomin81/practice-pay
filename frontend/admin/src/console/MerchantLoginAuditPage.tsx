import { useMerchantLoginAudit } from '@/console/useMerchantLoginAudit'
import { MerchantLoginAuditTable } from '@/console/MerchantLoginAuditTable'
import { AdminApiError } from '@/api/client'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

/**
 * 가맹점 로그인 감사 로그 페이지. **SUPER_ADMIN/OPERATOR 전용**이다 — 라우트·내비가 이미
 * 막지만(`App.tsx`/`ConsoleShell`), 서버도 `/admin/merchant-login-audit`를 403으로 막는다.
 * 전 가맹점의 로그인 시도를 본다(기록은 가맹점 콘솔이 아니라 api-merchant의 로그인이 남긴다).
 */
export function MerchantLoginAuditPage() {
	const audit = useMerchantLoginAudit()

	return (
		<div className="flex flex-col gap-6">
			<Card>
				<CardHeader>
					<CardTitle>가맹점 로그인 감사 로그</CardTitle>
					<CardDescription>
						전 가맹점의 관리자 로그인 시도(성공·실패·잠김)를 최신순으로 보여줍니다. 없는 가맹점 코드를 노린 시도도 함께 남습니다.
					</CardDescription>
				</CardHeader>
				<CardContent>
					{audit.isPending && <p className="text-sm text-muted-foreground">불러오는 중…</p>}
					{audit.isError && <p className="text-sm text-destructive">{listErrorMessage(audit.error)}</p>}
					{audit.isSuccess && <MerchantLoginAuditTable entries={audit.data.entries} />}
				</CardContent>
			</Card>
		</div>
	)
}

function listErrorMessage(error: unknown): string {
	if (error instanceof AdminApiError) {
		if (error.isForbidden) return '가맹점 로그인 감사 로그를 볼 권한이 없습니다(SUPER_ADMIN/OPERATOR만 가능).'
		return error.message
	}
	return '가맹점 로그인 감사 로그를 불러오지 못했습니다.'
}
