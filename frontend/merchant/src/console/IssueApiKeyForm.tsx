import { useState, type FormEvent } from 'react'
import { useIssueApiKey } from '@/console/useApiKeys'
import { ISSUABLE_SCOPES, type ApiKeyScope, type IssueApiKeyResponse } from '@/api/types'
import { MerchantApiError } from '@/api/client'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * API Key 발급 폼. 발급에 성공하면 응답의 `rawApiKey`를 [IssuedKeyReveal]로 크게
 * 보여준다 — 이 값은 **최초 1회만** 노출되고 다시 조회할 수 없다(계약 6.4).
 */
export function IssueApiKeyForm() {
	const [keyName, setKeyName] = useState('')
	const [scopes, setScopes] = useState<ApiKeyScope[]>([...ISSUABLE_SCOPES])
	const issue = useIssueApiKey()

	function toggleScope(scope: ApiKeyScope, checked: boolean) {
		setScopes((current) => (checked ? [...current, scope] : current.filter((value) => value !== scope)))
	}

	function handleSubmit(event: FormEvent) {
		event.preventDefault()
		issue.mutate({ keyName, scopes })
	}

	function reset() {
		issue.reset()
		setKeyName('')
		setScopes([...ISSUABLE_SCOPES])
	}

	// 발급 성공 직후에는 폼 대신 원문 노출 화면을 보여준다.
	if (issue.isSuccess) {
		return <IssuedKeyReveal issued={issue.data} onDone={reset} />
	}

	return (
		<form className="flex flex-col gap-4" onSubmit={handleSubmit}>
			<div className="flex flex-col gap-1.5">
				<Label htmlFor="keyName">Key 이름</Label>
				<Input
					id="keyName"
					value={keyName}
					onChange={(event) => setKeyName(event.target.value)}
					placeholder="예: 운영 서버용"
					required
				/>
			</div>

			<fieldset className="flex flex-col gap-2">
				<legend className="mb-1 text-sm font-medium">Scope</legend>
				{ISSUABLE_SCOPES.map((scope) => (
					<label key={scope} className="flex items-center gap-2 text-sm">
						<input
							type="checkbox"
							checked={scopes.includes(scope)}
							onChange={(event) => toggleScope(scope, event.target.checked)}
						/>
						{scope}
					</label>
				))}
			</fieldset>

			{issue.isError && (
				<p role="alert" className="text-sm text-destructive">
					{issueErrorMessage(issue.error)}
				</p>
			)}

			<Button type="submit" disabled={issue.isPending || scopes.length === 0}>
				{issue.isPending ? '발급 중…' : 'API Key 발급'}
			</Button>
		</form>
	)
}

function IssuedKeyReveal({ issued, onDone }: { issued: IssueApiKeyResponse; onDone: () => void }) {
	const [copied, setCopied] = useState(false)

	async function copy() {
		try {
			await navigator.clipboard.writeText(issued.rawApiKey)
			setCopied(true)
		} catch {
			// 클립보드 접근이 막힌 환경 — 사용자가 직접 선택해 복사한다.
			setCopied(false)
		}
	}

	return (
		<Alert variant="destructive" className="flex flex-col gap-3">
			<AlertTitle>API Key가 발급되었습니다 — 이 값은 다시 볼 수 없습니다</AlertTitle>
			<AlertDescription>
				<p className="mb-2">지금 복사해서 안전한 곳에 보관하세요. 이 화면을 벗어나면 원문을 다시 확인할 수 없습니다.</p>
				<code className="block w-full break-all rounded-md bg-muted px-2 py-1.5 font-mono text-xs text-foreground">
					{issued.rawApiKey}
				</code>
			</AlertDescription>
			<div className="flex items-center gap-2">
				<Button size="sm" variant="outline" onClick={() => void copy()}>
					{copied ? '복사됨' : '복사'}
				</Button>
				<Button size="sm" onClick={onDone}>
					확인했습니다
				</Button>
			</div>
		</Alert>
	)
}

function issueErrorMessage(error: unknown): string {
	if (error instanceof MerchantApiError) {
		if (error.isForbidden) return 'API Key를 발급할 권한이 없습니다(OWNER/ADMIN만 가능).'
		return error.message
	}
	return 'API Key 발급에 실패했습니다.'
}
