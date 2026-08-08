-- 근태 스키마 5종 (설계서 §5.3~5.4). Phase 4 계획서 D1·D8.
--
-- ★ 이 파일은 두 경로에서 실행된다 (계획서 3 D5, 계획서 4 D8):
--   1) 새 환경: docker compose up 이 빈 볼륨에서 자동 실행한다
--   2) 기존 환경: docker exec ... psql -f 로 손으로 한 번 적용한다
--
-- 그래서 IF NOT EXISTS 가 필수다. docker compose down -v 로 볼륨을 비우는 것은
-- Phase 2·3 데이터를 잃는 것이므로 금지한다. 반드시 두 번 실행해 멱등성을
-- 확인한다 (실패한 init 스크립트는 컨테이너를 죽이지 않고, 테이블 없이
-- healthy 한 컨테이너를 남긴다).
--
-- leave_request 는 approval 모듈 소유 테이블이다 (설계서 §5.3 "결재 문서의
-- 유형별 확장" — approval_doc 을 PK 로 참조하는 1:1 확장). 계획서 4 D1 은
-- attendance 가 approval 을 참조하면 순환 의존이 되므로 attendance 가
-- approvalId 대신 LeaveApplyCommand 값을 받는다고 확정했다. 그 확정과
-- 별개로, 이 테이블의 SQL 파일 위치는 "연차 기능이 이 Phase에서 처음
-- 쓰인다"는 이유로 여기 둔다 — 소유권은 여전히 approval 에 있다.

CREATE TABLE IF NOT EXISTS leave_request (
    approval_id BIGINT       PRIMARY KEY REFERENCES approval_doc(approval_id),
    leave_type  VARCHAR(20)  NOT NULL,
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    days        NUMERIC(3,1) NOT NULL,
    reason      VARCHAR(500)
);
COMMENT ON TABLE  leave_request            IS '연차 신청서. approval_doc 의 유형별 확장 (docType=LEAVE)';
COMMENT ON COLUMN leave_request.leave_type IS 'ANNUAL/HALF_AM/HALF_PM/SICK';
COMMENT ON COLUMN leave_request.days       IS '0.5 단위. 서버가 영업일수로 계산한다 — 기안자가 입력하지 않는다 (D5)';

CREATE TABLE IF NOT EXISTS attendance (
    att_id           BIGSERIAL    PRIMARY KEY,
    emp_id           BIGINT       NOT NULL REFERENCES employee(emp_id),
    work_date        DATE         NOT NULL,
    check_in         TIMESTAMP,
    check_out        TIMESTAMP,
    work_minutes     INT          DEFAULT 0,
    overtime_minutes INT          DEFAULT 0,
    status           VARCHAR(20)  NOT NULL,
    note             VARCHAR(200),
    UNIQUE (emp_id, work_date)
);
COMMENT ON TABLE  attendance         IS '일별 근태. 출퇴근 등록과 연차 반영이 함께 쓴다';
COMMENT ON COLUMN attendance.status  IS 'NORMAL/LATE/EARLY_LEAVE/ABSENT/LEAVE/HALF_LEAVE/HOLIDAY';
COMMENT ON COLUMN attendance.note    IS '예: 퇴근 미등록. check_out 이 없어도 ABSENT 로 적지 않는다 — 출근은 했으므로 거짓이 된다';

CREATE TABLE IF NOT EXISTS leave_balance (
    emp_id       BIGINT       NOT NULL REFERENCES employee(emp_id),
    year         INT          NOT NULL,
    granted_days NUMERIC(4,1) NOT NULL,
    used_days    NUMERIC(4,1) NOT NULL DEFAULT 0,
    updated_at   TIMESTAMP    NOT NULL,
    PRIMARY KEY (emp_id, year)
);
COMMENT ON TABLE leave_balance IS '연차 잔여. 잔여일수는 컬럼으로 두지 않고 (granted - used)로 계산한다 (불일치 방지) — LeaveBalance.getRemainingDays()';

CREATE TABLE IF NOT EXISTS leave_usage (
    usage_id    BIGSERIAL    PRIMARY KEY,
    emp_id      BIGINT       NOT NULL REFERENCES employee(emp_id),
    approval_id BIGINT       NOT NULL REFERENCES approval_doc(approval_id),
    leave_type  VARCHAR(20)  NOT NULL,
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    days        NUMERIC(3,1) NOT NULL,
    applied_at  TIMESTAMP    NOT NULL,
    UNIQUE (approval_id)
);
COMMENT ON TABLE  leave_usage             IS '연차 반영 이력. 결재 승인 1건당 정확히 1행';
COMMENT ON COLUMN leave_usage.approval_id IS 'UNIQUE — 결재 1건당 1회만 반영 (중복 반영 방지). 계획서 4 D2: 이 제약 위반을 잡아서 판단하지 않는다. existsByApprovalId 로 먼저 조회해 분기한다 — PostgreSQL 은 제약 위반 시 트랜잭션 전체를 중단시키므로, approve() 트랜잭션 안에서 그 예외를 잡으면 안 된다';

CREATE TABLE IF NOT EXISTS holiday (
    holiday_date DATE        PRIMARY KEY,
    holiday_name VARCHAR(50) NOT NULL
);
COMMENT ON TABLE holiday IS '공휴일. BusinessDayCalculator 가 주말과 함께 제외한다. 대체공휴일은 반영하지 않는다 (D5, B3) — 시드 파일 41 의 주석 참조';
