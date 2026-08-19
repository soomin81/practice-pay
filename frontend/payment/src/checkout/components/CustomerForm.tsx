import { useState } from 'react'
import { checkoutApi } from '@/api/client'
import type { SubmitCustomerResponse } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

/**
 * 구매자 정보(이름·이메일·휴대전화) 입력 단계.
 *
 * **지갑 연결보다 앞이다**(ADR-008, 계약 4.3). 서명 이후에 입력을 요구하면 돈은 나갔는데
 * 결제가 미완인 창이 생긴다 — 그래서 이 화면을 통과해야 지갑 버튼이 나온다.
 *
 * ## 형식 검증을 여기서 다시 하지 않는다
 *
 * 비어 있는지만 브라우저가 막고(`required`), 이메일·휴대전화 **형식은 서버가 판정한다.**
 * 같은 규칙을 양쪽에 두면 갈리고, 갈리면 느슨한 쪽이 기준이 된다. 서버는 이미 한글 메시지로
 * 이유를 돌려주므로(`구매자 이메일 형식이 올바르지 않습니다.`) 그대로 보여준다.
 *
 * `type="email"`은 검증이 아니라 **모바일 키보드를 바꾸려고** 쓴다(`type="tel"`도 같다).
 */
export function CustomerForm({
	sessionId,
	onSubmitted,
}: {
	sessionId: string
	/** 입력이 접수되면 호출한다 — 호출부가 지갑 단계로 넘긴다. */
	onSubmitted: (masked: SubmitCustomerResponse) => void
}) {
	const [name, setName] = useState('')
	const [email, setEmail] = useState('')
	const [phone, setPhone] = useState('')
	const [error, setError] = useState<string | null>(null)
	const [submitting, setSubmitting] = useState(false)

	async function submit(event: React.FormEvent) {
		event.preventDefault()
		setSubmitting(true)
		setError(null)
		try {
			onSubmitted(await checkoutApi.submitCustomer(sessionId, { name, email, phone }))
		} catch (cause) {
			setError(cause instanceof Error ? cause.message : String(cause))
		} finally {
			setSubmitting(false)
		}
	}

	return (
		<form className="space-y-3" onSubmit={submit} noValidate={false}>
			<div className="space-y-1">
				<p className="text-sm font-medium">구매자 정보</p>
				<p className="text-xs text-muted-foreground">
					결제에 문제가 생겼을 때 연락드릴 수단입니다. 가맹점에는 가려진 형태로만 전달됩니다.
				</p>
			</div>

			<div className="space-y-1.5">
				<Label htmlFor="customer-name">이름</Label>
				<Input
					id="customer-name"
					name="name"
					autoComplete="name"
					required
					value={name}
					onChange={(event) => setName(event.target.value)}
				/>
			</div>

			<div className="space-y-1.5">
				<Label htmlFor="customer-email">이메일</Label>
				<Input
					id="customer-email"
					name="email"
					type="email"
					autoComplete="email"
					required
					value={email}
					onChange={(event) => setEmail(event.target.value)}
				/>
			</div>

			<div className="space-y-1.5">
				<Label htmlFor="customer-phone">휴대전화</Label>
				<Input
					id="customer-phone"
					name="phone"
					type="tel"
					inputMode="numeric"
					autoComplete="tel"
					placeholder="010-1234-5678"
					required
					value={phone}
					onChange={(event) => setPhone(event.target.value)}
				/>
			</div>

			{error && <p className="text-sm text-destructive">{error}</p>}

			<Button type="submit" className="w-full" size="lg" disabled={submitting}>
				{submitting ? '확인하는 중…' : '다음'}
			</Button>
		</form>
	)
}
