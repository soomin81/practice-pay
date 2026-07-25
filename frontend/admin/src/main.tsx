import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import App from './App.tsx'

// 서버 상태는 react-query가 갖고, 나머지는 컴포넌트 안의 useState로 끝난다 —
// 지금 공유해야 할 클라이언트 상태가 없어 상태 라이브러리를 넣지 않는다(다른 앱과 같은 판단).
const queryClient = new QueryClient({
	defaultOptions: {
		queries: {
			refetchOnWindowFocus: false,
			// 미인증(401)은 로그아웃 상태를 뜻하므로 재시도하지 않는다.
			retry: false,
		},
	},
})

createRoot(document.getElementById('root')!).render(
	<StrictMode>
		<QueryClientProvider client={queryClient}>
			<BrowserRouter>
				<App />
			</BrowserRouter>
		</QueryClientProvider>
	</StrictMode>,
)
