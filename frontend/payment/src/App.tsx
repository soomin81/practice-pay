import { useState } from 'react'
import { CheckoutPage } from './checkout/CheckoutPage'
import { DevPaymentCreator } from './dev/DevPaymentCreator'
import './App.css'

/**
 * 세션 식별자는 쿼리 파라미터로 받는다: `/?session=cs_xxx`
 *
 * `docs/architecture/checkout-api.md`의 8절이 "체크아웃 페이지 URL 자체"를 미정으로
 * 남겨뒀던 부분이고, 여기서 그렇게 정했다 — 화면이 하나뿐이라 라우터를 들이지 않기
 * 위해서다. 경로 방식(`/c/cs_xxx`)으로 바꾸려면 라우터를 추가하면 되고, 백엔드 계약은
 * 영향받지 않는다.
 */
function readSessionIdFromUrl(): string | null {
	return new URLSearchParams(window.location.search).get('session')
}

export default function App() {
	const [sessionId, setSessionId] = useState<string | null>(readSessionIdFromUrl)

	function useSession(id: string) {
		// 새로고침해도 같은 세션으로 돌아오도록 주소를 함께 바꾼다.
		const url = new URL(window.location.href)
		url.searchParams.set('session', id)
		window.history.replaceState(null, '', url)
		setSessionId(id)
	}

	return (
		<main>
			<header>
				<h1>결제</h1>
			</header>

			{import.meta.env.DEV && <DevPaymentCreator onCreated={useSession} />}

			{sessionId ? (
				<CheckoutPage sessionId={sessionId} />
			) : (
				<section className="panel">
					<h2>결제 정보가 없습니다</h2>
					<p>
						가맹점에서 받은 결제 링크로 접속해 주세요. 주소에 <code>?session=</code> 값이 필요합니다.
					</p>
				</section>
			)}
		</main>
	)
}
