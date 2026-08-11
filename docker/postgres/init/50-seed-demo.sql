-- 데모용 대량 시드: 결재 문서 200건 + 반려 40건(부서·유형별 편중) + 근태 3개월분.
-- 데모 데이터: 결재 문서 200건 + 반려 이력 40건.
--
-- ★ ON CONFLICT DO NOTHING: 이 파일은 몇 번을 다시 실행해도 안전하다.
--   approval_doc / approval_line / approval_history / approval_reject_history 는
--   전부 명시적 PK(10001~ 대역, 기존 21-seed-approval.sql 의 1~6 과 절대 겹치지 않는다)를
--   쓰고 그 PK 로 충돌을 판정한다. attendance 는 자체 UNIQUE(emp_id, work_date) 로
--   충돌을 판정한다. 재실행해도 기존 6건이나 이미 들어간 행을 다시 만들거나
-- 중복시키지 않는다.
--
-- ★ 반려 사유를 부서·문서유형별로 편중시키는 것이 이 파일의 핵심이다.
--   고르게 뿌리면 사전점검이 "이 부서·이 유형에서 자주 나는 반려"를 집계해도
--   아무 신호가 없다. 그래서 반려 40건 중 4개 조합(부서×유형)에 편중을 명시적으로 심는다:
--
--     개발팀(dept 7) + PURCHASE  -> MISSING_EVIDENCE 위주      (12건 중 8건)
--     마케팅팀(dept 6) + EXPENSE -> INSUFFICIENT_CONTENT 위주  (12건 중 8건)
--     재무팀(dept 5)   + EXPENSE -> EXCESSIVE_AMOUNT 위주      (8건 중 5건)
--     인사팀(dept 4)   + GENERAL -> PROCEDURE_ERROR 위주       (8건 중 5건)
--
--   검증: SELECT dept_id, doc_type, reason_category, COUNT(*) FROM approval_reject_history
--         GROUP BY 1,2,3 ORDER BY 4 DESC; 로 편중이 실제로 보이는지 확인한다.
--
-- ★ 결재선은 ApprovalLinePolicy 를 호출하지 않고 손으로 고정한다 (
--   21-seed-approval.sql 과 같은 판단). 부서 관리자(또는 대리 승인자) 1인 -> 상위
--   본부장 1인의 2단계. 정책 코드를 호출해서 200건을 만들면 느리고, 정책이 바뀌면
--   시드도 조용히 바뀐다 (Phase 2·4에서 이미 두 번 내린 판단과 같다).
--
--   인사팀(4)   1단계 = 4(최민석)   2단계 = 2(김성일, 경영지원본부)
--   재무팀(5)   1단계 = 7(오세훈)   2단계 = 2(김성일, 경영지원본부)
--   마케팅팀(6) 1단계 = 10(윤서영)  2단계 = 3(박현주, 사업본부)
--   개발팀(7)   1단계 = 17(노은지)  2단계 = 3(박현주, 사업본부)
--
--   ★ 개발팀 1단계는 실제 부서장(14, 신동혁)이 아니라 17(노은지)을 쓴다 - 의도적이다.
--   ApprovalQueryServiceIT 가 신동혁(14)의 대기함(id 2)·완료함(id 3,4,5)을 정확한
--   개수로 단정하고, 곽수빈(18)의 기안함 합계를 정확히 6건으로 단정한다(21-seed-approval.sql
--   의 기존 6건 전제). 대량 시드가 14 를 승인자로 더 쓰거나 18 을 기안자로 더 쓰면
--   그 단정이 깨진다 - 이 시드는 "신호"(반려 편중)를 위한 것이지 결재선의 사실성을
--   위한 것이 아니므로, 겹치지 않는 다른 개발팀 사원으로 대체한다. 같은 이유로
--   개발팀 기안자 후보에서도 14·17·18 을 제외한다.
--
-- ★ LEAVE 문서는 이 시드에 포함하지 않는다. LEAVE 는 leave_request 확장 테이블과
-- 승인 시 leave_balance/attendance 반영까지 얽혀 있어 대량으로 손으로
--   찍으면 그 트랜잭션 규약을 우회하게 된다. 반려 편중 데모는 EXPENSE/PURCHASE/
--   GENERAL 만으로 대표 조합(개발팀 PURCHASE, 마케팅팀 EXPENSE)을 충분히 보인다.
--
-- ★ CONTRACT 문서유형도 이 시드에 포함하지 않는다 - 의도적이다. ApprovalDocMapperIT
--   가 "CON 접두사 문서번호가 아직 하나도 없다"(COALESCE 가 NULL 을 0 으로 바꾸는지의
--   검증)를 단정한다. 대량 시드가 CONTRACT 문서를 만들면 그 단정이 영구히 깨진다.
--
-- ★ 근태: 2026-01-01 ~ 오늘(CURRENT_DATE), 평일만, holiday 테이블에 있는 날짜는 제외한다.
--   끝을 고정 날짜가 아니라 CURRENT_DATE 로 두는 이유 - 언제 클론해서 띄우든
--   "올해 1월부터 오늘까지"가 채워져 화면이 비어 보이지 않는다.
--
--   원래는 2~4월만 채웠고, 그 이유가 "AttendanceQueryServiceIT 가 6·7월을 쓰므로
--   피한다"였다. 그 방향이 거꾸로였다 - 시드가 테스트를 피해 다니면 시드 범위를
--   넓힐 때마다 테스트가 깨지고, 어느 달이 예약됐는지 아무도 기억하지 못한다.
--   지금은 그 테스트가 @BeforeEach 로 자기 달을 직접 비우고 시작한다.
--   시드는 테스트를 신경 쓰지 않고 데모에 좋은 범위를 고르면 된다.

-- =====================================================================
-- 1) 결재 문서 200건을 임시 테이블에 먼저 만든다 (idx 1~200 -> approval_id 10001~10200)
-- =====================================================================
DROP TABLE IF EXISTS demo_doc;

CREATE TEMP TABLE demo_doc AS
WITH rejected_dev_purchase AS (
    SELECT
        gs                                                              AS ord,
        7::bigint                                                       AS dept_id,
        'PURCHASE'::varchar(20)                                         AS doc_type,
        (ARRAY[15,16,19,20]::bigint[])[1 + ((gs - 1) % 4)]              AS drafter_id,
        17::bigint                                                      AS approver1_id,
        3::bigint                                                       AS approver2_id,
        (CASE
            WHEN gs <= 8  THEN 'MISSING_EVIDENCE'
            WHEN gs <= 10 THEN 'PROCEDURE_ERROR'
            WHEN gs = 11  THEN 'OTHER'
            ELSE 'INSUFFICIENT_CONTENT'
         END)::varchar(30)                                              AS reason_category,
        (800000 + gs * 50000)::numeric(15)                              AS amount
    FROM generate_series(1, 12) AS gs
),
rejected_mkt_expense AS (
    SELECT
        gs                                                              AS ord,
        6::bigint                                                       AS dept_id,
        'EXPENSE'::varchar(20)                                          AS doc_type,
        (ARRAY[11,12,13]::bigint[])[1 + ((gs - 1) % 3)]                 AS drafter_id,
        10::bigint                                                      AS approver1_id,
        3::bigint                                                       AS approver2_id,
        (CASE
            WHEN gs <= 8  THEN 'INSUFFICIENT_CONTENT'
            WHEN gs <= 10 THEN 'EXCESSIVE_AMOUNT'
            WHEN gs = 11  THEN 'OTHER'
            ELSE 'PROCEDURE_ERROR'
         END)::varchar(30)                                              AS reason_category,
        (100000 + gs * 20000)::numeric(15)                              AS amount
    FROM generate_series(1, 12) AS gs
),
rejected_fin_expense AS (
    SELECT
        gs                                                              AS ord,
        5::bigint                                                       AS dept_id,
        'EXPENSE'::varchar(20)                                          AS doc_type,
        (ARRAY[8,9]::bigint[])[1 + ((gs - 1) % 2)]                      AS drafter_id,
        7::bigint                                                       AS approver1_id,
        2::bigint                                                       AS approver2_id,
        (CASE
            WHEN gs <= 5 THEN 'EXCESSIVE_AMOUNT'
            WHEN gs <= 7 THEN 'BUDGET_EXCEEDED'
            ELSE 'OTHER'
         END)::varchar(30)                                              AS reason_category,
        (300000 + gs * 40000)::numeric(15)                              AS amount
    FROM generate_series(1, 8) AS gs
),
rejected_hr_general AS (
    SELECT
        gs                                                              AS ord,
        4::bigint                                                       AS dept_id,
        'GENERAL'::varchar(20)                                          AS doc_type,
        (ARRAY[5,6]::bigint[])[1 + ((gs - 1) % 2)]                      AS drafter_id,
        4::bigint                                                       AS approver1_id,
        2::bigint                                                       AS approver2_id,
        (CASE
            WHEN gs <= 5 THEN 'PROCEDURE_ERROR'
            WHEN gs <= 7 THEN 'MISSING_EVIDENCE'
            ELSE 'OTHER'
         END)::varchar(30)                                              AS reason_category,
        0::numeric(15)                                                  AS amount
    FROM generate_series(1, 8) AS gs
),
rejected_all AS (
    SELECT
        row_number() OVER (ORDER BY dept_id, doc_type, ord) AS idx,
        dept_id, doc_type, drafter_id, approver1_id, approver2_id,
        reason_category, amount
    FROM (
        SELECT * FROM rejected_dev_purchase
        UNION ALL SELECT * FROM rejected_mkt_expense
        UNION ALL SELECT * FROM rejected_fin_expense
        UNION ALL SELECT * FROM rejected_hr_general
    ) u
),
-- 반려되지 않는 나머지 160건. 부서·유형을 고르게 순환시킨다 - 이 그룹은 신호가 아니라
-- "고르게 뿌리면 편중이 안 보인다"는 대비군이다. 상태는 APPROVED 130 / PENDING 20 /
-- DRAFT 5 / CANCELED 5 로 나눈다.
other_docs AS (
    SELECT
        40 + k                                                          AS idx,
        (ARRAY[4,5,6,7]::bigint[])[1 + ((k - 1) % 4)]                   AS dept_id,
        (ARRAY['EXPENSE','PURCHASE','GENERAL']::varchar(20)[])
            [1 + (((k - 1) / 4) % 3)]                                   AS doc_type,
        CASE
            WHEN k <= 130 THEN 'APPROVED'
            WHEN k <= 150 THEN 'PENDING'
            WHEN k <= 155 THEN 'DRAFT'
            ELSE 'CANCELED'
        END::varchar(20)                                                AS status,
        k                                                                AS k
    FROM generate_series(1, 160) AS k
),
other_docs_full AS (
    SELECT
        idx, dept_id, doc_type, status,
        CASE dept_id
            WHEN 4 THEN (ARRAY[5,6]::bigint[])[1 + ((k - 1) % 2)]
            WHEN 5 THEN (ARRAY[8,9]::bigint[])[1 + ((k - 1) % 2)]
            WHEN 6 THEN (ARRAY[11,12,13]::bigint[])[1 + ((k - 1) % 3)]
            WHEN 7 THEN (ARRAY[15,16,19,20]::bigint[])[1 + ((k - 1) % 4)]
        END                                                             AS drafter_id,
        CASE dept_id WHEN 4 THEN 4 WHEN 5 THEN 7 WHEN 6 THEN 10 WHEN 7 THEN 17 END::bigint
                                                                         AS approver1_id,
        CASE WHEN dept_id IN (4, 5) THEN 2 WHEN dept_id IN (6, 7) THEN 3 END::bigint
                                                                         AS approver2_id,
        NULL::varchar(30)                                               AS reason_category,
        (CASE doc_type
            WHEN 'EXPENSE'  THEN 50000   + (k * 3000)  % 750000
            WHEN 'PURCHASE' THEN 300000  + (k * 15000) % 2700000
            ELSE 0
         END)::numeric(15)                                              AS amount
    FROM other_docs
),
combined AS (
    SELECT idx, dept_id, doc_type, drafter_id, approver1_id, approver2_id,
           reason_category, amount, 'REJECTED'::varchar(20) AS status
    FROM rejected_all
    UNION ALL
    SELECT idx, dept_id, doc_type, drafter_id, approver1_id, approver2_id,
           reason_category, amount, status
    FROM other_docs_full
)
SELECT
    idx,
    10000 + idx                                                         AS approval_id,
    (CASE doc_type
        WHEN 'EXPENSE'  THEN 'EXP'
        WHEN 'PURCHASE' THEN 'PUR'
        ELSE 'GEN'
     END || '-2026-' || lpad((9000 + idx)::text, 4, '0'))::varchar(30)  AS doc_no,
    doc_type,
    (CASE doc_type
        WHEN 'EXPENSE'  THEN '경비 정산 요청 #'
        WHEN 'PURCHASE' THEN '구매 요청 #'
        ELSE '업무 공유 #'
     END || idx)::varchar(200)                                          AS title,
    (CASE doc_type
        WHEN 'EXPENSE'  THEN '업무 관련 경비를 정산합니다 (데모 시드).'
        WHEN 'PURCHASE' THEN '필요 물품 구매를 요청합니다 (데모 시드).'
        ELSE '업무 관련 사항을 공유합니다 (데모 시드).'
     END)                                                                AS content,
    drafter_id, dept_id, amount, status,
    CASE status
        WHEN 'REJECTED' THEN 1
        WHEN 'APPROVED' THEN 2
        WHEN 'PENDING'  THEN 1
        ELSE 0
    END                                                                  AS current_step,
    approver1_id, approver2_id, reason_category,
    (CASE reason_category
        WHEN 'MISSING_EVIDENCE'     THEN '증빙 자료가 첨부되지 않았습니다.'
        WHEN 'INSUFFICIENT_CONTENT' THEN '신청 사유가 구체적이지 않습니다.'
        WHEN 'EXCESSIVE_AMOUNT'     THEN '유사 건 대비 금액이 과다합니다.'
        WHEN 'BUDGET_EXCEEDED'      THEN '부서 예산 한도를 초과했습니다.'
        WHEN 'PROCEDURE_ERROR'      THEN '사전 승인 절차가 누락되었습니다.'
        WHEN 'OTHER'                THEN '추가 확인이 필요합니다.'
     END)::varchar(500)                                                 AS reason_text,
    (timestamp '2026-04-01 09:00:00'
        + ((idx * 37) % 122) * interval '1 day'
        + (idx % 8) * interval '1 hour'
        + (idx % 60) * interval '1 minute')                             AS drafted_at
FROM combined;

-- submitted_at / completed_at 은 drafted_at 기준 파생값이라 별 컬럼으로 갱신한다
-- (윗 SELECT 안에서 바로 참조하면 같은 SELECT 리스트 안이라 재사용이 안 되므로 분리한다).
ALTER TABLE demo_doc ADD COLUMN submitted_at TIMESTAMP;
ALTER TABLE demo_doc ADD COLUMN completed_at TIMESTAMP;

UPDATE demo_doc
   SET submitted_at = CASE WHEN status <> 'DRAFT' THEN drafted_at + interval '1 hour' END;

UPDATE demo_doc
   SET completed_at = CASE
        WHEN status IN ('APPROVED', 'REJECTED') THEN submitted_at + interval '1 day' + (idx % 3) * interval '1 day'
        WHEN status = 'CANCELED'                THEN drafted_at + interval '20 minutes'
        ELSE NULL
   END;

-- =====================================================================
-- 2) approval_doc
-- =====================================================================
INSERT INTO approval_doc
    (approval_id, doc_no, doc_type, title, content, drafter_id, dept_id, amount,
     status, current_step, drafted_at, submitted_at, completed_at)
SELECT approval_id, doc_no, doc_type, title, content, drafter_id, dept_id, amount,
       status, current_step, drafted_at, submitted_at, completed_at
FROM demo_doc
ON CONFLICT (approval_id) DO NOTHING;

-- =====================================================================
-- 3) approval_line - DRAFT/CANCELED 는 결재선이 없다 (21-seed-approval.sql 과 같은 규약)
-- =====================================================================
INSERT INTO approval_line (line_id, approval_id, step_no, approver_id, line_type, status, comment, processed_at)
SELECT
    20000 + (idx - 1) * 2 + 1                                            AS line_id,
    approval_id, 1, approver1_id, 'APPROVAL',
    CASE status WHEN 'REJECTED' THEN 'REJECTED' WHEN 'PENDING' THEN 'CURRENT' ELSE 'APPROVED' END,
    CASE status WHEN 'REJECTED' THEN reason_text ELSE NULL END,
    CASE status WHEN 'PENDING' THEN NULL ELSE submitted_at + interval '2 hours' END
FROM demo_doc
WHERE status IN ('REJECTED', 'PENDING', 'APPROVED')
ON CONFLICT (line_id) DO NOTHING;

INSERT INTO approval_line (line_id, approval_id, step_no, approver_id, line_type, status, comment, processed_at)
SELECT
    20000 + (idx - 1) * 2 + 2                                            AS line_id,
    approval_id, 2, approver2_id, 'APPROVAL',
    CASE status WHEN 'REJECTED' THEN 'SKIPPED' WHEN 'PENDING' THEN 'WAITING' ELSE 'APPROVED' END,
    NULL,
    CASE status WHEN 'APPROVED' THEN completed_at ELSE NULL END
FROM demo_doc
WHERE status IN ('REJECTED', 'PENDING', 'APPROVED')
ON CONFLICT (line_id) DO NOTHING;

-- =====================================================================
-- 4) approval_history - 슬롯 4개(DRAFT/SUBMIT|CANCEL/REJECT|APPROVE1/APPROVE2)를
--    문서마다 예약해 둔다. 상태별로 필요한 슬롯만 채운다.
-- =====================================================================
INSERT INTO approval_history (history_id, approval_id, actor_id, action, comment, created_at)
SELECT 30000 + (idx - 1) * 4 + 1, approval_id, drafter_id, 'DRAFT', NULL, drafted_at
FROM demo_doc
ON CONFLICT (history_id) DO NOTHING;

INSERT INTO approval_history (history_id, approval_id, actor_id, action, comment, created_at)
SELECT 30000 + (idx - 1) * 4 + 2, approval_id, drafter_id, 'SUBMIT', NULL, submitted_at
FROM demo_doc
WHERE status IN ('REJECTED', 'PENDING', 'APPROVED')
ON CONFLICT (history_id) DO NOTHING;

INSERT INTO approval_history (history_id, approval_id, actor_id, action, comment, created_at)
SELECT 30000 + (idx - 1) * 4 + 2, approval_id, drafter_id, 'CANCEL', NULL, completed_at
FROM demo_doc
WHERE status = 'CANCELED'
ON CONFLICT (history_id) DO NOTHING;

INSERT INTO approval_history (history_id, approval_id, actor_id, action, comment, created_at)
SELECT 30000 + (idx - 1) * 4 + 3, approval_id, approver1_id, 'REJECT', reason_text, submitted_at + interval '2 hours'
FROM demo_doc
WHERE status = 'REJECTED'
ON CONFLICT (history_id) DO NOTHING;

INSERT INTO approval_history (history_id, approval_id, actor_id, action, comment, created_at)
SELECT 30000 + (idx - 1) * 4 + 3, approval_id, approver1_id, 'APPROVE', NULL, submitted_at + interval '2 hours'
FROM demo_doc
WHERE status = 'APPROVED'
ON CONFLICT (history_id) DO NOTHING;

INSERT INTO approval_history (history_id, approval_id, actor_id, action, comment, created_at)
SELECT 30000 + (idx - 1) * 4 + 4, approval_id, approver2_id, 'APPROVE', NULL, completed_at
FROM demo_doc
WHERE status = 'APPROVED'
ON CONFLICT (history_id) DO NOTHING;

-- =====================================================================
-- 5) approval_reject_history - 반려 40건, 부서·유형별로 편중된 reason_category.
--    ★ 사전점검이 읽는 표. reason_text 는 여기 있지만(사람이 볼 화면용),
--    사전점검은 reason_text 원문을 프롬프트에 넣지 않고 reason_category 와 빈도만
--    쓴다 - 개인정보가 프롬프트로 흘러드는 것을 막기 위해서다.
-- =====================================================================
INSERT INTO approval_reject_history (id, approval_id, doc_type, dept_id, rejector_id, reason_category, reason_text, rejected_at)
SELECT 40000 + idx, approval_id, doc_type, dept_id, approver1_id, reason_category, reason_text, submitted_at + interval '2 hours'
FROM demo_doc
WHERE status = 'REJECTED'
ON CONFLICT (id) DO NOTHING;

-- =====================================================================
-- 6) 시퀀스를 시드 최대값으로 밀어 둔다 (11-seed-org.sql / 21-seed-approval.sql 과 같은 관례).
--    이미 테스트 실행으로 시퀀스가 이 대역을 넘어섰다면 GREATEST 로 뒤로 밀리지 않게 한다.
-- =====================================================================
SELECT setval(pg_get_serial_sequence('approval_doc', 'approval_id'),
              GREATEST((SELECT MAX(approval_id) FROM approval_doc),
                       (SELECT last_value FROM approval_doc_approval_id_seq)));
SELECT setval(pg_get_serial_sequence('approval_line', 'line_id'),
              GREATEST((SELECT MAX(line_id) FROM approval_line),
                       (SELECT last_value FROM approval_line_line_id_seq)));
SELECT setval(pg_get_serial_sequence('approval_history', 'history_id'),
              GREATEST((SELECT MAX(history_id) FROM approval_history),
                       (SELECT last_value FROM approval_history_history_id_seq)));
SELECT setval(pg_get_serial_sequence('approval_reject_history', 'id'),
              GREATEST((SELECT MAX(id) FROM approval_reject_history),
                       (SELECT last_value FROM approval_reject_history_id_seq)));

DROP TABLE IF EXISTS demo_doc;

-- =====================================================================
-- 7) 근태 (2026-01-01 ~ 오늘), 평일만, 공휴일 제외, 전 직원 20명.
--    attendance 는 명시적 PK 를 쓰지 않는다 - UNIQUE(emp_id, work_date) 가 이미
--    자연키라서 그것으로 충돌을 판정하는 편이 더 단순하다.
--    여기서는 출근/지각/조퇴/결근만 만든다. 연차는 바로 아래 8) 에서 얹는다.
-- =====================================================================
INSERT INTO attendance (emp_id, work_date, check_in, check_out, work_minutes, overtime_minutes, status, note)
SELECT
    e.emp_id,
    d.work_date,
    CASE mm.m
        WHEN 0 THEN NULL
        WHEN 1 THEN d.work_date + time '09:22:00'
        WHEN 2 THEN d.work_date + time '09:18:00'
        ELSE        d.work_date + (time '08:55:00' + ((e.emp_id % 10) || ' minutes')::interval)
    END                                                                  AS check_in,
    CASE mm.m
        WHEN 0 THEN NULL
        WHEN 3 THEN d.work_date + time '17:00:00'
        ELSE        d.work_date + (time '18:00:00' + ((e.emp_id % 15) || ' minutes')::interval)
    END                                                                  AS check_out,
    CASE mm.m WHEN 0 THEN 0 WHEN 1 THEN 460 WHEN 2 THEN 460 WHEN 3 THEN 420 ELSE 480 END
                                                                         AS work_minutes,
    CASE WHEN mm.m = 4 AND (e.emp_id + extract(day FROM d.work_date)::int) % 7 = 0 THEN 60 ELSE 0 END
                                                                         AS overtime_minutes,
    CASE mm.m WHEN 0 THEN 'ABSENT' WHEN 1 THEN 'LATE' WHEN 2 THEN 'LATE' WHEN 3 THEN 'EARLY_LEAVE' ELSE 'NORMAL' END
                                                                         AS status,
    CASE mm.m WHEN 0 THEN '결근' WHEN 1 THEN '지각' WHEN 2 THEN '지각' WHEN 3 THEN '조퇴' ELSE NULL END
                                                                         AS note
FROM employee e
CROSS JOIN LATERAL (
    SELECT gs::date AS work_date
    FROM generate_series(date '2026-01-01', CURRENT_DATE, interval '1 day') AS gs
    WHERE extract(dow FROM gs) NOT IN (0, 6)
      AND gs::date NOT IN (SELECT holiday_date FROM holiday)
) d
CROSS JOIN LATERAL (
    SELECT (e.emp_id + (d.work_date - date '2026-01-01')) % 20 AS m
) mm
ON CONFLICT (emp_id, work_date) DO NOTHING;

-- =====================================================================
-- 8) 연차 사용을 근태에 반영한다.
--
-- ★ 핵심 불변식: 각 사원의 연차 근태 일수 합계 = leave_balance.used_days
--
--   used_days 를 새로 정하지 않고, **이미 시드된 used_days 만큼 정확히**
--   연차 근태를 만든다. 방향이 중요하다:
--     - 근태만 넣고 used_days 를 안 맞추면 화면이 어긋난다. 연차 맥락 패널은
--       "사용 5.0일"이라 하는데 달력에는 12일이 연차로 찍히는 식이다.
--     - 반대로 used_days 를 근태에서 다시 계산하면 이미 그 값을 단정하는
--       테스트들(LeaveBalanceMapperIT·LeaveContextServiceIT 의 곽수빈 5.0,
--       ApprovalServiceLeaveApplyRollbackIT 의 신동혁 10.0)이 깨진다.
--   used_days 를 기준으로 삼으면 그 테스트들이 기대하는 값이 임의의 숫자가
--   아니라 **실제로 참인 값**이 된다.
--
--   반차(0.5)는 used_days 의 소수부로 표현된다 - 5.5 면 종일 5 + 반차 1 이다.
--
-- ★ INSERT 가 아니라 UPDATE 인 이유: 위 7) 이 모든 영업일에 이미 행을 만들었다.
--   같은 날짜에 또 넣으면 UNIQUE(emp_id, work_date) 위반이다.
--
-- ★ leave_usage / leave_request / 연차 결재 문서는 만들지 않는다 - 파일 맨 위에서
-- LEAVE 문서를 시드에 넣지 않기로 한 것과 같은 이유다(의 트랜잭션
--   규약을 손으로 우회하게 된다). 이 데이터는 "시스템 도입 전에 이미 쓴 연차"로
--   읽으면 되고, 앞으로 생기는 연차는 결재 승인 경로가 만든다.
--
-- ★ 여름에 40% 정도를 몰아준다 - 실제 패턴이 그렇고 데모에서 월을 넘겨볼 때
--   눈에 띈다. 다만 **전부 여름에 몰면 오히려 비현실적**이라 나머지 60% 는
--   1~6월에 흩뿌린다. (처음에 여름 가중치를 세게 줬더니 54일 전부가 7·8월에
--   들어가 버렸다 - 연중 한 번도 연차를 안 쓰다가 여름에만 몰아 쓰는 사람은 없다.)
-- =====================================================================

-- 8-1) 재실행 대비 초기화. 결재 승인 경로가 만든 연차(leave_usage 에 근거가 있는 것)는
--      건드리지 않고, 이 시드가 만든 것만 되돌린다. 그래야 아래 UPDATE 를 다시 돌려도
--      연차가 누적되지 않고 used_days 와의 불변식이 유지된다.
UPDATE attendance a
SET status           = 'NORMAL',
    check_in         = a.work_date + (time '08:55:00' + ((a.emp_id % 10) || ' minutes')::interval),
    check_out        = a.work_date + (time '18:00:00' + ((a.emp_id % 15) || ' minutes')::interval),
    work_minutes     = 480,
    overtime_minutes = 0,
    note             = NULL
WHERE a.status IN ('LEAVE', 'HALF_LEAVE')
  AND NOT EXISTS (
      SELECT 1 FROM leave_usage lu
       WHERE lu.emp_id = a.emp_id
         AND a.work_date BETWEEN lu.start_date AND lu.end_date
  );

-- 8-2) used_days 만큼 연차를 배치한다.
WITH biz AS (
    SELECT gs::date AS work_date,
           row_number() OVER (ORDER BY gs) AS n
    FROM generate_series(date '2026-01-01', CURRENT_DATE, interval '1 day') AS gs
    WHERE extract(dow FROM gs) NOT IN (0, 6)
      AND gs::date NOT IN (SELECT holiday_date FROM holiday)
),
plan AS (
    SELECT emp_id,
           floor(used_days)::int                                        AS full_days,
           CASE WHEN used_days > floor(used_days) THEN 1 ELSE 0 END     AS half_days,
           GREATEST(1, round(used_days * 0.4)::int)                     AS summer_quota
    FROM leave_balance
    WHERE year = 2026
      AND used_days > 0
),
picked AS (
    SELECT u.emp_id,
           u.work_date,
           u.full_days,
           row_number() OVER (PARTITION BY u.emp_id ORDER BY u.work_date) AS pick_no
    FROM (
        -- 여름 몫 (7월 이후)
        SELECT p.emp_id, s.work_date, p.full_days
        FROM plan p
        CROSS JOIN LATERAL (
            SELECT b.work_date
            FROM biz b
            WHERE b.work_date >= date '2026-07-01'
            ORDER BY ((b.n * 7919 + p.emp_id * 104729) % 100000)
            LIMIT LEAST(p.summer_quota, p.full_days + p.half_days)
        ) s
        UNION ALL
        -- 나머지 (1~6월)
        SELECT p.emp_id, r.work_date, p.full_days
        FROM plan p
        CROSS JOIN LATERAL (
            SELECT b.work_date
            FROM biz b
            WHERE b.work_date < date '2026-07-01'
            ORDER BY ((b.n * 6971 + p.emp_id * 92831) % 100000)
            LIMIT GREATEST(0, p.full_days + p.half_days
                              - LEAST(p.summer_quota, p.full_days + p.half_days))
        ) r
    ) u
)
UPDATE attendance a
SET status           = CASE WHEN pk.pick_no <= pk.full_days THEN 'LEAVE' ELSE 'HALF_LEAVE' END,
    -- 종일 연차는 출퇴근 기록이 없다(DefaultLeaveApplyService.apply 와 같은 모양).
    -- 반차는 반나절 근무하므로 출퇴근이 남는다.
    check_in         = CASE WHEN pk.pick_no <= pk.full_days THEN NULL
                            ELSE a.work_date + time '09:00:00' END,
    check_out        = CASE WHEN pk.pick_no <= pk.full_days THEN NULL
                            ELSE a.work_date + time '14:00:00' END,
    work_minutes     = CASE WHEN pk.pick_no <= pk.full_days THEN 0 ELSE 240 END,
    overtime_minutes = 0,
    note             = CASE WHEN pk.pick_no <= pk.full_days THEN '연차' ELSE '반차' END
FROM picked pk
WHERE a.emp_id = pk.emp_id
  AND a.work_date = pk.work_date;
