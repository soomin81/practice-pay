-- 구매자 개인정보(이름·이메일·휴대전화)와 그 원본 열람 감사 로그.
--
-- payment에 컬럼을 더하지 않고 별도 테이블에 둔다(ADR-008): 결제를 읽는 모든 경로가
-- 개인정보를 함께 끌고 오지 않고, 보관 기간이 지나 파기할 때 이 행만 지우면 결제 기록은
-- 그대로 남는다 — 결제 기록과 개인정보는 수명이 다르다.

CREATE TABLE payment_customer (
    payment_customer_seq BIGINT NOT NULL AUTO_INCREMENT,
    payment_customer_id VARCHAR(50) NOT NULL,
    payment_seq BIGINT NOT NULL,

    -- AES-256-GCM 암호문. 값마다 새 랜덤 IV를 앞에 붙여 Base64로 담는다 — 같은 이메일이라도
    -- 행마다 암호문이 달라서 DB만 유출되면 동일인 여부조차 드러나지 않는다.
    customer_name_encrypted VARCHAR(512) NOT NULL,
    -- 화면·엑셀·로그가 읽는 값. 쓸 때 함께 저장해서 읽기 경로가 복호화를 아예 타지 않게 한다.
    customer_name_masked VARCHAR(100) NOT NULL,

    customer_email_encrypted VARCHAR(512) NOT NULL,
    customer_email_masked VARCHAR(255) NOT NULL,
    -- HMAC(pepper, 정규화된 값)의 Blind Index. 랜덤 IV 때문에 암호문으로는 검색할 수 없어서
    -- 정확 일치 검색용으로 따로 둔다. 같은 값이 같은 인덱스를 가지므로 동일인 여부는
    -- 드러난다 — 검색이 실제로 필요해서 감수한 대가다(ADR-008).
    customer_email_index CHAR(64) NOT NULL,

    customer_phone_encrypted VARCHAR(512) NOT NULL,
    customer_phone_masked VARCHAR(50) NOT NULL,
    customer_phone_index CHAR(64) NOT NULL,

    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT pk_payment_customer
        PRIMARY KEY (payment_customer_seq),

    CONSTRAINT uk_payment_customer_id
        UNIQUE (payment_customer_id),

    -- 결제 1건당 1건.
    CONSTRAINT uk_payment_customer_payment
        UNIQUE (payment_seq),

    CONSTRAINT fk_payment_customer_payment
        FOREIGN KEY (payment_seq)
        REFERENCES payment (payment_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '구매자 개인정보(암호문·마스킹·Blind Index)';

-- 이름에는 Blind Index를 두지 않는다 — 동명이인이 흔해 검색이 신뢰할 수 없고, 두면
-- "같은 이름인지"가 드러나는 대가만 남는다(ADR-008).
CREATE INDEX idx_payment_customer_email
    ON payment_customer (customer_email_index);

CREATE INDEX idx_payment_customer_phone
    ON payment_customer (customer_phone_index);

-- 마스킹되지 않은 원본을 누가 언제 어느 결제에서 봤는지.
--
-- 읽기만 하는 동작에 감사를 붙인 유일한 자료다 — 상태를 바꾸지 않아도 "봤다"는 사실
-- 자체가 사건이기 때문이다(ADR-008).
CREATE TABLE customer_pii_access_audit (
    customer_pii_access_audit_seq BIGINT NOT NULL AUTO_INCREMENT,
    customer_pii_access_audit_id VARCHAR(50) NOT NULL,
    -- 인증된 SUPER_ADMIN이 특정 결제를 지목해야만 열람이 성립하므로 둘 다 NOT NULL이다.
    internal_user_seq BIGINT NOT NULL,
    payment_seq BIGINT NOT NULL,
    -- 왜 봤는지. 자동 경로가 없는 행위라 실행한 사람 말고는 이유를 아는 곳이 없다.
    reason VARCHAR(500) NOT NULL,
    client_ip VARCHAR(45) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_customer_pii_access_audit
        PRIMARY KEY (customer_pii_access_audit_seq),

    CONSTRAINT uk_customer_pii_access_audit_id
        UNIQUE (customer_pii_access_audit_id),

    CONSTRAINT fk_customer_pii_access_audit_internal_user
        FOREIGN KEY (internal_user_seq)
        REFERENCES internal_user (internal_user_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_customer_pii_access_audit_payment
        FOREIGN KEY (payment_seq)
        REFERENCES payment (payment_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '구매자 개인정보 원본 열람 감사 로그';

-- 최근 열람을 최신순으로 훑는 조회와, 결제 한 건의 열람 이력 조회.
CREATE INDEX idx_customer_pii_access_audit_occurred_at
    ON customer_pii_access_audit (occurred_at);

CREATE INDEX idx_customer_pii_access_audit_payment
    ON customer_pii_access_audit (payment_seq, occurred_at);
