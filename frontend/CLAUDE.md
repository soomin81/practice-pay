# CLAUDE.md (frontend)

`frontend/`에서 작업할 때의 지침이다. 공통 결제 도메인(애그리게이트, 상태 머신, MVP 범위)은 루트 `../CLAUDE.md`를 참고한다 — 이 문서는 프론트엔드 구현 컨벤션만 다룬다.

## 구조 — 앱 셋이 생긴다

백엔드가 상대하는 대상별로 앱을 나눈 것과 그대로 대응한다.

| 디렉토리 | 대상 | 호출하는 백엔드 | 상태 |
|---|---|---|---|
| `payment/` | **고객**(Hosted Checkout) | `api-payment` `:8081`의 `/checkout/**` | **구현 중** |
| `merchant/` | 가맹점 운영자 | `api-merchant` `:8083` | 아직 없음 |
| `admin/` | PG 내부 운영자 | `api-admin` `:8082` | 아직 없음 |

**워크스페이스(pnpm/npm workspaces)를 쓰지 않는다 — 각 앱이 독립 프로젝트다.** 셋이 호출하는 API도 타입도 인증 방식도 전부 달라서 지금 공유할 것이 실질적으로 없다. 진짜 공유될 만한 UI 컴포넌트는 **두 번째 앱을 만들 때 무엇이 겹치는지 드러난 뒤** `frontend/packages/`로 뽑는다. 이 판단은 백엔드의 "지금 실제로 하는 일에만 맞춘다 — 나중에 할 일까지 미리 넣지 않는다"와 같은 원칙이다.

**merchant/admin을 만들 때는 CORS가 지금보다 까다롭다.** 그 둘은 세션 쿠키 인증이라 교차 출처에서 쿠키를 보내려면 `allowCredentials = true` + 정확한 Origin + `SameSite=None; Secure`가 필요하다. `payment`는 쿠키를 쓰지 않아 이 문제가 없다(그래서 백엔드가 `allowCredentials`를 꺼 뒀다).

## 실행 — 호스트 Node로 돌린다

호스트에 Node 24가 설치돼 있다(`node -v`로 확인). 백엔드와 달리 **프론트엔드는 Docker를 쓰지 않는다** — 컨테이너가 필요한 이유였던 "호스트 Node 없음"이 해소됐고, Docker 경유는 Windows 바인드 마운트 때문에 폴링·볼륨 우회를 계속 달고 다녀야 했다.

```
cd frontend/payment
npm install
npm run dev            # http://localhost:5173
npm test
npm run build
npm run lint
npm run gen:api        # OpenAPI 스펙 → src/api/schema.d.ts
```

Node 메이저 버전을 올릴 때는 호스트에 그 버전을 설치하는 것이 곧 전환이다 — 고정해 두는 파일이 없으므로, 버전에 민감한 문제가 생기면 `node -v`부터 확인한다.

호스트로 옮기면서 정리한 것 둘이다:

| 설정 | 이유 |
|---|---|
| `server.watch.usePolling` **제거** | Docker Desktop의 Windows 바인드 마운트가 inotify를 전달하지 않아 켜 뒀던 것이다. 호스트에서는 파일 이벤트가 그대로 오므로 폴링은 CPU만 쓴다 |
| `server.strictPort: true` **추가** | 5173은 api-payment의 CORS 허용 목록에 있는 포트다. strictPort가 없으면 5173이 점유됐을 때 Vite가 조용히 5174로 옮겨 가고, **Origin이 달라져 모든 요청이 CORS로 막힌다** — 원인이 드러나지 않으므로 차라리 기동에 실패시킨다 |

**`gen:api`는 `npx -y openapi-typescript@7`로 격리 실행한다(devDependency로 넣지 않는다).** 그 도구가 peer로 `typescript@^5`를 요구하는데 이 앱은 TS 6이라, 프로젝트에 설치하면 TS 컴파일러 API를 쓰는 그 도구가 조용히 잘못된 결과를 낼 수 있다. npx 캐시는 프로젝트 트리 밖이라 앱의 TS 6과 서로 보이지 않는다 — Docker에서 `gen-api`를 별도 서비스로 뒀던 것과 같은 격리를 같은 이유로 유지하는 것이다.

## API — `docs/architecture/checkout-api.md`가 기준이다

**백엔드 소스를 읽어서 API 형태를 추론하지 않는다.** 경로·요청·응답·오류 코드는 전부 그 문서에 있다.

```
cd backend && gradlew.bat :apps:api-payment:openapi3   # 스펙 생성
cd ../frontend/payment && npm run gen:api             # → src/api/schema.d.ts
```

스펙은 커밋 대상이 아니라 `backend/apps/api-payment/build/api-spec/` 아래 생성물이라, 위 Gradle 태스크를 먼저 돌리지 않으면 `gen:api`가 "파일 없음"으로 실패한다.

- **`schema.d.ts`는 생성물이라 손대지 않는다.** 별칭은 `src/api/types.ts`에만 둔다 — 백엔드가 필드를 바꾸면 그 별칭을 쓰는 화면 코드에서 컴파일 에러가 나는 것이 이 계층의 목적이다.
- **다만 `schema.d.ts`는 커밋한다** — jOOQ 생성 코드나 `openapi3.yaml`을 커밋하지 않는 것과 다른 판단이다. 이게 없으면 새로 클론했을 때 백엔드 Gradle 빌드를 먼저 돌려야 프론트가 빌드되고, API가 바뀔 때 **타입 diff가 코드 리뷰에 보이지 않는다.** 커밋해 두면 "이번 변경으로 응답 형태가 이렇게 바뀐다"가 diff에 그대로 드러난다.
- **오류 응답은 스펙에 없다**(MockMvc가 컨테이너 오류 디스패치를 재현하지 못해 백엔드가 의도적으로 뺐다). `CheckoutApiError.status`로 분기하고, 기준은 계약 문서 5절이다.
- 스펙을 다시 생성했으면 **`schema.d.ts`를 눈으로 확인한다.** 백엔드에서 `.type()`이나 부모 객체 서술자가 빠지면 필드가 **조용히 사라지거나 optional로** 나온다(아래 참고).

## UI — Tailwind v4 + shadcn/ui

CSS 파일을 따로 쓰지 않는다. 스타일은 전부 Tailwind 유틸리티 클래스이고, 공통 UI는 shadcn/ui가 생성한 컴포넌트를 쓴다.

```
src/components/ui/            shadcn 생성물 — 손대지 않는다
src/lib/utils.ts              shadcn의 cn() (clsx + tailwind-merge)
src/checkout/components/      이 앱의 화면 컴포넌트
src/index.css                 Tailwind import + shadcn 테마 변수
```

- **`src/components/ui/`는 생성물로 취급한다.** 고치고 싶으면 그 파일을 직접 바꾸지 말고 감싸는 컴포넌트를 `src/checkout/components/`에 만든다 — `npx shadcn@latest add`를 다시 돌리면 덮어써진다. oxlint도 이 디렉토리를 `ignorePatterns`로 제외한다(생성 코드가 `only-export-components` 경고를 낸다).
- **컴포넌트 추가는 CLI로 한다**: `npx shadcn@latest add <name>`. `init`은 이미 끝났고 설정은 `components.json`에 있다(스타일 `radix-nova`, base color `neutral`, CSS 변수 방식).
- **import 별칭 `@/`는 `src/`를 가리킨다.** shadcn 생성 코드가 그 형태로 import하기 때문에 필수다. `tsconfig.json`/`tsconfig.app.json`의 `paths`와 `vite.config.ts`의 `resolve.alias` **양쪽에** 있어야 한다 — 전자는 타입 검사용, 후자는 번들러용이라 하나만 있으면 다른 쪽에서 깨진다.
- **`baseUrl`은 두지 않는다.** shadcn 문서는 `paths`와 함께 두라고 하지만 TypeScript 6에서 deprecated라 빌드가 TS5101로 막힌다. TS 6부터 `paths`는 tsconfig 위치 기준으로 해석되므로 `baseUrl` 없이 그대로 동작한다.

**화면 컴포넌트는 상태 분기와 분리한다.** `CheckoutPage.tsx`는 서버가 준 상태를 보고 **어떤 화면을 그릴지만** 정하고, 모양은 `checkout/components/`가 갖는다:

| 컴포넌트 | 역할 |
|---|---|
| `CheckoutShell` | 페이지 바깥 틀. 모든 상태 화면이 같은 폭·여백을 쓰게 해서 상태가 바뀔 때 카드가 튀지 않게 한다 |
| `StatusScreen` | 완료·만료·취소·실패·로딩을 한 컴포넌트로. **tone은 호출부가 정한다** — 이 컴포넌트가 상태를 해석하지 않는다 |
| `PayScreen` | 결제 진행 본 화면. 취소 버튼이 여기에만 있다(`PAYMENT_SUBMITTED` 이후 고객 취소 불가) |
| `PaymentSummary` | 주문 금액(KRW)과 보낼 토큰 금액 |
| `PaymentDetails` | 네트워크·수취 지갑·Contract·환율·남은 시간 |
| `ConfirmationProgress` | Confirmation 진행률 |
| `WalletConnectSlot` | 2단계(wagmi + viem) 자리. **이 블록만 교체하면 되도록** 나머지가 의존하지 않게 두었다 |

주소·해시는 `shortenHex`로 줄여 보여주되 **전체 값을 `title`에 남긴다** — 고객이 지갑 화면과 대조할 수 있어야 한다.

## 지킬 것 셋

**① 토큰 금액을 `Number`로 변환하지 않는다.** 스펙이 `payment.amount`를 `type: string`으로 주는 이유다 — Minor Unit이 JavaScript `Number`의 안전 정수 범위(2^53-1)를 넘을 수 있다. `src/checkout/format.ts`의 `formatTokenAmount`가 문자열 자리수만 잘라 쓰고, 그 근거를 테스트로 남겼다(안전 범위를 넘는 값과 18-decimals 토큰 케이스).

**② 체인 정보를 상수로 박지 않는다.** `chainId`/`tokenContractAddress`/`receivingWallet`은 전부 `GET` 응답에서 받아 쓴다 — 토큰을 Symbol로 판단하지 않고 (네트워크, Contract 주소) 조합으로 다룬다는 도메인 규칙이 프론트에도 적용된다.

**③ 다음 상태를 스스로 추론하지 않는다.** 화면 분기는 서버가 준 `checkoutSessionStatus`/`paymentStatus`를 따르고, 리다이렉트는 상태 응답의 `redirectUrl`이 채워지는 것을 신호로 삼는다.

## 세션 식별자와 개발용 도구

- **세션은 쿼리 파라미터로 받는다**: `/?session=cs_xxx`. 화면이 하나라 라우터를 들이지 않았다(계약 문서 8절이 미정으로 남겨뒀던 부분을 여기서 정했다). 경로 방식으로 바꿔도 백엔드 계약은 영향받지 않는다.
- **DEV 전용 "테스트 결제 생성" 버튼**이 가맹점 서버 역할을 대신한다(`src/dev/`). 체크아웃은 `checkoutSessionId`가 있어야 시작하는데 그건 API Key가 필요한 `POST /api/v1/payments`가 만들기 때문이다.
  - API Key는 `.env.local`에서만 읽는다(`.env.example` 복사). 값이 없으면 버튼이 꺼지고 안내만 뜬다 — 키를 코드에 기본값으로 두지 않는다.
  - `import.meta.env.DEV`로 두 겹(호출부 + 컴포넌트 자신) 막는다. **프로덕션 번들에 DEV 컴포넌트와 API Key가 들어가지 않는 것을 빌드 산출물에서 직접 확인했다** — 번들을 바꾸는 변경 뒤에는 다시 확인한다.

## 테스트

Vitest + Testing Library. `npm test`(1회 실행) / `npm run test:watch`.

**지갑 연결과 전송은 자동 테스트가 사실상 불가능하다** — MetaMask 확장이 필요하다. 그래서 단위 테스트는 상태 기계·포맷·API 클라이언트까지만 의미가 있고, 실제 전송은 백엔드와 같은 규율로 **실물 수동 검증**이 최종 확인이 된다(Base Sepolia 테스트넷 USDC 필요).

화면 컴포넌트는 `checkout/components/screens.test.tsx`에 스모크 테스트가 있다. 잡으려는 것은 둘이다: 컴포넌트가 마운트 중에 터지지 않는 것, 그리고 **금액·주소·Confirmation이 서버 값 그대로 나오는 것**(안전 정수 범위를 넘는 금액을 픽스처에 일부러 넣어 뒀다 — `Number`를 거치면 여기서 깨진다).

**단, 테스트는 "보기에 멀쩡한지"를 확인해주지 않는다.** Tailwind 클래스는 오타가 나도 조용히 무시되고 컴파일도 통과한다. 레이아웃을 바꿨으면 `npm run dev`로 **눈으로 확인한다.**

## 현재 상태와 다음

- **된 것**: 스캐폴딩, 타입 생성, API 클라이언트, 상태별 화면, 3초 폴링, DEV 결제 생성 버튼. 백엔드를 띄운 상태로 CORS·Preflight·조회·지갑 연결까지 실물 확인했다. UI는 Tailwind v4 + shadcn/ui로 옮겼고 화면 컴포넌트를 `checkout/components/`로 분리했다.
- **안 된 것**: 지갑 연결(wagmi + viem)과 ERC-20 `transfer`. `WalletConnectSlot`에 자리만 잡아 뒀다.
- **아직 눈으로 안 본 것**: Tailwind 전환 후의 화면. 빌드·테스트·클래스 생성까지는 확인했지만 브라우저에서 렌더된 모습은 확인하지 못했다(작업 당시 브라우저 자동화 확장이 연결돼 있지 않았다). 다음에 `npm run dev`로 각 상태 화면을 한 번 훑는 것이 좋다.
- 명령어는 위 "실행" 절 참고. 프로젝트가 더 생기면 이 문서에 앱별 절을 나눈다.
