import type { ReactNode } from 'react'
import { render } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

/**
 * react-query에 의존하는 컴포넌트·훅을 테스트할 때 쓰는 Provider.
 *
 * **테스트마다 새 `QueryClient`를 만든다** — 캐시를 공유하면 앞 테스트가 넣어 둔
 * 데이터를 뒤 테스트가 그대로 받아서, 통과해야 할 이유 없이 통과한다.
 *
 * `retryDelay: 0`이 중요하다. 재시도 자체는 각 훅이 정하지만(`useCheckout.ts`),
 * 기본 지연이 지수 백오프라 재시도가 일어나는 테스트가 몇 초씩 걸린다.
 */
export function createTestQueryClient(): QueryClient {
	return new QueryClient({
		defaultOptions: {
			queries: {
				retryDelay: 0,
				// 테스트 실패 메시지를 콘솔 에러로 뒤덮지 않는다.
				gcTime: Infinity,
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
