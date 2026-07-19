import { useState } from 'react'
import { FlaskConical } from 'lucide-react'
import { Button } from '@/components/ui/button'

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
 * API Key와 수취 지갑은 `.env.local`(gitignore)에서만 읽는다. 값이 없으면 기능을 끄고
 * 안내만 한다 — 둘 다 코드에 기본값으로 박아두지 않는다.
 */
export function DevPaymentCreator({ onCreated }: { onCreated: (sessionId: string) => void }) {
	const [busy, setBusy] = useState(false)
	const [error, setError] = useState<string | null>(null)

	if (!import.meta.env.DEV) return null

	const apiKey: string | undefined = import.meta.env.VITE_DEV_API_KEY
	const receivingWallet: string | undefined = import.meta.env.VITE_DEV_RECEIVING_WALLET
	const baseUrl: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081'

	if (!apiKey) {
		return <MissingEnv name="VITE_DEV_API_KEY" />
	}

	// **수취 지갑에 기본값을 두지 않는다.** 한때 여기에 USDC 토큰 Contract 주소가
	// 하드코딩돼 있었는데, 그대로 테스트하면 토큰을 Contract 자신에게 보내게 되고
	// 되찾을 수 없다. 원래 가맹점이 결제를 만들 때 지정하는 값이라 "그럴듯한 기본값"
	// 자체가 존재할 수 없다 — 없으면 기능을 끄는 것이 맞다.
	if (!receivingWallet) {
		return <MissingEnv name="VITE_DEV_RECEIVING_WALLET" />
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
					receivingWallet,
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
		<DevBar>
			<div className="flex flex-wrap items-center gap-2">
				<Button size="sm" variant="outline" onClick={createPayment} disabled={busy}>
					{busy ? '만드는 중…' : '테스트 결제 생성'}
				</Button>
				<span className="text-muted-foreground">가맹점 서버 역할을 대신합니다(API Key 사용)</span>
			</div>
			{error && <p className="mt-2 text-destructive">{error}</p>}
		</DevBar>
	)
}

/** 설정이 빠졌을 때의 안내. 무엇을 채워야 하는지만 알려주고 기능은 끈다. */
function MissingEnv({ name }: { name: string }) {
	return (
		<DevBar>
			<span className="text-muted-foreground">
				테스트 결제를 만들려면 <code className="font-mono">frontend/payment/.env.local</code>에{' '}
				<code className="font-mono">{name}</code>을(를) 설정하세요(
				<code className="font-mono">.env.example</code>와 <code className="font-mono">docs/guides/testnet-wallet-setup.md</code> 참고).
			</span>
		</DevBar>
	)
}

/**
 * DEV 도구임이 한눈에 보이도록 점선 테두리로 감싼다 — 결제 화면의 카드와 섞이면
 * 실제 결제 UI의 일부로 오해할 수 있다.
 */
function DevBar({ children }: { children: React.ReactNode }) {
	return (
		<aside className="rounded-lg border border-dashed bg-background/60 p-3 text-xs">
			<p className="mb-2 flex items-center gap-1.5 font-semibold tracking-wide uppercase">
				<FlaskConical className="size-3.5" aria-hidden />
				Dev
			</p>
			{children}
		</aside>
	)
}
