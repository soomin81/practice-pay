import { useEffect, useState } from 'react'
import { MerchantApiError } from '@/api/client'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { useRotateWebhookSecret, useUpdateWebhookUrl, useWebhookSettings } from '@/console/useWebhookSettings'
import { PageHeader } from '@/components/console/PageHeader'
import { Panel } from '@/components/console/Panel'

/**
 * Webhook 설정 화면 — 수신 URL과 **서명 비밀**을 다룬다.
 *
 * 서명 비밀은 가맹점이 받은 요청이 PG에서 온 것인지 확인하는 유일한 수단이다.
 * 이 값이 없으면 수신 URL만 아는 누구나 `payment.succeeded`를 위조해 보낼 수 있다.
 *
 * 비밀은 **기본적으로 가려 둔다** — 이 경로는 OWNER/ADMIN만 열 수 있지만, 화면
 * 공유나 어깨너머로 새는 것까지 막지는 못한다.
 */
export function WebhookPage() {
	const settings = useWebhookSettings()
	const updateUrl = useUpdateWebhookUrl()
	const rotate = useRotateWebhookSecret()

	const [url, setUrl] = useState('')
	const [secretShown, setSecretShown] = useState(false)
	const [confirmingRotate, setConfirmingRotate] = useState(false)
	const [copied, setCopied] = useState(false)

	// 서버 값이 도착하면 입력란의 초기값으로 삼는다.
	useEffect(() => {
		if (settings.data) setUrl(settings.data.webhookUrl ?? '')
	}, [settings.data])

	if (settings.isPending) return <p className="text-sm text-muted-foreground">불러오는 중…</p>
	if (settings.error) return <p className="text-sm text-destructive">{messageOf(settings.error)}</p>
	if (!settings.data) return null

	const { signingSecret, secretVersion, webhookUrl } = settings.data

	async function copySecret() {
		try {
			await navigator.clipboard.writeText(signingSecret)
			setCopied(true)
		} catch {
			// 클립보드 접근이 막힌 환경 — 사용자가 직접 선택해 복사한다.
			setCopied(false)
		}
	}

	return (
		<>
			<PageHeader
				title="Webhook"
				description="결제 상태가 바뀔 때 알림을 받을 주소와, 그 요청의 진위를 확인할 서명 비밀입니다."
			/>
		<div className="flex flex-col gap-6">
			<Panel
				title="수신 URL"
				meta="결제 상태가 바뀔 때 이 주소로 POST를 보냅니다. 비워 두면 전송하지 않습니다."
				bodyClassName="flex flex-col gap-3 px-5 pb-5"
			>
				<form
					className="flex flex-wrap items-center gap-2"
					onSubmit={(event) => {
						event.preventDefault()
						updateUrl.mutate(url.trim() === '' ? null : url.trim())
					}}
				>
					<Input
						aria-label="Webhook 수신 URL"
						placeholder="https://example.com/webhooks/practice-pay"
						value={url}
						onChange={(event) => setUrl(event.target.value)}
						className="max-w-xl"
					/>
					<Button type="submit" disabled={updateUrl.isPending}>
						{updateUrl.isPending ? '저장 중…' : '저장'}
					</Button>
				</form>
				{updateUrl.error ? <p className="text-sm text-destructive">{messageOf(updateUrl.error)}</p> : null}
				{updateUrl.isSuccess && !updateUrl.error ? (
					<p className="text-sm text-muted-foreground">
						{webhookUrl ? '저장했습니다.' : '수신 URL을 해제했습니다 — 더 이상 전송하지 않습니다.'}
					</p>
				) : null}
			</Panel>

			<Panel
				title="서명 비밀"
				meta={
					<>
						받은 요청의 <code className="mono-cell">X-PracticePay-Signature</code> 헤더를 이 값으로 검증하세요.{' '}
						<strong>검증하지 않으면 누구나 결제 성공 알림을 위조할 수 있습니다.</strong>
					</>
				}
				bodyClassName="flex flex-col gap-3 px-5 pb-5"
			>
				<div className="flex flex-wrap items-center gap-2">
					<code
						data-testid="signing-secret"
						className="min-w-0 flex-1 break-all rounded-md bg-muted px-2 py-1.5 font-mono text-xs text-foreground"
					>
						{secretShown ? signingSecret : '•'.repeat(24)}
					</code>
					<Button size="sm" variant="outline" onClick={() => setSecretShown(!secretShown)}>
						{secretShown ? '가리기' : '보기'}
					</Button>
					<Button size="sm" variant="outline" onClick={() => void copySecret()}>
						{copied ? '복사됨' : '복사'}
					</Button>
				</div>
				<p className="text-sm text-muted-foreground">현재 세대: {secretVersion}</p>

				{confirmingRotate ? (
					<Alert variant="destructive" className="flex flex-col gap-3">
						<AlertTitle>비밀을 교체하면 되돌릴 수 없습니다</AlertTitle>
						<AlertDescription>
							새 비밀을 서버에 반영하기 전까지, 그 사이에 발생한 Webhook은 서명이 맞지 않아 거부됩니다. 겹쳐 쓸 수 있는
							기간은 없습니다.
						</AlertDescription>
						<div className="flex items-center gap-2">
							<Button
								size="sm"
								variant="destructive"
								disabled={rotate.isPending}
								onClick={() => {
									rotate.mutate(undefined, {
										onSuccess: () => {
											setConfirmingRotate(false)
											setSecretShown(true)
											setCopied(false)
										},
									})
								}}
							>
								{rotate.isPending ? '교체 중…' : '교체합니다'}
							</Button>
							<Button size="sm" variant="outline" onClick={() => setConfirmingRotate(false)}>
								취소
							</Button>
						</div>
					</Alert>
				) : (
					<div>
						<Button size="sm" variant="outline" onClick={() => setConfirmingRotate(true)}>
							비밀 교체
						</Button>
					</div>
				)}
				{rotate.error ? <p className="text-sm text-destructive">{messageOf(rotate.error)}</p> : null}
			</Panel>
			</div>
		</>
	)
}

function messageOf(error: unknown): string {
	return error instanceof MerchantApiError ? error.message : '요청을 처리하지 못했습니다.'
}
