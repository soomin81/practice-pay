# CLAUDE.md (frontend)

`frontend/`에서 작업할 때의 지침이다. 공통 결제 도메인(애그리게이트, 상태 머신, MVP 범위)은 루트 `../CLAUDE.md`를 참고한다 — 이 문서는 프론트엔드 구현 컨벤션만 다룬다.

## 구조 — 앱 셋이 생긴다

백엔드가 상대하는 대상별로 앱을 나눈 것과 그대로 대응한다.

| 디렉토리 | 대상 | 호출하는 백엔드 | 상태 |
|---|---|---|---|
| `payment/` | **고객**(Hosted Checkout) | `api-payment` `:8081`의 `/checkout/**` | **구현 중** |
| `merchant/` | 가맹점 운영자 | `api-merchant` `:8083`의 `/merchant/**` | **구현 중**(API Key 관리 + 팀 계정·초대) |
| `admin/` | PG 내부 운영자 | `api-admin` `:8082`의 `/admin/**` | **구현 중**(로그인 → 가맹점 목록·등록·상세(사용자 관리) + 내부 직원 명부·발급·계정 관리 + 로그인 감사(내부·가맹점)) |

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
  - **손으로 비슷하게 써 두고 나중에 CLI로 맞추면 variant가 어긋난다.** merchant의 `input`/`label`/`badge`를 그렇게 만들었다가 CLI로 교체했더니, 임의로 넣었던 `badge`의 `muted` variant가 실제 생성물에 없어서 그걸 쓰던 화면이 컴파일 에러로 드러났다(실제 목록은 `default`/`secondary`/`destructive`/`outline`/`ghost`/`link`). 다른 앱에서 복사하는 것도 같은 위험이 있으니 **처음부터 CLI로 받는다** — 교체 자체는 `--overwrite --yes`로 비대화형 실행이 되고, 그때 `package.json`/`components.json`/`index.css`는 건드리지 않는다.
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
| `WalletPanel` | 지갑 연결과 USDC 전송. 흐름은 `wallet/useWalletPayment.ts`가 갖고 여기서는 단계만 그린다 |

주소·해시는 `shortenHex`로 줄여 보여주되 **전체 값을 `title`에 남긴다** — 고객이 지갑 화면과 대조할 수 있어야 한다.

## 지갑 — wagmi + viem

`src/wallet/`에 있다. 흐름은 계약 문서 7절 그대로다: 지갑 연결 → `POST /wallet` → ERC-20 `transfer` 서명·브로드캐스트 → `POST /transaction` → 이후는 상태 폴링이 이어받는다.

| 파일 | 역할 |
|---|---|
| `config.ts` | wagmi 설정. 체인 목록(`baseSepolia`)과 `injected` 커넥터 |
| `erc20.ts` | `transfer`만 담은 ABI |
| `useWalletPayment.ts` | 연결·전환·서명·제출 흐름과 단계(`PaymentStep`) |

- **`WagmiProvider`는 `QueryClientProvider` 바깥에 있어야 한다**(`main.tsx`). wagmi가 내부적으로 react-query를 쓰기 때문에, 안쪽에 두면 wagmi 훅이 QueryClient를 찾지 못한다.
- **`config.ts`의 `baseSepolia`는 "이 결제가 Base Sepolia다"라는 선언이 아니다.** wagmi가 설정 시점에 체인을 알아야 transport를 만들 수 있어서 두는 **앱이 말을 걸 수 있는 체인 목록**이다. 결제가 실제로 어느 체인·어느 Contract를 쓰는지는 여전히 서버 응답(`session.payment.chainId` / `tokenContractAddress`)이 정한다.
- **모르는 체인이면 보내지 않는다.** 서버가 준 `chainId`를 `asSupportedChainId()`가 설정된 체인 목록과 대조해서 좁힌다. 타입만 맞추려면 캐스팅으로 끝낼 수 있는 자리지만, 그러면 백엔드가 네트워크를 추가하고 프론트가 안 따라온 상황에서 **지갑에게 엉뚱한 체인으로 보내라고 시키게 된다.** 주소도 같은 이유로 `asAddress()`가 형식을 확인한다. 둘 다 `wallet/guards.test.ts`가 지킨다.
- **금액은 `BigInt(session.payment.amount)`로만 옮긴다.** Minor Unit 문자열을 `Number`에 넣으면 안전 정수 범위를 넘을 때 조용히 값이 달라진다.
- **`POST /wallet`의 409는 오류가 아니다.** 재연결은 계약상 지원하지 않으므로(4.3), 이미 `WALLET_CONNECTED`인 세션에 다시 연결하면 409가 온다 — 새로고침 후 같은 지갑으로 붙는 정상 경로라 무시하고 진행한다.
- **`POST /transaction` 성공은 "결제됐다"가 아니라 "제출을 접수했다"이다**(4.4). 여기서 성공 화면으로 넘기지 않고, 세션을 다시 읽어 폴링 화면으로 넘긴다.
- WalletConnect는 넣지 않았다 — Project ID가 필요하다. 브라우저 확장 지갑(`injected`)만 지원한다.

## 지킬 것 셋

**① 토큰 금액을 `Number`로 변환하지 않는다.** 스펙이 `payment.amount`를 `type: string`으로 주는 이유다 — Minor Unit이 JavaScript `Number`의 안전 정수 범위(2^53-1)를 넘을 수 있다. `src/checkout/format.ts`의 `formatTokenAmount`가 문자열 자리수만 잘라 쓰고, 그 근거를 테스트로 남겼다(안전 범위를 넘는 값과 18-decimals 토큰 케이스).

**② 체인 정보를 상수로 박지 않는다.** `chainId`/`tokenContractAddress`/`receivingWallet`은 전부 `GET` 응답에서 받아 쓴다 — 토큰을 Symbol로 판단하지 않고 (네트워크, Contract 주소) 조합으로 다룬다는 도메인 규칙이 프론트에도 적용된다.

**③ 다음 상태를 스스로 추론하지 않는다.** 화면 분기는 서버가 준 `checkoutSessionStatus`/`paymentStatus`를 따르고, 리다이렉트는 상태 응답의 `redirectUrl`이 채워지는 것을 신호로 삼는다.

## 세션 식별자와 개발용 도구

- **세션은 쿼리 파라미터로 받는다**: `/?session=cs_xxx`. 화면이 하나라 라우터를 들이지 않았다(계약 문서 8절이 미정으로 남겨뒀던 부분을 여기서 정했다). 경로 방식으로 바꿔도 백엔드 계약은 영향받지 않는다.
- **DEV 전용 "테스트 결제 생성" 버튼**이 가맹점 서버 역할을 대신한다(`src/dev/`). 체크아웃은 `checkoutSessionId`가 있어야 시작하는데 그건 API Key가 필요한 `POST /api/v1/payments`가 만들기 때문이다.
  - **API Key와 수취 지갑(`VITE_DEV_RECEIVING_WALLET`)은 `.env.local`에서만 읽는다**(`.env.example` 복사). 둘 중 하나라도 없으면 버튼이 꺼지고 안내만 뜬다 — **어느 쪽도 코드에 기본값을 두지 않는다.**
  - 수취 지갑에 기본값을 두지 않는 이유는 API Key와 다르다. 이 값은 원래 가맹점이 결제를 만들 때 지정하는 것이라 "그럴듯한 기본값"이 존재할 수 없고, 기본값을 두면 **그 주소로 실제 테스트넷 USDC가 전송된다.** 실제로 한동안 USDC 토큰 Contract 주소가 여기 하드코딩돼 있었다(복붙 사고) — 그리로 보낸 토큰은 되찾을 수 없다. 준비 절차는 [`docs/guides/testnet-wallet-setup.md`](../docs/guides/testnet-wallet-setup.md).
  - `import.meta.env.DEV`로 두 겹(호출부 + 컴포넌트 자신) 막는다. **프로덕션 번들에 DEV 컴포넌트와 API Key가 들어가지 않는 것을 빌드 산출물에서 직접 확인했다** — 번들을 바꾸는 변경 뒤에는 다시 확인한다.

## 테스트

Vitest + Testing Library. `npm test`(1회 실행) / `npm run test:watch`.

**지갑 연결과 전송은 자동 테스트가 사실상 불가능하다** — MetaMask 확장이 필요하다. 그래서 단위 테스트는 상태 기계·포맷·API 클라이언트까지만 의미가 있고, 실제 전송은 백엔드와 같은 규율로 **실물 수동 검증**이 최종 확인이 된다(Base Sepolia 테스트넷 USDC 필요).

**jest-dom 매처는 `src/test-setup.ts`가 등록한다**(`vite.config.ts`의 `test.setupFiles`). 패키지만 설치하고 이 연결을 빠뜨리면 매처를 쓸 수 없어 `toBeTruthy()` 같은 약한 단언으로 우회하게 된다 — 실제로 한동안 그 상태였다. `@testing-library/jest-dom/vitest` 진입점을 써야 한다(기본 진입점은 Jest의 전역 `expect`를 가정한다).

화면 컴포넌트는 `checkout/components/screens.test.tsx`에 스모크 테스트가 있다. 잡으려는 것은 둘이다: 컴포넌트가 마운트 중에 터지지 않는 것, 그리고 **금액·주소·Confirmation이 서버 값 그대로 나오는 것**(안전 정수 범위를 넘는 금액을 픽스처에 일부러 넣어 뒀다 — `Number`를 거치면 여기서 깨진다).

**단, 테스트는 "보기에 멀쩡한지"를 확인해주지 않는다.** Tailwind 클래스는 오타가 나도 조용히 무시되고 컴파일도 통과한다. 레이아웃을 바꿨으면 `npm run dev`로 **눈으로 확인한다.**

## 앱: merchant (가맹점 콘솔) — payment와 다른 점만

`frontend/merchant`는 `payment`를 스캐폴딩 템플릿으로 미러링했다(Vite + React + TS6 +
Tailwind v4 + shadcn/ui + react-query + oxlint + vitest). **wagmi/viem(지갑)은 없다.**
공통 UI를 `frontend/packages/`로 뽑는 것은 여전히 두 앱의 실제 중복이 드러난 뒤로
미룬다 — 지금은 shadcn 생성물(`button`/`card`/`alert`)을 복사하고 나머지(`input`/
`label`/`badge`)만 이 앱에 두는 정도라 뽑아낼 만큼의 중복이 아니다.

계약 기준 문서는 [`docs/architecture/merchant-console-api.md`](../docs/architecture/merchant-console-api.md)다.

```
cd frontend/merchant
npm install
npm run dev            # http://localhost:5174  (payment가 5173이라 겹치지 않게 5174)
npm test / npm run build / npm run lint
npm run gen:api        # api-merchant의 openapi3.yaml → src/api/schema.d.ts
```

- **`gen:api`는 `api-merchant`의 스펙을 먼저 생성해야 한다**(payment와 같은 순서):
  `cd backend && gradlew.bat :apps:api-merchant:openapi3` → `build/api-spec/openapi3.yaml`
  → `npm run gen:api`. 스펙이 없으면 "파일 없음"으로 실패한다.
- **포트는 5174 고정(`strictPort`)이다.** api-merchant의 `app.merchant-console.allowed-origins`가
  이 Origin을 허용하는데, strictPort가 없으면 Vite가 조용히 다른 포트로 옮겨 가 세션
  쿠키 요청이 전부 CORS로 막힌다(payment의 5173과 같은 이유).

### payment와 결정적으로 다른 지점: 세션 쿠키 인증

- **모든 요청에 `credentials: 'include'`.** 세션 쿠키(`JSESSIONID`)로 인증한다
  (payment는 쿠키를 안 쓴다). `src/api/client.ts`가 이걸 붙인다.
- **상태 변경 요청(POST/DELETE)에는 CSRF 토큰을 실어야 한다.** `XSRF-TOKEN` 쿠키를 읽어
  `X-XSRF-TOKEN` 헤더로 되돌려준다(`client.ts`의 `csrfHeader()`). 쿠키는 안전한 GET
  응답에 실려 오는데, 앱 부팅 시 `useMe`(`GET /merchant/me`)가 먼저 받아 둔다 — 없으면
  `csrfHeader()`가 한 번 GET을 쳐서 받아온다. 없이 보내면 서버가 403이다.
- **오류는 status로 분기한다**(`MerchantApiError`, payment의 `CheckoutApiError` 대응):
  401=미인증(로그아웃), 403=CSRF/권한, 409=중복 등. **`me()`는 401을 오류가 아니라
  `null`(로그아웃)로 바꿔 돌려준다** — App이 그 `null`을 보고 로그인 화면을 그린다.
- **rawApiKey는 발급 응답에서만 보인다** — `IssueApiKeyForm`이 발급 직후 그 값을 크게
  경고와 함께 노출하고, "확인했습니다"를 누르면 다시 볼 수 없다(계약 6.4). 폐기 확인은
  브라우저 `confirm()`이 아니라 인라인 확인으로 한다(모달 dialog는 안 넣었다).

### 라우터 — 초대 수락이 인증 게이트 밖에 있다

2번째 슬라이스(팀 계정)에서 **react-router를 도입했다.** 페이지가 둘 이상이 된 것도
이유지만, 결정적인 것은 **초대 수락 페이지가 비인증 상태에서 별도 URL로 도달해야
한다**는 것이다 — 조건부 렌더링으로는 표현할 수 없다.

| 경로 | 인증 | 내용 |
|---|---|---|
| `/accept-invitation?token=…` | **비인증(공개)** | 초대 수락. 초대받은 사람은 아직 로그인할 수 없다 |
| `/` | 필요 | API Key 관리 |
| `/team` | 필요 | 팀 계정(명부 + 초대 발급) |

- **`App.tsx`에서 `/accept-invitation`을 인증 게이트보다 먼저 매칭시킨다.** 이 순서가
  깨지면 초대 링크가 로그인 화면으로 튕겨 활성화 흐름 자체가 성립하지 않는다 —
  이 슬라이스에서 가장 깨지기 쉬운 지점이라 **`src/routing.test.tsx`가 회귀로 지킨다.**
- 테스트는 `test-utils.tsx`의 `renderWithRouter(ui, { route })`로 시작 경로(쿼리스트링
  포함)를 정한다. `MemoryRouter`라 실제 주소창과 무관하다.
- **초대 토큰도 1회 노출 규칙이다**(rawApiKey와 같다). 다만 `InviteSubAccountForm`은
  토큰 문자열이 아니라 **바로 쓸 수 있는 초대 링크**(`{origin}/accept-invitation?token=…`)를
  보여준다 — MVP에 초대 메일 발송이 없어서 발급한 사람이 직접 전달해야 하기 때문이다.
- **역할 선택지에 `OWNER`가 없다**(`INVITABLE_ROLES`) — 하위 계정 발급으로는 OWNER를
  만들 수 없다는 도메인 규칙이 화면에도 그대로 반영된다. 역할 **변경** 폼도 같은 목록을
  재사용한다.

### 명부의 행 액션 (3번째 슬라이스)

`MerchantUserActions`가 **계정 상태에 따라 다른 액션**을 그린다 — 도메인 상태 머신을
화면에 옮긴 것이다: `ACTIVE`→정지·종료·역할 변경, `SUSPENDED`→재개·종료,
`INVITED`→종료만, `TERMINATED`/`LOCKED`→없음.

- **자기 자신 행에는 액션 대신 "본인"을 그린다.** 서버도 403으로 막지만, 누를 수 있게
  두고 거부하는 것보다 감추는 편이 낫다. 현재 사용자는 `TeamPage`가 `useMe()`에서
  받아 `MerchantUserTable`에 넘긴다(캐시된 쿼리라 추가 요청이 없다).
- **OWNER 행에는 역할 변경을 두지 않는다** — 강등은 서버 규칙(마지막 OWNER 보호,
  ADMIN 차단)에 걸리기 쉬워 화면에서 미리 뺐다.
- 확인은 `ApiKeyTable`의 **인라인 확인 패턴**을 그대로 쓴다. 종료는 되돌릴 수 없다는
  것을 확인 문구에 적는다.
- 오류는 status로 분기한다: 409(마지막 OWNER 보호·허용되지 않는 전이)는 **서버 메시지를
  그대로 보여준다**(둘을 프론트가 구분할 수 없고, 서버 문구가 이미 구체적이다).

### 초대 관리 (4번째 슬라이스)

`INVITED` 행에는 **초대 상태**와 **재발송/취소**가 함께 붙는다.

- **만료 판단은 화면이 한다.** 서버는 만료를 알려주지 않는다 — 만료 검사는 수락 시점에만
  하고 상태는 `PENDING`으로 남는다(만료 배치가 없다). `console/format.ts`의
  `describeInvitation()`이 `pendingInvitationExpiresAt`을 현재와 비교해
  "유효/만료됨/없음"을 만든다.
- **초대 링크 형식은 `format.ts`의 `invitationUrlFor()`가 유일한 출처다.** 최초 발급과
  재발송이 같은 링크를 만들어야 하고, `/accept-invitation` 경로가 바뀔 때 두 곳이 갈리면
  한쪽 링크가 조용히 죽는다. 1회 노출 UI는 `InvitationReveal` 컴포넌트를 공유한다.
- **재발송은 이전 링크를 죽인다** — 성공 문구에 그 사실을 적는다(서버가 기존 초대를
  `REVOKED`로 만들기 때문이다).
- **초대 취소는 계정을 남긴다**(종료와 분리) — 확인 문구에 "계정은 남습니다"를 적어
  종료와 헷갈리지 않게 한다.

## 현재 상태와 다음

- **된 것**: 스캐폴딩, 타입 생성, API 클라이언트, 상태별 화면, 3초 폴링, DEV 결제 생성 버튼. UI는 Tailwind v4 + shadcn/ui이고 화면 컴포넌트는 `checkout/components/`에 있다. 지갑 연결과 ERC-20 `transfer`는 wagmi + viem으로 **코드가 들어갔다**.
- **상태 관리 라이브러리(Zustand 등)는 넣지 않았다.** 서버 상태는 react-query가, 지갑 상태는 wagmi 훅이 갖고, 나머지는 한 컴포넌트 안의 `useState`로 끝난다 — 지금 컴포넌트 사이에서 공유해야 할 클라이언트 상태가 없다. 생기면 그때 넣는다.
- **아직 실물로 확인 못 한 것 둘**:
  - **지갑 흐름 전체.** MetaMask 확장과 Base Sepolia 테스트넷 USDC가 있어야 한다. 타입 검사·가드 테스트·번들까지는 확인했지만 **연결·서명·전송을 실제로 돌려본 적이 없다.** 백엔드와 같은 규율로 실물 수동 검증이 최종 확인이다.
  - **Tailwind 전환 후의 화면.** 빌드·테스트·클래스 생성까지는 확인했지만 브라우저에서 렌더된 모습은 보지 못했다(작업 당시 브라우저 자동화 확장 미연결). `npm run dev`로 각 상태 화면을 한 번 훑는 것이 좋다.
- 명령어는 위 "실행" 절 참고. 프로젝트가 더 생기면 이 문서에 앱별 절을 나눈다.

## 앱: admin (내부 운영자 콘솔) — merchant와 다른 점만

`frontend/admin`은 `frontend/merchant`를 그대로 미러링했다(같은 스택·같은 세션 쿠키
클라이언트 구조·같은 라우터). 계약 기준 문서는
[`docs/architecture/admin-console-api.md`](../docs/architecture/admin-console-api.md)다.

```
cd frontend/admin
npm install
npm run dev            # http://localhost:5175  (payment 5173, merchant 5174와 겹치지 않게)
npm test / npm run build / npm run lint
npm run gen:api        # api-admin의 openapi3.yaml → src/api/schema.d.ts
```

- **로그인에 `merchantCode`가 없다** — 내부 운영자는 특정 가맹점에 속하지 않는다.
- **`VIEWER`에게는 가맹점 등록 폼을 아예 그리지 않는다**(`canRegisterMerchant`). 서버도
  403으로 막지만(SecurityConfig의 메서드 스코핑) 누를 수 있게 두고 거부하는 것보다 낫다.
  `GET`은 VIEWER도 허용되므로 목록은 보인다.

### 초대 링크가 **다른 콘솔**을 가리킨다 — 이 앱에서 가장 틀리기 쉬운 지점

가맹점을 등록하면 최초 OWNER의 초대 Token이 나오는데, 그 사람이 활성화할 곳은 이 콘솔이
아니라 **가맹점 콘솔**(`frontend/merchant`)의 `/accept-invitation`이다. 그래서 merchant
앱처럼 `window.location.origin`을 쓸 수 없다:

- `console/format.ts`의 **`merchantInvitationUrlFor()`**가 `VITE_MERCHANT_CONSOLE_URL`
  (기본 `http://localhost:5174`)로 링크를 만든다. 운영에서 콘솔 도메인이 갈리면 이 값만 바꾼다.
- 자기 origin을 쓰면 **상대가 열 수 없는 링크**가 되는데 화면상으로는 멀쩡해 보인다 —
  `console.test.tsx`가 "링크가 5174를 가리키고 현재 origin을 포함하지 않는다"를 회귀로 지킨다.
- 등록 성공 문구에도 "가맹점 콘솔 링크"임을 적어 운영자가 착각하지 않게 한다.

### admin 2차 — 라우트가 늘고 초대 링크가 두 종류가 됐다

| 경로 | 인증 | 내용 |
|---|---|---|
| `/accept-invitation?token=…` | **비인증(공개)** | 내부 직원 초대 수락 |
| `/` | 필요 | 가맹점 목록·등록 |
| `/internal-users` | 필요(**SUPER_ADMIN**) | 내부 직원 명부·발급 |

- **`/accept-invitation`을 인증 게이트보다 먼저 매칭시킨다**(merchant와 같은 구조·같은
  이유). `routing.test.tsx`가 "미인증에서 로그인으로 튕기지 않는다"를 회귀로 지킨다.
- **`/internal-users`는 라우트와 내비 둘 다 SUPER_ADMIN에게만 노출한다**(`canManageInternalUsers`).
  서버도 `GET`/`POST` 모두 403으로 막는다.
- **초대 링크가 두 종류이고 가리키는 콘솔이 다르다** — 이 앱에서 가장 틀리기 쉬운 지점이다:
  - 가맹점 등록 → **가맹점 콘솔**(`merchantInvitationUrlFor`, `VITE_MERCHANT_CONSOLE_URL`)
  - 내부 직원 발급 → **이 콘솔 자신**(`internalInvitationUrlFor`, `window.location.origin`)

  그래서 `InvitationReveal`은 **링크 생성 함수를 주입받는다** — 컴포넌트가 스스로 정하면
  호출부에서 어느 쪽인지 보이지 않아 바꿔 쓰기 쉬워진다. 테스트가 "둘이 서로 다른 origin을
  가리킨다"를 고정한다.
- **발급 폼의 역할 선택지에 `SUPER_ADMIN`이 없다**(`ISSUABLE_INTERNAL_ROLES`) — 최초
  SUPER_ADMIN은 Bootstrap으로만 만든다는 규정 때문이다. **도메인(`InternalUser.invite`)도
  같은 제약을 `require`로 갖는다**(merchant의 `OWNER` 승격과 같은 방식) — 화면의 선택지
  제한은 UX일 뿐이고 실제 방어선은 서버다(직접 호출하면 400).

### admin 6차 — 가맹점 로그인 감사 로그

`/merchant-login-audit`(**SUPER_ADMIN/OPERATOR** 라우트·내비, `canManageMerchantAccounts` 게이트
— 내부 로그인 감사가 `canManageInternalUsers`(SUPER_ADMIN)인 것과 다른 게이트)에서 **전 가맹점**의
관리자 로그인 시도를 최신순으로 본다. 5차(내부 로그인 감사)와 표 구조는 같되 가맹점 열(이름 +
코드)이 더 있고, **없는 merchantCode 시도**는 "알 수 없는 가맹점"으로 구분한다. **기록은 이 콘솔이
아니라 api-merchant의 로그인이** 남긴다(조회만 admin) — 감사 인프라를 두 앱에 나눈 첫 사례다.
백엔드 계약은 `docs/architecture/admin-console-api.md` 4절.

### admin 5차 — 로그인 감사 로그

`/login-audit`(SUPER_ADMIN 전용 라우트·내비, `canManageInternalUsers` 재사용)에서 내부 운영자
로그인 시도를 최신순으로 본다 — 성공·실패·잠김 배지, 로그인 아이디, 이름, 클라이언트 IP.
**없는 계정을 노린 시도**는 `userName`이 `null`이라 "알 수 없는 계정"으로 구분해 보여준다
(존재하지 않는 계정 probing을 눈에 띄게). 조회 전용 페이지라 액션·폼이 없다 —
`InternalUsersPage`와 같은 로딩/오류/목록 분기 구조다. 백엔드 계약은
`docs/architecture/admin-console-api.md` 4절.

### admin 4차 — 가맹점 상세와 그 가맹점 사용자 계정 관리

가맹점 목록 행(이름)을 누르면 상세(`/merchants/:merchantId`)로 가고, 거기서 그 가맹점의
사용자 명부와 계정 관리(정지·재개·종료·역할 변경)를 한다. 내부 직원판(3차)의 가맹점판
미러링이다.

- **관리 액션은 SUPER_ADMIN/OPERATOR에게만 그린다**(`canManageMerchantAccounts`) — VIEWER는
  명부만. 서버도 `POST /admin/merchants/**`를 그 역할로 좁힌다(조회 GET은 전원). `MeResponse`의
  역할로 판단해 `AdminMerchantUserTable`에 `canManage`로 넘긴다.
- **가맹점 헤더 정보(이름·코드)는 별도 단건 조회 없이 캐시된 목록(`useMerchants`)에서 찾는다** —
  깊은 링크로 캐시에 없으면 식별자만 보여준다. 백엔드에 "가맹점 단건 조회" 엔드포인트를
  만들지 않기 위한 선택이다.
- **자기 자신 개념이 없다**(내부 직원판과 다른 점) — 내부 운영자는 가맹점 사용자가 될 수
  없어서다. OWNER 행은 역할 변경을 감추고(가맹점 콘솔과 같은 판단), 역할 선택지는
  ADMIN/VIEWER(`INVITABLE_MERCHANT_ROLES`, OWNER 승격은 서버가 400). 초대 재발송·취소는 없다.
- 오류는 status로 분기한다(409는 마지막 OWNER 보호·허용되지 않는 전이 — 서버 메시지 그대로).

### admin 3차 — 내부 직원 명부의 행 액션(정지·재개·종료·역할 변경)

`InternalUserActions`가 **계정 상태에 따라 다른 액션**을 그린다 — merchant의
`MerchantUserActions`를 그대로 미러링했다: `ACTIVE`→정지·종료·역할 변경,
`SUSPENDED`→재개·종료, `INVITED`→종료만, `TERMINATED`/`LOCKED`→없음.

- **merchant와 다른 점은 초대 재발송·취소가 없다는 것뿐이다** — 그 흐름은 아직 내부
  운영자 API에 없다(가맹점 콘솔에만 있다). 그래서 `INVITED` 행은 종료만 할 수 있다.
- **`SUPER_ADMIN` 행에는 역할 변경을 두지 않는다**(merchant가 `OWNER` 행에 그런 것과
  같은 판단) — 승격은 불가능하고(선택지가 `ISSUABLE_INTERNAL_ROLES` = OPERATOR/VIEWER),
  강등은 마지막 SUPER_ADMIN 보호에 걸리기 쉬워 화면에서 미리 뺐다.
- **자기 자신 행에는 액션 대신 "본인"을 그린다.** 현재 사용자는 `InternalUsersPage`가
  `useMe()`에서 받아 `InternalUserTable`에 넘긴다(캐시된 쿼리라 추가 요청이 없다). 서버도
  자기 자신 대상 요청을 403으로 막는다.
- **오류는 status로 분기한다**(`AdminApiError`): 409(마지막 SUPER_ADMIN 보호·허용되지 않는
  전이)는 **서버 메시지를 그대로** 보여주고, 403은 권한, 404는 없는 계정으로 옮긴다 —
  merchant와 같은 방식이다.
- 이 페이지 전체가 여전히 SUPER_ADMIN 전용이라(라우트·내비·서버 3중), 관리 액션도 그
  안에서만 노출된다. 계약은 `docs/architecture/admin-console-api.md`의 4절에 있다.
