# 계정·권한·API Key 설계

## 1. 목적

본 문서는 다음 인증 주체와 자격증명을 분리하여 정의한다.

- PG 내부 운영자 계정
- 가맹점 관리자 계정
- 가맹점 서버의 결제 API 연동용 API Key

로그인 계정과 API Key는 사용 주체와 생명주기가 다르므로 별도 도메인으로 관리한다.

```text
사람의 관리자 화면 접근
→ 로그인 계정과 세션 인증

가맹점 서버의 결제 API 호출
→ MerchantApiKey 인증
```

## 2. 도메인 경계

```text
Identity & Access
├── InternalUser
├── MerchantUser
└── AccountInvitation

Developer Integration
└── MerchantApiKey
```

가맹점과의 관계:

```text
Merchant
├── MerchantUser
└── MerchantApiKey
```

## 3. 내부 운영자 계정

### 3.1 정의

`InternalUser`는 PG 내부 관리자 화면을 사용하는 계정이다.

### 3.2 MVP 역할

| 역할 | 설명 |
|---|---|
| `SUPER_ADMIN` | 내부 계정 발급, 권한 부여, 전체 관리 |
| `OPERATOR` | 가맹점·결제·운영 업무 |
| `VIEWER` | 조회 전용 |

### 3.3 발급 정책

```text
최초 SUPER_ADMIN
→ 내부 운영자 초대 또는 계정 발급
→ 역할 부여
```

- 내부 운영자 계정은 `SUPER_ADMIN`만 발급할 수 있다.
- 일반 회원가입은 제공하지 않는다.
- 최초 `SUPER_ADMIN`은 배포 초기화 명령, 안전한 운영 절차 또는 별도 Bootstrap 과정으로 생성한다.
- 최초 관리자 비밀번호를 DDL이나 Git 저장소에 평문으로 저장하지 않는다.

### 3.4 로그인 경로

권장 경로:

```text
/admin/login
```

가맹점 관리자 로그인과 세션 영역을 분리한다.

## 4. 가맹점 사용자 계정

### 4.1 정의

`MerchantUser`는 특정 가맹점의 관리자 화면을 사용하는 계정이다.

### 4.2 MVP 역할

| 역할 | 설명 |
|---|---|
| `OWNER` | 가맹점 최고 관리자, 하위 계정과 API Key 관리 |
| `ADMIN` | 가맹점 운영 관리, 하위 계정과 API Key 관리 |
| `VIEWER` | 조회 전용 |

향후 필요 시 다음 역할을 추가한다.

- `DEVELOPER`
- `SETTLEMENT_MANAGER`

### 4.3 가맹점 등록과 OWNER 생성

권장 흐름:

```text
내부 운영자
→ Merchant 등록
→ Merchant OWNER 초대 계정 생성
→ OWNER가 초대 링크에서 비밀번호 설정
→ 계정 ACTIVE
```

정책:

- 가맹점 등록 트랜잭션에서 `Merchant`와 최초 `MerchantUser(OWNER)`를 함께 생성한다.
- 초기 계정을 단순한 `기본 계정`이 아니라 가맹점의 `OWNER` 계정으로 정의한다.
- 내부 운영자가 OWNER의 최종 비밀번호를 직접 정하지 않는다.
- MVP에서 초대 메일이 부담되면 임시 비밀번호 방식을 사용할 수 있지만, 최초 로그인 시 비밀번호 변경을 강제한다.
- OWNER는 같은 가맹점의 하위 사용자만 발급할 수 있다.
- 다른 가맹점의 사용자나 내부 운영자 계정을 발급할 수 없다.

### 4.4 하위 계정 발급

```text
Merchant OWNER
├── ADMIN 발급
└── VIEWER 발급
```

MVP 정책:

- `OWNER`, `ADMIN`은 하위 계정을 발급할 수 있다.
- `ADMIN`은 `OWNER`를 새로 발급하거나 기존 OWNER의 권한을 변경할 수 없다.
- `VIEWER`는 계정과 API Key를 관리할 수 없다.
- 가맹점에는 최소 하나의 활성 OWNER가 항상 존재해야 한다.

### 4.5 로그인 경로

권장 경로:

```text
/merchant/login
```

## 5. 계정 상태

내부 운영자와 가맹점 사용자는 다음 상태를 공통으로 사용할 수 있다.

| 상태 | 설명 |
|---|---|
| `INVITED` | 초대되었으나 비밀번호 설정 전 |
| `ACTIVE` | 정상 로그인 가능 |
| `LOCKED` | 로그인 실패 등으로 일시 잠김 |
| `SUSPENDED` | 운영 정책에 의해 사용 중지 |
| `TERMINATED` | 계정 종료 |

정상 흐름:

```text
INVITED
→ ACTIVE
```

보안 흐름:

```text
ACTIVE
→ LOCKED
→ ACTIVE
```

운영 흐름:

```text
ACTIVE
→ SUSPENDED
→ ACTIVE
```

종료:

```text
ACTIVE 또는 SUSPENDED
→ TERMINATED
```

`TERMINATED`는 종료 상태이며 재활성화하지 않는다.

## 6. 가맹점 결제 연동용 API Key

### 6.1 정의

`MerchantApiKey`는 가맹점 서버가 스테이블코인 결제 시스템의 API를 호출할 때 사용하는 서버 간 인증 자격증명이다.

대표 사용 API:

```text
POST /api/v1/payments
GET  /api/v1/payments/{paymentId}
```

향후:

```text
POST /api/v1/refunds
GET  /api/v1/settlements
```

### 6.2 소유권

```text
소유 주체
= Merchant

관리 주체
= MerchantUser

사용 주체
= 가맹점 서버
```

API Key를 특정 직원 계정의 소유로 만들지 않는다.

발급자와 폐기자는 감사 정보로 기록한다.

### 6.3 인증 방식

MVP 권장:

```http
Authorization: Bearer sk_test_<prefix>_<secret>
```

현재는 Base Sepolia만 지원하므로 `TEST` Key만 발급한다.

향후 Mainnet 지원 시:

```text
sk_test_...
sk_live_...
```

를 분리한다.

API Key 환경과 결제 네트워크 환경은 반드시 일치해야 한다.

### 6.4 저장 정책

API Key 원문은 생성 시 최초 한 번만 표시한다.

DB 저장 값:

- `key_prefix`: 식별 및 화면 표시
- `secret_hash`: 전체 API Key 검증용 해시
- `hash_algorithm`: 해시 방식 버전 관리

저장 금지:

- 전체 API Key 원문
- Secret 평문
- 로그의 Authorization Header 원문

권장 인증 흐름:

```text
요청 API Key 수신
→ Prefix 추출
→ Prefix로 후보 Key 조회
→ 전체 Key를 서버 측 Pepper와 함께 해시
→ secret_hash 비교
→ 상태·환경·Merchant 상태 확인
→ last_used_at 갱신
```

API Key는 요청마다 검증하므로 비밀번호용 느린 해시와 동일하게 처리할 필요는 없다. SHA-256 또는 HMAC-SHA-256 계열을 사용할 수 있으며 서버 측 비밀값을 함께 사용한다.

### 6.5 API Key 상태

| 상태 | 설명 |
|---|---|
| `ACTIVE` | API 호출 가능 |
| `REVOKED` | 관리자에 의해 즉시 폐기 |
| `EXPIRED` | 만료 시각 경과 |

정상 흐름:

```text
ACTIVE
→ REVOKED
```

또는:

```text
ACTIVE
→ EXPIRED
```

폐기된 API Key는 다시 활성화하지 않는다. 새로운 Key를 발급한다.

### 6.6 발급 권한

MVP:

| 역할 | 발급 | 폐기 | 목록 조회 |
|---|---:|---:|---:|
| `OWNER` | 가능 | 가능 | 가능 |
| `ADMIN` | 가능 | 가능 | 가능 |
| `VIEWER` | 불가 | 불가 | 제한적 또는 불가 |

### 6.7 복수 Key

가맹점당 API Key를 하나로 제한하지 않는다.

복수 Key 사용 예:

- 개발 서버
- 운영 서버
- 배치 서버
- 무중단 Key Rotation
- 장애 복구용 신규 Key

### 6.8 Scope

MVP Scope:

- `PAYMENT_CREATE`
- `PAYMENT_READ`

향후:

- `REFUND_CREATE`
- `REFUND_READ`
- `SETTLEMENT_READ`

현재 DB에서는 확장 가능한 별도 `merchant_api_key_scope` 테이블을 사용한다.

## 7. 멱등성과 유일성

계정:

```text
internal_user.login_id
internal_user.email

merchant_seq + merchant_user.login_id
merchant_seq + merchant_user.email
```

API Key:

```text
merchant_api_key_id
key_prefix
```

`key_prefix`는 전체 시스템에서 유일하게 생성한다.

## 8. 감사 정보

최소 저장 항목:

- 내부 운영자 생성자
- 가맹점 OWNER를 생성한 내부 운영자
- 하위 계정을 생성한 MerchantUser
- API Key 발급자
- API Key 폐기자
- 마지막 로그인 일시
- 마지막 API Key 사용 일시

향후 별도 감사 로그로 확장한다.

## 9. MVP와 후속 범위

MVP 포함:

- 최초 SUPER_ADMIN Bootstrap
- 내부 운영자 로그인
- SUPER_ADMIN의 내부 계정 발급
- 가맹점 등록 시 OWNER 계정 생성
- OWNER/ADMIN의 하위 계정 발급
- 가맹점 사용자 로그인
- TEST API Key 복수 발급
- API Key 최초 1회 표시
- API Key 해시 저장
- API Key 폐기
- `PAYMENT_CREATE`, `PAYMENT_READ` Scope
- API Key 기반 결제 API 인증

후속:

- OTP 또는 MFA
- SSO
- 비밀번호 만료
- 로그인 IP 정책
- API Key IP Allowlist
- API 요청 서명(HMAC Signature)
- Key 자동 Rotation
- 계정 승인 워크플로
- 세분화된 RBAC
- 로그인·API 감사 조회
