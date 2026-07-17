-- ============================================================
-- V3__add_identity_access_and_merchant_api_key.sql
-- MySQL 8.x
-- ============================================================

-- 1. PG 내부 운영자 계정
CREATE TABLE internal_user (
    internal_user_seq BIGINT NOT NULL AUTO_INCREMENT,
    internal_user_id VARCHAR(50) NOT NULL,
    login_id VARCHAR(100) NOT NULL,
    email VARCHAR(320) NOT NULL,
    user_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NULL,
    user_status VARCHAR(30) NOT NULL,
    role_code VARCHAR(30) NOT NULL,
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until DATETIME(6) NULL,
    password_changed_at DATETIME(6) NULL,
    last_login_at DATETIME(6) NULL,
    invited_at DATETIME(6) NULL,
    activated_at DATETIME(6) NULL,
    terminated_at DATETIME(6) NULL,
    created_by_internal_user_seq BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_internal_user
        PRIMARY KEY (internal_user_seq),

    CONSTRAINT uk_internal_user_id
        UNIQUE (internal_user_id),

    CONSTRAINT uk_internal_user_login_id
        UNIQUE (login_id),

    CONSTRAINT uk_internal_user_email
        UNIQUE (email),

    CONSTRAINT fk_internal_user_created_by
        FOREIGN KEY (created_by_internal_user_seq)
        REFERENCES internal_user (internal_user_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_internal_user_status
        CHECK (
            user_status IN (
                'INVITED',
                'ACTIVE',
                'LOCKED',
                'SUSPENDED',
                'TERMINATED'
            )
        ),

    CONSTRAINT ck_internal_user_role
        CHECK (
            role_code IN (
                'SUPER_ADMIN',
                'OPERATOR',
                'VIEWER'
            )
        ),

    CONSTRAINT ck_internal_user_failed_login
        CHECK (failed_login_count >= 0),

    CONSTRAINT ck_internal_user_version
        CHECK (version >= 0),

    CONSTRAINT ck_internal_user_active_password
        CHECK (
            user_status <> 'ACTIVE'
            OR password_hash IS NOT NULL
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'PG 내부 운영자 계정';

CREATE INDEX idx_internal_user_status
    ON internal_user (user_status);

CREATE INDEX idx_internal_user_role_status
    ON internal_user (role_code, user_status);


-- 2. 가맹점 관리자 계정
CREATE TABLE merchant_user (
    merchant_user_seq BIGINT NOT NULL AUTO_INCREMENT,
    merchant_user_id VARCHAR(50) NOT NULL,
    merchant_seq BIGINT NOT NULL,
    login_id VARCHAR(100) NOT NULL,
    email VARCHAR(320) NOT NULL,
    user_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NULL,
    user_status VARCHAR(30) NOT NULL,
    role_code VARCHAR(30) NOT NULL,
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until DATETIME(6) NULL,
    password_changed_at DATETIME(6) NULL,
    last_login_at DATETIME(6) NULL,
    invited_at DATETIME(6) NULL,
    activated_at DATETIME(6) NULL,
    terminated_at DATETIME(6) NULL,
    invited_by_internal_user_seq BIGINT NULL,
    invited_by_merchant_user_seq BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_merchant_user
        PRIMARY KEY (merchant_user_seq),

    CONSTRAINT uk_merchant_user_id
        UNIQUE (merchant_user_id),

    CONSTRAINT uk_merchant_user_login
        UNIQUE (merchant_seq, login_id),

    CONSTRAINT uk_merchant_user_email
        UNIQUE (merchant_seq, email),

    CONSTRAINT fk_merchant_user_merchant
        FOREIGN KEY (merchant_seq)
        REFERENCES merchant (merchant_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_merchant_user_invited_internal
        FOREIGN KEY (invited_by_internal_user_seq)
        REFERENCES internal_user (internal_user_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_merchant_user_invited_merchant
        FOREIGN KEY (invited_by_merchant_user_seq)
        REFERENCES merchant_user (merchant_user_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_merchant_user_status
        CHECK (
            user_status IN (
                'INVITED',
                'ACTIVE',
                'LOCKED',
                'SUSPENDED',
                'TERMINATED'
            )
        ),

    CONSTRAINT ck_merchant_user_role
        CHECK (
            role_code IN (
                'OWNER',
                'ADMIN',
                'VIEWER'
            )
        ),

    CONSTRAINT ck_merchant_user_failed_login
        CHECK (failed_login_count >= 0),

    CONSTRAINT ck_merchant_user_version
        CHECK (version >= 0),

    CONSTRAINT ck_merchant_user_active_password
        CHECK (
            user_status <> 'ACTIVE'
            OR password_hash IS NOT NULL
        ),

    CONSTRAINT ck_merchant_user_single_inviter_type
        CHECK (
            NOT (
                invited_by_internal_user_seq IS NOT NULL
                AND invited_by_merchant_user_seq IS NOT NULL
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '가맹점 관리자 계정';

CREATE INDEX idx_merchant_user_merchant_status
    ON merchant_user (merchant_seq, user_status);

CREATE INDEX idx_merchant_user_merchant_role
    ON merchant_user (merchant_seq, role_code, user_status);


-- 3. 계정 초대 토큰
CREATE TABLE account_invitation (
    account_invitation_seq BIGINT NOT NULL AUTO_INCREMENT,
    account_invitation_id VARCHAR(50) NOT NULL,
    account_type VARCHAR(30) NOT NULL,
    internal_user_seq BIGINT NULL,
    merchant_user_seq BIGINT NULL,
    token_hash VARCHAR(255) NOT NULL,
    invitation_status VARCHAR(30) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    accepted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_account_invitation
        PRIMARY KEY (account_invitation_seq),

    CONSTRAINT uk_account_invitation_id
        UNIQUE (account_invitation_id),

    CONSTRAINT uk_account_invitation_token_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_account_invitation_internal_user
        FOREIGN KEY (internal_user_seq)
        REFERENCES internal_user (internal_user_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_account_invitation_merchant_user
        FOREIGN KEY (merchant_user_seq)
        REFERENCES merchant_user (merchant_user_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_account_invitation_type
        CHECK (
            account_type IN (
                'INTERNAL_USER',
                'MERCHANT_USER'
            )
        ),

    CONSTRAINT ck_account_invitation_status
        CHECK (
            invitation_status IN (
                'PENDING',
                'ACCEPTED',
                'EXPIRED',
                'REVOKED'
            )
        ),

    CONSTRAINT ck_account_invitation_target
        CHECK (
            (
                account_type = 'INTERNAL_USER'
                AND internal_user_seq IS NOT NULL
                AND merchant_user_seq IS NULL
            )
            OR
            (
                account_type = 'MERCHANT_USER'
                AND merchant_user_seq IS NOT NULL
                AND internal_user_seq IS NULL
            )
        ),

    CONSTRAINT ck_account_invitation_accepted_at
        CHECK (
            invitation_status <> 'ACCEPTED'
            OR accepted_at IS NOT NULL
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '내부 운영자 및 가맹점 사용자 활성화 초대';

CREATE INDEX idx_account_invitation_pending
    ON account_invitation (invitation_status, expires_at);


-- 4. 가맹점 결제 API 연동 Key
CREATE TABLE merchant_api_key (
    merchant_api_key_seq BIGINT NOT NULL AUTO_INCREMENT,
    merchant_api_key_id VARCHAR(50) NOT NULL,
    merchant_seq BIGINT NOT NULL,
    key_name VARCHAR(100) NOT NULL,
    api_environment VARCHAR(10) NOT NULL,
    key_prefix VARCHAR(50) NOT NULL,
    secret_hash VARCHAR(255) NOT NULL,
    hash_algorithm VARCHAR(30) NOT NULL,
    api_key_status VARCHAR(30) NOT NULL,
    expires_at DATETIME(6) NULL,
    last_used_at DATETIME(6) NULL,
    created_by_merchant_user_seq BIGINT NOT NULL,
    revoked_by_merchant_user_seq BIGINT NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_merchant_api_key
        PRIMARY KEY (merchant_api_key_seq),

    CONSTRAINT uk_merchant_api_key_id
        UNIQUE (merchant_api_key_id),

    CONSTRAINT uk_merchant_api_key_prefix
        UNIQUE (key_prefix),

    CONSTRAINT fk_merchant_api_key_merchant
        FOREIGN KEY (merchant_seq)
        REFERENCES merchant (merchant_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_merchant_api_key_created_by
        FOREIGN KEY (created_by_merchant_user_seq)
        REFERENCES merchant_user (merchant_user_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_merchant_api_key_revoked_by
        FOREIGN KEY (revoked_by_merchant_user_seq)
        REFERENCES merchant_user (merchant_user_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_merchant_api_key_environment
        CHECK (
            api_environment IN (
                'TEST',
                'LIVE'
            )
        ),

    CONSTRAINT ck_merchant_api_key_status
        CHECK (
            api_key_status IN (
                'ACTIVE',
                'REVOKED',
                'EXPIRED'
            )
        ),

    CONSTRAINT ck_merchant_api_key_version
        CHECK (version >= 0),

    CONSTRAINT ck_merchant_api_key_revoked_at
        CHECK (
            api_key_status <> 'REVOKED'
            OR revoked_at IS NOT NULL
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '가맹점 서버의 결제 API 연동용 API Key';

CREATE INDEX idx_merchant_api_key_merchant_status
    ON merchant_api_key (merchant_seq, api_key_status);

CREATE INDEX idx_merchant_api_key_expiration
    ON merchant_api_key (api_key_status, expires_at);


-- 5. API Key Scope
CREATE TABLE merchant_api_key_scope (
    merchant_api_key_seq BIGINT NOT NULL,
    scope_code VARCHAR(50) NOT NULL,
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_merchant_api_key_scope
        PRIMARY KEY (merchant_api_key_seq, scope_code),

    CONSTRAINT fk_merchant_api_key_scope_key
        FOREIGN KEY (merchant_api_key_seq)
        REFERENCES merchant_api_key (merchant_api_key_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_merchant_api_key_scope_code
        CHECK (
            scope_code IN (
                'PAYMENT_CREATE',
                'PAYMENT_READ',
                'REFUND_CREATE',
                'REFUND_READ',
                'SETTLEMENT_READ'
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '가맹점 API Key 권한 Scope';
