/**
 * Vitest 전역 설정(`vite.config.ts`의 `test.setupFiles`).
 *
 * jest-dom 매처를 Vitest의 `expect`에 등록한다 — `toBeInTheDocument()`,
 * `toHaveTextContent()`처럼 DOM을 상대로 의도가 드러나는 단언을 쓰기 위해서다.
 * 이게 없으면 `toBeTruthy()` 같은 약한 단언으로 우회하게 되는데, 그러면 무엇이
 * 깨졌는지가 실패 메시지에 남지 않는다.
 *
 * `/vitest` 진입점을 쓰는 것이 중요하다. 기본 진입점은 Jest의 전역 `expect`를
 * 가정해서 Vitest에서는 매처가 등록되지 않는다.
 */
import '@testing-library/jest-dom/vitest'
import { afterEach } from 'vitest'
import { cleanup } from '@testing-library/react'

/**
 * 렌더한 DOM을 테스트마다 치운다.
 *
 * **직접 등록해야 한다.** Testing Library는 `afterEach`가 전역에 있을 때만 자동
 * cleanup을 붙이는데, 이 프로젝트는 `globals: false`라(vite.config.ts) 전역이 없어서
 * 그 경로를 타지 않는다.
 *
 * 없으면 앞 테스트가 그린 화면이 문서에 남아 다음 테스트에 보인다 — 한 파일에서
 * 비슷한 화면을 여러 번 그릴 때 `queryBy...().not.toBeInTheDocument()`가 앞 테스트의
 * 잔해를 발견하면서 **엉뚱한 곳에서 실패한다.** 반대로 통과하면 안 될 단언이 통과할
 * 수도 있다.
 */
afterEach(cleanup)
