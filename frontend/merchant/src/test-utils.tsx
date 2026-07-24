import type { ReactNode } from 'react'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

/**
 * react-query에 의존하는 컴포넌트·훅을 테스트할 때 쓰는 Provider(payment의
 * test-utils.tsx와 같은 이유·같은 방식).
 *
 * **테스트마다 새 `QueryClient`를 만든다** — 캐시를 공유하면 앞 테스트 데이터가 새어
 * 통과해야 할 이유 없이 통과한다. `retries`는 끄고 `retryDelay: 0`으로 둬서 실패
 * 경로 테스트가 백오프로 몇 초씩 걸리지 않게 한다.
 */
export function createTestQueryClient(): QueryClient {
	return new QueryClient({
		defaultOptions: {
			queries: {
				retry: false,
				retryDelay: 0,
				gcTime: Infinity,
			},
			mutations: {
				retry: false,
			},
		},
	})
}

export function Providers({ client, children }: { client: QueryClient; children: ReactNode }) {
	return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

/** `render`에 Provider를 씌운 것. 반환값에 `client`를 함께 준다. */
export function renderWithQuery(ui: ReactNode, client: QueryClient = createTestQueryClient()) {
	return {
		client,
		...render(<Providers client={client}>{ui}</Providers>),
	}
}
