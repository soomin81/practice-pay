-- 가맹점 관리자 로그인 감사 로그.
--
-- V6의 internal_login_audit(내부 운영자)를 가맹점 관리자 로그인으로 확장한 것이다.
-- merchant_user.last_login_at 집계 필드로만 남던 로그인 이력을 시도 하나하나로 남긴다.
-- 가맹점 로그인은 merchant_code로 가맹점을 먼저 확정하므로, 없는 merchant_code로의 시도는
-- merchant_seq/merchant_user_seq가 모두 NULL이고 attempted_merchant_code만 남는다.

CREATE TABLE merchant_login_audit (
    merchant_login_audit_seq BIGINT NOT NULL AUTO_INCREMENT,
    merchant_login_audit_id VARCHAR(50) NOT NULL,
    -- 없는 merchant_code로의 시도는 대응 가맹점이 없어 NULL이다.
    merchant_seq BIGINT NULL,
    -- 가맹점은 찾았지만 login_id가 없는 시도는 merchant_seq만 있고 이 값은 NULL이다.
    merchant_user_seq BIGINT NULL,
    attempted_merchant_code VARCHAR(50) NOT NULL,
    attempted_login_id VARCHAR(100) NOT NULL,
    login_outcome VARCHAR(30) NOT NULL,
    client_ip VARCHAR(45) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_merchant_login_audit
        PRIMARY KEY (merchant_login_audit_seq),

    CONSTRAINT uk_merchant_login_audit_id
        UNIQUE (merchant_login_audit_id),

    CONSTRAINT fk_merchant_login_audit_merchant
        FOREIGN KEY (merchant_seq)
        REFERENCES merchant (merchant_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_merchant_login_audit_merchant_user
        FOREIGN KEY (merchant_user_seq)
        REFERENCES merchant_user (merchant_user_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_merchant_login_audit_outcome
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
  COMMENT = '가맹점 관리자 로그인 감사 로그';

-- 최근 로그인/실패를 최신순으로 훑는 조회(GET /admin/merchant-login-audit)를 위한 인덱스.
CREATE INDEX idx_merchant_login_audit_occurred_at
    ON merchant_login_audit (occurred_at);
