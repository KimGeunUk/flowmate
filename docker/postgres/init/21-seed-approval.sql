-- 데모용 결재 문서 6건. 상태별로 하나씩 두어 내 결재함의 탭 4종을 바로 확인할 수 있게 한다.
-- 기안자는 곽수빈(18, 개발팀 사원), 결재선은 신동혁(14, 과장) → 박현주(3, 부장).
INSERT INTO approval_doc
    (approval_id, doc_no, doc_type, title, content, drafter_id, dept_id, amount, status, current_step, drafted_at, submitted_at, completed_at) VALUES
    (1, 'EXP-2026-0001', 'EXPENSE',  '3월 출장비 정산',       '부산 지사 방문 출장비를 정산합니다.', 18, 7,  540000, 'DRAFT',    0, '2026-03-02 09:10:00', NULL,                  NULL),
    (2, 'EXP-2026-0002', 'EXPENSE',  '거래처 미팅 식대',       '○○물류 담당자와의 오찬 비용입니다.',  18, 7,  120000, 'PENDING',  1, '2026-03-05 10:00:00', '2026-03-05 10:05:00', NULL),
    (3, 'PUR-2026-0001', 'PURCHASE', '개발용 모니터 4대 구매', '신규 입사자 4명 장비 구매 요청입니다.', 18, 7, 1600000, 'PENDING',  2, '2026-03-06 11:00:00', '2026-03-06 11:02:00', NULL),
    (4, 'EXP-2026-0003', 'EXPENSE',  '2월 교통비 정산',       '2월 외근 교통비입니다.',              18, 7,   88000, 'APPROVED', 2, '2026-02-28 14:00:00', '2026-02-28 14:01:00', '2026-03-02 09:00:00'),
    (5, 'PUR-2026-0002', 'PURCHASE', '사무용 의자 교체',       '노후 의자 교체 요청입니다.',           18, 7,  900000, 'REJECTED', 1, '2026-03-01 15:00:00', '2026-03-01 15:01:00', '2026-03-01 17:30:00'),
    (6, 'GEN-2026-0001', 'GENERAL',  '팀 워크숍 계획 공유',    '4월 팀 워크숍 일정과 장소 초안입니다.', 18, 7,       0, 'CANCELED', 0, '2026-03-03 09:00:00', NULL,                  '2026-03-03 09:20:00');

INSERT INTO approval_line (approval_id, step_no, approver_id, line_type, status, comment, processed_at) VALUES
    (2, 1, 14, 'APPROVAL', 'CURRENT',  NULL, NULL),
    (2, 2,  3, 'APPROVAL', 'WAITING',  NULL, NULL),
    (3, 1, 14, 'APPROVAL', 'APPROVED', '구매 필요 확인했습니다.', '2026-03-06 13:00:00'),
    (3, 2,  3, 'APPROVAL', 'CURRENT',  NULL, NULL),
    (4, 1, 14, 'APPROVAL', 'APPROVED', NULL, '2026-03-01 09:00:00'),
    (4, 2,  3, 'APPROVAL', 'APPROVED', '확인했습니다.', '2026-03-02 09:00:00'),
    (5, 1, 14, 'APPROVAL', 'REJECTED', '견적서를 첨부해 주세요.', '2026-03-01 17:30:00'),
    (5, 2,  3, 'APPROVAL', 'SKIPPED',  NULL, NULL);

INSERT INTO approval_history (approval_id, actor_id, action, comment, created_at) VALUES
    (1, 18, 'DRAFT',   NULL, '2026-03-02 09:10:00'),
    (2, 18, 'DRAFT',   NULL, '2026-03-05 10:00:00'),
    (2, 18, 'SUBMIT',  NULL, '2026-03-05 10:05:00'),
    (3, 18, 'DRAFT',   NULL, '2026-03-06 11:00:00'),
    (3, 18, 'SUBMIT',  NULL, '2026-03-06 11:02:00'),
    (3, 14, 'APPROVE', '구매 필요 확인했습니다.', '2026-03-06 13:00:00'),
    (4, 18, 'DRAFT',   NULL, '2026-02-28 14:00:00'),
    (4, 18, 'SUBMIT',  NULL, '2026-02-28 14:01:00'),
    (4, 14, 'APPROVE', NULL, '2026-03-01 09:00:00'),
    (4,  3, 'APPROVE', '확인했습니다.', '2026-03-02 09:00:00'),
    (5, 18, 'DRAFT',   NULL, '2026-03-01 15:00:00'),
    (5, 18, 'SUBMIT',  NULL, '2026-03-01 15:01:00'),
    (5, 14, 'REJECT',  '견적서를 첨부해 주세요.', '2026-03-01 17:30:00'),
    (6, 18, 'DRAFT',   NULL, '2026-03-03 09:00:00'),
    (6, 18, 'CANCEL',  NULL, '2026-03-03 09:20:00');

-- 반려 이력. Phase 5 사전점검이 이 표를 읽는다.
INSERT INTO approval_reject_history (approval_id, doc_type, dept_id, rejector_id, reason_category, reason_text, rejected_at) VALUES
    (5, 'PURCHASE', 7, 14, 'MISSING_EVIDENCE', '견적서를 첨부해 주세요.', '2026-03-01 17:30:00');

SELECT setval(pg_get_serial_sequence('approval_doc', 'approval_id'), (SELECT MAX(approval_id) FROM approval_doc));
SELECT setval(pg_get_serial_sequence('approval_line', 'line_id'), (SELECT MAX(line_id) FROM approval_line));
SELECT setval(pg_get_serial_sequence('approval_history', 'history_id'), (SELECT MAX(history_id) FROM approval_history));
SELECT setval(pg_get_serial_sequence('approval_reject_history', 'id'), (SELECT MAX(id) FROM approval_reject_history));
