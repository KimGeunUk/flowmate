CREATE TABLE approval_doc (
    approval_id  BIGSERIAL    PRIMARY KEY,
    doc_no       VARCHAR(30)  NOT NULL UNIQUE,
    doc_type     VARCHAR(20)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    content      TEXT,
    drafter_id   BIGINT       NOT NULL REFERENCES employee(emp_id),
    dept_id      BIGINT       NOT NULL REFERENCES department(dept_id),
    amount       NUMERIC(15)  NOT NULL DEFAULT 0,
    status       VARCHAR(20)  NOT NULL,
    current_step INT          NOT NULL DEFAULT 0,
    drafted_at   TIMESTAMP    NOT NULL,
    submitted_at TIMESTAMP,
    completed_at TIMESTAMP
);
COMMENT ON TABLE  approval_doc              IS '결재 문서';
COMMENT ON COLUMN approval_doc.doc_no       IS '문서번호. EXP-2026-0001 형식';
COMMENT ON COLUMN approval_doc.doc_type     IS 'EXPENSE/PURCHASE/LEAVE/CONTRACT/GENERAL';
COMMENT ON COLUMN approval_doc.amount       IS '리스크 통계와 결재선 정책이 참조한다';
COMMENT ON COLUMN approval_doc.status       IS 'DRAFT/PENDING/APPROVED/REJECTED/CANCELED';
COMMENT ON COLUMN approval_doc.current_step IS '진행 중인 결재 단계. DRAFT 는 0, 상신 시 1부터';

CREATE INDEX idx_doc_drafter ON approval_doc(drafter_id, status, drafted_at DESC);
CREATE INDEX idx_doc_status  ON approval_doc(status, drafted_at DESC);

CREATE TABLE approval_line (
    line_id      BIGSERIAL   PRIMARY KEY,
    approval_id  BIGINT      NOT NULL REFERENCES approval_doc(approval_id),
    step_no      INT         NOT NULL,
    approver_id  BIGINT      NOT NULL REFERENCES employee(emp_id),
    line_type    VARCHAR(20) NOT NULL,
    status       VARCHAR(20) NOT NULL,
    comment      VARCHAR(500),
    processed_at TIMESTAMP,
    UNIQUE (approval_id, step_no)
);
COMMENT ON TABLE  approval_line           IS '결재선. 한 문서의 단계별 결재자';
COMMENT ON COLUMN approval_line.line_type IS 'APPROVAL(결재)/AGREEMENT(합의)/REFERENCE(참조)';
COMMENT ON COLUMN approval_line.status    IS 'WAITING/CURRENT/APPROVED/REJECTED/SKIPPED';

-- 내 결재함의 "내 차례인 문서" 판정이 이 인덱스를 탄다
CREATE INDEX idx_line_approver ON approval_line(approver_id, status);

CREATE TABLE approval_history (
    history_id  BIGSERIAL   PRIMARY KEY,
    approval_id BIGINT      NOT NULL REFERENCES approval_doc(approval_id),
    actor_id    BIGINT      NOT NULL REFERENCES employee(emp_id),
    action      VARCHAR(20) NOT NULL,
    comment     VARCHAR(500),
    created_at  TIMESTAMP   NOT NULL
);
COMMENT ON TABLE  approval_history        IS '결재 이력. 문서 상세 화면의 타임라인';
COMMENT ON COLUMN approval_history.action IS 'DRAFT/SUBMIT/APPROVE/REJECT/CANCEL';

CREATE INDEX idx_history_approval ON approval_history(approval_id, created_at);

CREATE TABLE approval_attachment (
    attach_id   BIGSERIAL    PRIMARY KEY,
    approval_id BIGINT       NOT NULL REFERENCES approval_doc(approval_id),
    file_name   VARCHAR(255) NOT NULL,
    file_path   VARCHAR(500) NOT NULL,
    file_size   BIGINT       NOT NULL,
    uploaded_at TIMESTAMP    NOT NULL
);
COMMENT ON TABLE  approval_attachment           IS '첨부파일';
COMMENT ON COLUMN approval_attachment.file_name IS '사용자가 올린 원본 파일명. 화면 표시와 다운로드 시 사용';
COMMENT ON COLUMN approval_attachment.file_path IS '디스크 저장 경로. UUID 기반이라 원본명과 다르다';

CREATE INDEX idx_attach_approval ON approval_attachment(approval_id);

-- ★ Phase 5 AI 사전점검의 학습 원천. 이 테이블을 위해 반려 화면에서 유형 선택을 필수로 만든다.
CREATE TABLE approval_reject_history (
    id              BIGSERIAL    PRIMARY KEY,
    approval_id     BIGINT       NOT NULL REFERENCES approval_doc(approval_id),
    doc_type        VARCHAR(20)  NOT NULL,
    dept_id         BIGINT       NOT NULL,
    rejector_id     BIGINT       NOT NULL,
    reason_category VARCHAR(30)  NOT NULL,
    reason_text     VARCHAR(500),
    rejected_at     TIMESTAMP    NOT NULL
);
COMMENT ON TABLE  approval_reject_history                 IS 'AI 사전점검이 참조하는 반려 이력';
COMMENT ON COLUMN approval_reject_history.doc_type        IS 'approval_doc 과 중복이지만 의도한 비정규화. 사전점검이 조인 없이 끝내기 위한 것';
COMMENT ON COLUMN approval_reject_history.reason_category IS 'INSUFFICIENT_CONTENT/EXCESSIVE_AMOUNT/MISSING_EVIDENCE/PROCEDURE_ERROR/BUDGET_EXCEEDED/OTHER';

CREATE INDEX idx_reject_type_dept ON approval_reject_history(doc_type, dept_id, rejected_at DESC);
