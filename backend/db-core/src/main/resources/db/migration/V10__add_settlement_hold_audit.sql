-- 정산 채권의 보류·해제·취소 이력.
--
-- settlement_receivable.hold_reason_code는 "지금 왜 막혀 있나"에만 답하는 현재 상태
-- 필드라, 보류를 풀면 NULL로 지워져 막혔던 사실 자체가 사라진다. 이 세 전이는 전부
-- 사람이 판단해 실행하고 가맹점에게 나갈 돈을 좌우하므로(ADR-007), 로그인 감사와 같은
-- append-only 이력으로 따로 남긴다.

CREATE TABLE settlement_hold_audit (
    settlement_hold_audit_seq BIGINT NOT NULL AUTO_INCREMENT,
    settlement_hold_audit_id VARCHAR(50) NOT NULL,
    settlement_receivable_seq BIGINT NOT NULL,
    -- 인증된 내부 운영자만 실행할 수 있어 주체가 없는 행이 존재하지 않는다
    -- (로그인 감사가 '없는 계정으로의 시도' 때문에 NULL을 허용한 것과 다르다).
    internal_user_seq BIGINT NOT NULL,
    hold_action VARCHAR(30) NOT NULL,
    -- HELD일 때의 사유 코드. settlement_receivable.hold_reason_code에 들어간 값과 같다.
    reason_code VARCHAR(50) NULL,
    -- 사람이 남기는 자유 메모. 해제·취소는 자동 경로가 없어 실행자 말고는 이유를 아는
    -- 곳이 없으므로 애플리케이션이 필수로 요구한다.
    note VARCHAR(500) NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_settlement_hold_audit
        PRIMARY KEY (settlement_hold_audit_seq),

    CONSTRAINT uk_settlement_hold_audit_id
        UNIQUE (settlement_hold_audit_id),

    CONSTRAINT fk_settlement_hold_audit_receivable
        FOREIGN KEY (settlement_receivable_seq)
        REFERENCES settlement_receivable (settlement_receivable_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_settlement_hold_audit_internal_user
        FOREIGN KEY (internal_user_seq)
        REFERENCES internal_user (internal_user_seq)
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT ck_settlement_hold_audit_action
        CHECK (
            hold_action IN (
                'HELD',
                'RELEASED',
                'CANCELLED'
            )
        )
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = '정산 채권 보류·해제·취소 이력';

-- 채권 한 건의 이력을 최신순으로 훑는 조회
-- (GET /admin/settlement-receivables/{id}/hold-history)를 위한 인덱스.
CREATE INDEX idx_settlement_hold_audit_receivable
    ON settlement_hold_audit (settlement_receivable_seq, occurred_at);
