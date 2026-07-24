import { useApiKeys } from '@/console/useApiKeys'
import { IssueApiKeyForm } from '@/console/IssueApiKeyForm'
import { ApiKeyTable } from '@/console/ApiKeyTable'
import { MerchantApiError } from '@/api/client'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'

/**
 * API Key 관리 페이지 — 발급 폼과 목록을 조립한다. 상태 분기(로딩/오류/목록)는 여기서
 * 정하고, 모양은 하위 컴포넌트가 갖는다(payment의 "화면 컴포넌트는 상태 분기와 분리"와 같은 결).
 */
export function ApiKeysPage() {
	const keys = useApiKeys()

	return (
		<div className="flex flex-col gap-6">
			<Card>
				<CardHeader>
					<CardTitle>API Key 발급</CardTitle>
					<CardDescription>결제 API 연동에 쓸 서버 간 자격증명을 발급합니다(환경은 항상 TEST).</CardDescription>
				</CardHeader>
				<CardContent>
					<IssueApiKeyForm />
				</CardContent>
			</Card>

			<Card>
				<CardHeader>
					<CardTitle>발급된 API Key</CardTitle>
					<CardDescription>Secret 원문은 발급 시점에만 보이고 여기에는 표시되지 않습니다.</CardDescription>
				</CardHeader>
				<CardContent>
					{keys.isPending && <p className="text-sm text-muted-foreground">불러오는 중…</p>}
					{keys.isError && (
						<p className="text-sm text-destructive">{listErrorMessage(keys.error)}</p>
					)}
					{keys.isSuccess && <ApiKeyTable apiKeys={keys.data.apiKeys} />}
				</CardContent>
			</Card>
		</div>
	)
}

function listErrorMessage(error: unknown): string {
	if (error instanceof MerchantApiError) {
		if (error.isForbidden) return 'API Key 목록을 볼 권한이 없습니다(OWNER/ADMIN만 가능).'
		return error.message
	}
	return 'API Key 목록을 불러오지 못했습니다.'
}
