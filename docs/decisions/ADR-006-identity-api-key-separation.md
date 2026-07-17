# ADR-006: 관리자 계정과 가맹점 API Key를 분리한다

- 상태: 승인

## 배경

내부 운영자와 가맹점 관리자는 사람이 관리자 화면에 로그인하기 위한 계정이다.

반면 가맹점 API Key는 가맹점 서버가 결제 API를 호출하기 위한 서버 간 인증 수단이다. 두 자격증명을 하나의 사용자 계정이나 동일한 생명주기로 관리하면 직원 변경, Key Rotation, 권한 분리 및 감사 추적이 어려워진다.

## 결정

다음 도메인을 분리한다.

```text
InternalUser
MerchantUser
MerchantApiKey
```

발급 구조:

```text
최초 SUPER_ADMIN
→ 내부 운영자 발급

내부 운영자
→ Merchant 등록
→ 최초 Merchant OWNER 생성

Merchant OWNER
→ 같은 가맹점의 하위 사용자 발급
→ MerchantApiKey 발급·폐기
```

`MerchantApiKey`의 소유자는 Merchant다. 발급자와 폐기자는 감사 정보로만 기록한다.

API Key 원문은 최초 발급 시 한 번만 노출하고 DB에는 Prefix와 Hash만 저장한다.

## 결과

사람의 로그인 인증과 서버 간 API 인증의 보안 정책을 독립적으로 발전시킬 수 있다.

직원이 퇴사하거나 계정이 비활성화되어도 가맹점 시스템 Key의 소유권은 Merchant에 남는다.

향후 MFA, 세분화된 RBAC, API Key Rotation, HMAC 요청 서명 및 IP Allowlist를 독립적으로 추가할 수 있다.
