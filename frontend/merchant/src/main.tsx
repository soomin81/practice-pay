import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import './index.css'
import App from './App.tsx'

// payment 앱과 달리 wagmi/지갑이 없어 Provider는 react-query 하나뿐이다.
// 서버 상태는 react-query가, 나머지는 컴포넌트 안의 useState가 갖는다 — 지금 공유해야
// 할 클라이언트 상태가 없어 Zustand 등 상태 라이브러리는 넣지 않는다(payment와 같은 판단).
const queryClient = new QueryClient({
	defaultOptions: {
		queries: {
			refetchOnWindowFocus: false,
			// 미인증(401)은 로그아웃 상태를 뜻하므로 재시도하지 않는다 — 각 훅에서 다룬다.
			retry: false,
		},
	},
})

createRoot(document.getElementById('root')!).render(
	<StrictMode>
		<QueryClientProvider client={queryClient}>
			<App />
		</QueryClientProvider>
	</StrictMode>,
)
