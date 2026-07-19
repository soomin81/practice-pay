import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import './index.css'
import App from './App.tsx'

// 폴링 주기와 재시도는 각 훅이 상황에 맞게 정한다(useCheckout.ts) — 여기서는
// 전역 기본값만 보수적으로 둔다. 특히 창 포커스마다 다시 부르는 기본 동작은 끈다:
// 체크아웃은 고객이 지갑 앱으로 전환했다 돌아오는 일이 잦은데, 그때마다 세션 정보를
// 다시 읽을 이유가 없다(상태 폴링은 별도로 계속 돈다).
const queryClient = new QueryClient({
	defaultOptions: {
		queries: {
			refetchOnWindowFocus: false,
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
