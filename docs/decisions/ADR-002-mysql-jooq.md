# ADR-002: MySQL과 jOOQ를 사용한다

- 상태: 승인

## 결정

Database는 MySQL 8.x, DB 접근은 jOOQ를 사용한다. JPA와 Hibernate는 사용하지 않는다. 생성 Record는 Persistence Adapter 내부에서만 사용한다.
