import { useState, type FormEvent } from 'react'
import { useRegisterMerchant } from '@/console/useMerchants'
import { merchantInvitationUrlFor } from '@/console/format'
import { InvitationReveal } from '@/console/InvitationReveal'
import { AdminApiError } from '@/api/client'
import type { RegisterMerchantResponse } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * 가맹점 등록 폼. 가맹점과 최초 `OWNER` 계정이 한 트랜잭션에서 함께 생긴다 —
 * 가맹점은 스스로 가입할 수 없다(`docs/architecture/identity-access-api-key.md`의 "4.3").
 *
 * 등록에 성공하면 OWNER 초대 링크를 **1회만** 보여준다. 그 링크는 이 콘솔이 아니라
 * **가맹점 콘솔**을 가리킨다([merchantInvitationUrlFor]).
 */
export function RegisterMerchantForm() {
	const [merchantCode, setMerchantCode] = useState('')
	const [merchantName, setMerchantName] = useState('')
	const [webhookUrl, setWebhookUrl] = useState('')
	const [ownerLoginId, setOwnerLoginId] = useState('')
	const [ownerEmail, setOwnerEmail] = useState('')
	const [ownerUserName, setOwnerUserName] = useState('')
	const register = useRegisterMerchant()

	function handleSubmit(event: FormEvent) {
		event.preventDefault()
		register.mutate({
			merchantCode,
			merchantName,
			// 선택값이라 비어 있으면 아예 보내지 않는다(빈 문자열은 URL 검증에 걸린다).
			webhookUrl: webhookUrl.trim() === '' ? null : webhookUrl,
			ownerLoginId,
			ownerEmail,
			ownerUserName,
		})
	}

	function reset() {
		register.reset()
		setMerchantCode('')
		setMerchantName('')
		setWebhookUrl('')
		setOwnerLoginId('')
		setOwnerEmail('')
		setOwnerUserName('')
	}

	if (register.isSuccess) {
		return <RegisteredMerchant registered={register.data} onDone={reset} />
	}

	return (
		<form className="flex flex-col gap-4" onSubmit={handleSubmit}>
			<div className="grid gap-4 sm:grid-cols-2">
				<Field id="merchantCode" label="가맹점 코드" value={merchantCode} onChange={setMerchantCode} required />
				<Field id="merchantName" label="가맹점 이름" value={merchantName} onChange={setMerchantName} required />
			</div>
			<Field
				id="webhookUrl"
				label="Webhook URL (선택)"
				value={webhookUrl}
				onChange={setWebhookUrl}
				placeholder="https://merchant.example.com/webhooks/payments"
			/>

			<fieldset className="grid gap-4 rounded-lg border p-3 sm:grid-cols-3">
				<legend className="px-1 text-sm font-medium">최초 OWNER 계정</legend>
				<Field id="ownerLoginId" label="로그인 아이디" value={ownerLoginId} onChange={setOwnerLoginId} required />
				<Field id="ownerEmail" label="이메일" type="email" value={ownerEmail} onChange={setOwnerEmail} required />
				<Field id="ownerUserName" label="이름" value={ownerUserName} onChange={setOwnerUserName} required />
			</fieldset>

			{register.isError && (
				<p role="alert" className="text-sm text-destructive">
					{registerErrorMessage(register.error)}
				</p>
			)}

			<Button type="submit" disabled={register.isPending}>
				{register.isPending ? '등록 중…' : '가맹점 등록'}
			</Button>
		</form>
	)
}

function Field({
	id,
	label,
	value,
	onChange,
	type = 'text',
	required = false,
	placeholder,
}: {
	id: string
	label: string
	value: string
	onChange: (value: string) => void
	type?: string
	required?: boolean
	placeholder?: string
}) {
	return (
		<div className="flex flex-col gap-1.5">
			<Label htmlFor={id}>{label}</Label>
			<Input
				id={id}
				type={type}
				value={value}
				placeholder={placeholder}
				onChange={(event) => onChange(event.target.value)}
				required={required}
			/>
		</div>
	)
}

/**
 * 등록 결과. **초대 링크가 가맹점 콘솔을 가리킨다는 점을 문구로도 알린다** — 운영자가
 * 이 링크를 OWNER에게 직접 전달해야 하는데(초대 메일 발송이 없다), 자기 콘솔 주소로
 * 착각하면 상대가 열 수 없다.
 */
function RegisteredMerchant({ registered, onDone }: { registered: RegisterMerchantResponse; onDone: () => void }) {
	return (
		<InvitationReveal
			title="가맹점이 등록되었습니다 — 이 초대 링크는 다시 볼 수 없습니다"
			description={
				<>
					<strong>{registered.merchantName}</strong>({registered.merchantCode})가 등록되고 OWNER 계정{' '}
					<strong>{registered.ownerLoginId}</strong>이(가) 초대되었습니다. 아래 <strong>가맹점 콘솔</strong> 링크를
					OWNER에게 전달하세요 — 이 화면을 벗어나면 확인할 수 없습니다.
				</>
			}
			invitationToken={registered.invitationToken}
			buildUrl={merchantInvitationUrlFor}
			onDone={onDone}
		/>
	)
}

function registerErrorMessage(error: unknown): string {
	if (error instanceof AdminApiError) {
		if (error.isConflict) return '이미 사용 중인 가맹점 코드 또는 OWNER 계정 정보입니다.'
		if (error.isForbidden) return '가맹점을 등록할 권한이 없습니다(SUPER_ADMIN/OPERATOR만 가능).'
		return error.message
	}
	return '가맹점 등록에 실패했습니다.'
}
