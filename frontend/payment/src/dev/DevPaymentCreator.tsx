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
 * API Key는 `.env.local`(gitignore)에서만 읽는다. 값이 없으면 기능을 끄고 안내만 한다 —
 * 코드에 기본값으로 박아두지 않는다.
 *
 * **수취 지갑은 더 이상 여기서 보내지 않는다.** PG가 수탁하는 지갑이라 백엔드 설정
 * (`APP_PAYMENT_RECEIVING_WALLETS_BASE_SEPOLIA`)에서만 온다 — 가맹점 역할이 지정할 수
 * 있으면 USDC를 직접 받으면서 정산 채권까지 받는다(`docs/architecture/mvp-scope.md`의
 * "수취 지갑 귀속"). 백엔드에 그 설정이 없으면 이 버튼이 503을 받는다.
 */
export function DevPaymentCreator({ onCreated }: { onCreated: (sessionId: string) => void }) {
	const [busy, setBusy] = useState(false)
	const [error, setError] = useState<string | null>(null)

	if (!import.meta.env.DEV) return null

	const apiKey: string | undefined = import.meta.env.VITE_DEV_API_KEY
	const baseUrl: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8081'

	if (!apiKey) {
		return <MissingEnv name="VITE_DEV_API_KEY" />
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
					// Faucet 한 번(2시간에 20 USDC)으로 결제 한 건이 끝나도록 정한 금액이다.
					// 20,000원 ÷ 1,393(적용 환율) = 14.357502 USDC — 50,000원이면 35.89 USDC라
					// 두 번 받아야 해서 테스트 한 바퀴에 2시간이 걸렸다.
					orderAmount: 20000,
					network: 'BASE_SEPOLIA',
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
