import { useState } from 'react'

/**
 * 개발 중에 체크아웃을 시작할 `checkoutSessionId`를 만들어주는 도구다.
 *
 * 체크아웃은 원래 **가맹점 서버**가 `POST /api/v1/payments`(API Key 인증)로 결제를
 * 만들고 그 ID로 고객을 보내면서 시작한다. 개발할 때마다 curl을 치지 않으려고 그
 * 역할을 이 버튼이 대신한다.
 *
 * **운영 번들에 절대 들어가면 안 된다.** 두 겹으로 막는다.
 *  1. 호출부(App)가 `import.meta.env.DEV`로 감싼다 — 프로덕션 빌드에서는 이 트리
 *     자체가 제거된다(Vite가 상수로 치환해 dead code로 만든다).
 *  2. 이 컴포넌트도 스스로 `import.meta.env.DEV`를 확인하고 아니면 아무것도 그리지 않는다.
 *
 * API Key는 `.env.local`(gitignore)에서만 읽는다. 값이 없으면 기능을 끄고 안내만 한다 —
 * 키를 코드에 기본값으로 박아두지 않는다.
 */
export function DevPaymentCreator({ onCreated }: { onCreated: (sessionId: string) => void }) {
	const [busy, setBusy] = useState(false)
	const [error, setError] = useState<string | null>(null)

	if (!import.meta.env.DEV) return null

	const apiKey: string | undefined = import.meta.env.VITE_DEV_API_KEY
	const baseUrl: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081'

	if (!apiKey) {
		return (
			<aside className="dev-bar">
				<strong>DEV</strong> 테스트 결제를 만들려면 <code>frontend/payment/.env.local</code>에{' '}
				<code>VITE_DEV_API_KEY</code>를 설정하세요(<code>.env.example</code> 참고).
			</aside>
		)
	}

	async function createPayment() {
		setBusy(true)
		setError(null)
		try {
			const response = await fetch(`${baseUrl}/api/v1/payments`, {
				method: 'POST',
				headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${apiKey}` },
				body: JSON.stringify({
					merchantOrderId: `dev-${Date.now()}`,
					orderName: '개발용 테스트 주문',
					orderAmount: 50000,
					network: 'BASE_SEPOLIA',
					receivingWallet: '0x036CbD53842c5426634e7929541eC2318f3dCF7e',
					successUrl: 'https://merchant.example.com/done',
					cancelUrl: 'https://merchant.example.com/cancel',
				}),
			})

			if (!response.ok) {
				const body = (await response.json().catch(() => ({}))) as { message?: string }
				throw new Error(body.message ?? `결제 생성 실패 (HTTP ${response.status})`)
			}

			const created = (await response.json()) as { checkoutSessionId: string }
			onCreated(created.checkoutSessionId)
		} catch (cause) {
			setError(cause instanceof Error ? cause.message : String(cause))
		} finally {
			setBusy(false)
		}
	}

	return (
		<aside className="dev-bar">
			<strong>DEV</strong>
			<button onClick={createPayment} disabled={busy}>
				{busy ? '만드는 중…' : '테스트 결제 생성'}
			</button>
			<span className="muted">가맹점 서버 역할을 대신합니다(API Key 사용)</span>
			{error && <span className="error-text">{error}</span>}
		</aside>
	)
}
