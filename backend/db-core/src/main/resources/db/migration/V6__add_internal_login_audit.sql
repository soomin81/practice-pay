-- 내부 운영자 로그인 감사 로그.
--
-- 지금까지 로그인 이력은 internal_user의 집계 필드(last_login_at/failed_login_count)로만
-- 남아 성공·실패 이력을 추적할 수 없었다(identity-access-api-key.md 8·9절). 이 테이블은
-- 로그인 시도 하나하나를 append-only로 기록한다 — 성공·실패·잠금 결과와 클라이언트 IP,
-- 그리고 알 수 없는 login_id로의 시도(internal_user_seq NULL)까지 남긴다.

CREATE TABLE internal_login_audit (
    internal_login_audit_seq BIGINT NOT NULL AUTO_INCREMENT,
    internal_login_audit_id VARCHAR(50) NOT NULL,
    -- 알 수 없는 login_id로의 시도는 대응 계정이 없어 NULL이다.
    internal_user_seq BIGINT NULL,
    attempted_login_id VARCHAR(100) NOT NULL,
    login_outcome VARCHAR(30) NOT NULL,
    -- 프록시 뒤 실제 IP(X-Forwarded-For)는 MVP에서 다루지 않는다 — remoteAddr만 남긴다.
    client_ip VARCHAR(45) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_internal_login_audit
        PRIMARY KEY (internal_login_audit_seq),

    CONSTRAINT uk_internal_login_audit_id
        UNIQUE (internal_login_audit_id),

    CONSTRAINT fk_internal_login_audit_internal_user
        FOREIGN KEY (internal_user_seq)
        REFERENCES internal_user (internal_user_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_internal_login_audit_outcome
        CHECK (
            login_outcome IN (
                'SUCCESS',
                'INVALID_CREDENTIALS',
                'LOCKED'
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '내부 운영자 로그인 감사 로그';

-- 최근 로그인/실패를 최신순으로 훑는 조회(GET /admin/login-audit)를 위한 인덱스.
CREATE INDEX idx_internal_login_audit_occurred_at
    ON internal_login_audit (occurred_at);
