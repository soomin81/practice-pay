# MetaMask로 Base Sepolia 테스트넷 USDC 준비하고 결제 흘려보기

이 문서는 **설계 기준이 아니라 실행 절차서**다(`docs/`의 다른 문서와 성격이 다르다). 이 프로젝트의 결제 흐름을 **실물로 한 번 끝까지 흘려보기** 위해 필요한 지갑·토큰 준비와 확인 방법을 정리한다.

왜 필요한가: 지갑 연결과 온체인 전송은 **자동 테스트가 불가능하다**(MetaMask 확장이 필요하다). 그래서 이 프로젝트는 백엔드·프론트 모두 **실물 수동 검증**을 최종 확인으로 삼는다. 이 문서가 그 절차다.

---

## 0. 시작하기 전에 — 안전 수칙

> [!WARNING]
> **실제 자산이 든 지갑을 쓰지 않는다.** 이 프로젝트 테스트용으로 **새 MetaMask 계정을 하나 만들어서** 그것만 쓴다. 개발 중에는 주소·개인키를 로그나 스크린샷에 흘리기 쉽다.

- **시드 구문(Secret Recovery Phrase)은 누구에게도, 어떤 도구에도 입력하지 않는다.** 코딩 에이전트(Claude Code 포함)에게 붙여넣지 않는다 — 이 문서의 어떤 절차도 시드 구문을 필요로 하지 않는다.
- **테스트넷 토큰은 금전적 가치가 없다.** 테스트넷 ETH·USDC를 판다는 제안은 전부 사기다. Faucet은 무료다.
- **테스트넷과 메인넷을 헷갈리지 않는다.** MetaMask 상단의 네트워크 이름이 항상 `Base Sepolia`인지 확인한다.

---

## 1. 준비물

| 항목 | 용도 | 어디서 |
|---|---|---|
| MetaMask 브라우저 확장 | 지갑 연결·서명 | [metamask.io](https://metamask.io) |
| Base Sepolia 네트워크 | 결제가 일어나는 체인 | 아래 2절에서 추가 |
| Base Sepolia ETH | **가스비** (USDC 전송에도 가스가 든다) | 아래 3절 Faucet |
| Base Sepolia USDC | 실제로 보낼 금액 | 아래 4절 Circle Faucet |
| 계정 **2개** | 보내는 쪽 / 받는 쪽 | MetaMask에서 계정 추가 |

**계정이 왜 2개인가**: 이 시스템은 고객이 **PG의 수취 지갑**으로 USDC를 보내는 구조다(가맹점 지갑이 아니다 — `docs/architecture/mvp-scope.md`의 "수취 지갑 귀속"). 혼자 테스트하려면 보내는 계정과 받는 계정이 따로 있어야 흐름이 실제와 같아진다. 5절에서 수취 주소를 직접 넣는다.

---

## 2. Base Sepolia 네트워크 추가

MetaMask → 네트워크 선택 → **네트워크 추가** → 수동 추가에 아래 값을 넣는다.

| 항목 | 값 |
|---|---|
| 네트워크 이름 | `Base Sepolia` |
| RPC URL | `https://sepolia.base.org` |
| 체인 ID | `84532` |
| 통화 기호 | `ETH` |
| 블록 탐색기 | `https://sepolia.basescan.org` |

**체인 ID `84532`는 이 프로젝트가 쓰는 값과 같아야 한다** — 백엔드가 `BASE_SEPOLIA`로 내려주고, 프론트는 그 값을 응답에서 받아 쓴다(상수로 박지 않는다). 지갑이 다른 네트워크에 있으면 결제 화면이 전환을 요청한다.

> MetaMask의 "인기 네트워크" 목록에서 Base Sepolia를 바로 추가할 수도 있다. 그 경우에도 체인 ID가 `84532`인지 확인한다.

---

## 3. 가스용 테스트 ETH 받기

**USDC를 보내는 데도 가스비(ETH)가 필요하다.** USDC만 받아 두면 전송이 실패한다.

아래 중 하나에서 받는다(대부분 24시간에 한 번, 0.05~0.5 ETH):

- [Coinbase Developer Platform Faucet](https://portal.cdp.coinbase.com/products/faucet) — 24시간마다 0.1 ETH
- [Alchemy Base Sepolia Faucet](https://www.alchemy.com/faucets/base-sepolia)
- [Chainstack Base Faucet](https://faucet.chainstack.com/base-testnet-faucet)
- [thirdweb Faucet](https://thirdweb.com/base-sepolia-testnet)
- 전체 목록: [Base 공식 문서 — Network Faucets](https://docs.base.org/base-chain/network-information/network-faucets)

가스비는 매우 저렴하다(보통 0.01~0.5 gwei). 0.01 ETH면 수백 번 전송할 수 있다.

**확인**: MetaMask에서 Base Sepolia를 선택했을 때 잔액에 ETH가 보이면 된다.

---

## 4. 테스트 USDC 받기

[**Circle Testnet Faucet**](https://faucet.circle.com/)에서 받는다. 계정 가입이 필요 없다.

1. 네트워크에서 **Base Sepolia** 선택
2. 받을 주소(보내는 쪽 계정 주소) 입력
3. **USDC** 선택 후 요청

**주소당·체인당 2시간에 20 USDC**까지 받을 수 있다.

**얼마가 필요한지 정확히 계산해 두면 기다리는 시간을 아낄 수 있다.** 이 프로젝트의 환율은 고정값이다(MVP는 Fake Exchange를 쓴다 — ADR-004):

| 항목 | 값 | 출처 |
|---|---|---|
| 시장 환율 | `1400.000000000000` KRW/USDC | `FakeExchangeRateProvider.FIXED_RATE` |
| 스프레드 | `0.005` (0.5%) | `CreatePaymentUseCase.SPREAD_RATE` |
| **적용 환율** | **1393 KRW/USDC** | `1400 × (1 − 0.005)` |

DEV 버튼의 기본 주문이 **50,000원**이므로 필요한 금액은 **35.893755 USDC**다. Faucet 한도가 20 USDC라 **두 번(2시간 간격) 받아야 한 건을 결제할 수 있다.**

> **한 번의 Faucet 요청으로 끝내려면** `frontend/payment/src/dev/DevPaymentCreator.tsx`의 `orderAmount`를 `20000` 이하로 낮춘다(20,000원 ≈ 14.36 USDC). 테스트를 여러 번 돌릴 거라면 이쪽을 권한다.

---

## 5. MetaMask에 USDC 토큰 표시하기

Faucet에서 받아도 **MetaMask가 토큰을 자동으로 보여주지 않을 수 있다.** 직접 추가한다.

MetaMask → 토큰 → **토큰 가져오기** → 사용자 정의 토큰:

| 항목 | 값 |
|---|---|
| 토큰 Contract 주소 | `0x036CbD53842c5426634e7929541eC2318f3dCF7e` |
| 토큰 기호 | `USDC` |
| 소수 자릿수 | `6` |

> [!IMPORTANT]
> **이 주소는 "토큰 Contract"이지 "받는 사람 주소"가 아니다.** 이 프로젝트는 토큰을 Symbol이 아니라 **(네트워크, Contract 주소) 조합**으로 판별한다 — 이름이 `USDC`인 가짜 토큰이 얼마든지 있을 수 있기 때문이다. 백엔드의 허용 Contract 목록(`PaymentNetworkConfig`)에 있는 값이 바로 이것이고, 출처는 Circle 공식 문서다.
>
> **이 주소로 USDC를 전송하면 안 된다.** 토큰 Contract 자신에게 보낸 토큰은 되찾을 수 없다. 5절 아래의 "수취 지갑" 항목을 반드시 읽는다.

---

## 6. 백엔드와 프론트엔드 띄우기

`README.md`의 "시작하기"를 이미 한 번 했다고 가정한다(MySQL 기동 + Flyway + 시드).

**터미널 3개가 필요하다.**

```bash
# 1) 결제 API (8081)
cd backend
gradlew.bat :apps:api-payment:bootRun

# 2) Confirm Worker — 이게 없으면 결제가 CONFIRMING에서 영원히 멈춘다
cd backend
gradlew.bat :apps:batch:bootRun

# 3) 프론트엔드
cd frontend/payment
npm run dev            # http://localhost:5173
```

`batch` 앱이 **10초마다** 온체인 상태를 폴링해서 Confirmation을 세고, `Payment`를 `SUCCEEDED`로 확정한 뒤 Fake Exchange 매도와 정산채권 생성까지 이어간다. **이 앱을 안 띄우면 화면이 "결제 확인 중"에서 멈춘다.**

DEV 버튼을 쓰려면 API Key가 필요하다:

```bash
cd frontend/payment
cp .env.example .env.local
```

`.env.local`에는 **값 두 개**가 필요하다. API Key는 시드에 들어 있는 개발용 키가 이미 적혀 있고, **수취 지갑은 비어 있으니 직접 채워야 한다**(다음 절).

---

## 7. 수취 지갑 지정하기

USDC를 **받을** 주소를 정해야 한다. 여기서는 **여러분이 PG 역할을 대신하는 것**이다 — 원래 이 주소는 PG가 수탁하는 지갑이지 가맹점이나 고객의 지갑이 아니다. MetaMask에서 **두 번째 계정**을 만들고(계정 메뉴 → 계정 추가) 그 주소를 복사한다.

`.env.local`에 넣는다:

```bash
VITE_DEV_RECEIVING_WALLET=0x여기에_본인의_두_번째_계정_주소
```

**이 값이 비어 있으면 "테스트 결제 생성" 버튼이 비활성화된다.** 기본값을 두지 않는 것이 의도다 — 이 주소로 실제 테스트넷 USDC가 전송되므로, 되찾을 수 없는 주소가 기본값으로 박혀 있으면 안 된다.

> 지금 이 값을 프론트가 결제 생성 요청에 실어 보낼 수 있는 것은 **API가 아직 수취 지갑을 서버에서 주입하지 않기 때문**이다(예정된 gap — `docs/architecture/mvp-scope.md`의 "수취 지갑 귀속"). 그 gap이 닫히면 이 환경변수는 사라진다.

> [!CAUTION]
> **토큰 Contract 주소(`0x036CbD…CF7e`)를 여기에 넣지 않는다.** 그건 "USDC라는 토큰"의 주소이지 사람의 지갑이 아니다. 그리로 보낸 토큰은 되찾을 수 없다.
>
> 실제로 이 코드에 한동안 그 주소가 `receivingWallet` 기본값으로 하드코딩돼 있었다(복붙 사고). 지금은 환경변수로 빠졌고 기본값이 없다.

IntelliJ HTTP Client(`backend/apps/api-payment/requests.http`)로 테스트할 때도 같은 규칙이다 — 그 파일의 `receivingWallet`은 자리표시자(`0xaaaa…`)이므로, 실제로 전송까지 해볼 거라면 본인 주소로 바꾼다.

---

## 8. 결제 흘려보기

1. `http://localhost:5173` 접속
2. 상단 **DEV** 영역의 **"테스트 결제 생성"** 클릭 → 주소에 `?session=cs_...`가 붙고 결제 화면이 뜬다
3. 화면에 나온 값을 확인한다:
   - **보낼 금액** (기본 주문 50,000원이면 `35.893755 USDC`)
   - **네트워크** `BASE_SEPOLIA (chainId 84532)`
   - **수취 지갑** — 7절에서 넣은 본인 계정 주소인지 확인 (마우스를 올리면 전체 주소가 보인다)
   - **토큰 Contract** — `0x036C…CF7e`
4. **"지갑 연결"** → MetaMask 창에서 승인. **보내는 쪽 계정**을 선택한다.
5. **"N USDC 보내기"** → MetaMask가 전송 승인을 요청한다.
   - 다른 네트워크에 있었다면 먼저 **네트워크 전환**을 요청한다. 승인한다.
   - 승인 창에서 **받는 주소와 금액을 다시 한번 눈으로 대조한다.**
6. 서명하면 화면이 **"결제 확인 중"**으로 바뀌고 Confirmation이 올라간다.

### 얼마나 기다리나

**12 Confirmation이 필요하다**(`PaymentNetworkConfig.REQUIRED_CONFIRMATION_COUNT`). Base의 블록 시간이 약 2초이고 Worker가 10초마다 폴링하므로 **대략 30초~1분**이면 완료된다.

---

## 9. 완료 확인

화면에 **"결제가 완료되었습니다"**가 뜨고 가맹점 URL(`https://merchant.example.com/done`)로 이동하는 버튼이 나오면 성공이다. 그 URL은 실제로 존재하지 않으므로 눌러도 404가 나는 것이 정상이다.

**MVP의 종착점은 화면이 아니라 데이터다.** DB에서 확인한다:

```bash
docker exec -i backend-mysql-1 mysql -uroot -pverysecret stablecoin_payment -e "
SELECT payment_id, payment_status FROM payment ORDER BY created_at DESC LIMIT 1;
SELECT exchange_order_id, exchange_order_status FROM exchange_order ORDER BY created_at DESC LIMIT 1;
SELECT settlement_receivable_id, receivable_status FROM settlement_receivable ORDER BY created_at DESC LIMIT 1;
"
```

셋이 이렇게 나오면 **MVP 흐름이 끝까지 통과한 것이다**:

| 테이블 | 컬럼 | 기대 상태 |
|---|---|---|
| `payment` | `payment_status` | `SUCCEEDED` |
| `exchange_order` | `exchange_order_status` | `COMPLETED` |
| `settlement_receivable` | `receivable_status` | `READY` |

온체인 쪽은 [sepolia.basescan.org](https://sepolia.basescan.org)에서 Transaction Hash로 직접 확인할 수 있다(화면의 "트랜잭션" 항목에 마우스를 올리면 전체 Hash가 보인다).

---

## 10. 문제 해결

| 증상 | 원인과 조치 |
|---|---|
| **"지갑을 찾을 수 없습니다"** | MetaMask 확장이 없거나 꺼져 있다. 설치 후 **페이지 새로고침**(확장은 로드 시점에 주입된다) |
| **가스비 부족으로 전송 실패** | USDC만 있고 ETH가 없다. 3절 Faucet에서 ETH를 받는다 |
| **"결제 서버에 연결하지 못했습니다"** | `api-payment`(8081)가 안 떠 있거나 CORS. 프론트가 반드시 **5173** 포트여야 한다(백엔드 허용 목록에 그 포트만 있다). `strictPort`가 켜져 있어 포트가 밀리면 기동 자체가 실패하므로, 뜬 경우엔 포트 문제는 아니다 |
| **"결제 확인 중"에서 멈춤** | `batch` 앱이 안 떠 있다. 6절의 2번 터미널 확인 |
| **5분 뒤 "확인이 예상보다 오래 걸립니다"** | 폴링 상한(5분)에 걸린 것이다. 화면만 멈춘 것이고 백엔드는 계속 확인 중이니 새로고침한다 |
| **지갑 연결에서 409** | 이미 지갑이 연결된 세션이다. 정상 처리되므로 무시하고 진행된다(계약상 재연결은 지원하지 않는다) |
| **USDC가 MetaMask에 안 보임** | 5절의 토큰 가져오기를 안 했다. Faucet 트랜잭션 자체는 탐색기에서 확인할 수 있다 |
| **결제가 실패로 끝남** | 수취 지갑·Contract·금액·네트워크 중 하나가 어긋났다. 백엔드 로그의 실패 사유 코드를 본다(화면에는 코드를 그대로 노출하지 않는다) |

---

## 11. 남은 개선

- **DEV 주문 금액이 코드에 고정돼 있다**(50,000원 = 35.893755 USDC). Faucet 한도가 2시간에 20 USDC라 한 건 테스트에 두 번 요청이 필요하다. 수취 지갑처럼 `.env.local`로 빼면 매번 코드를 고치지 않아도 된다.

---

## 참고

- [Circle Testnet Faucet](https://faucet.circle.com/)
- [Base 공식 문서 — Network Faucets](https://docs.base.org/base-chain/network-information/network-faucets)
- [Base 공식 문서 — Connecting to Base](https://docs.base.org/base-chain/quickstart/connecting-to-base)
- [Base Sepolia 블록 탐색기](https://sepolia.basescan.org)
- 프로젝트 내부: [Hosted Checkout API 계약](../architecture/checkout-api.md) · [상태 전이 정책](../domain/state-transitions.md) · [MVP 범위](../architecture/mvp-scope.md)
