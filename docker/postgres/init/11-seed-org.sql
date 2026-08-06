-- 부서: 대표이사실 → 본부 2 → 팀 4 (3단 계층)
INSERT INTO department (dept_id, parent_dept_id, dept_name, dept_code, sort_order) VALUES
    (1, NULL, '대표이사실',   'CEO',      1),
    (2, 1,    '경영지원본부', 'HQ_MGMT',  1),
    (3, 1,    '사업본부',     'HQ_BIZ',   2),
    (4, 2,    '인사팀',       'TEAM_HR',  1),
    (5, 2,    '재무팀',       'TEAM_FIN', 2),
    (6, 3,    '마케팅팀',     'TEAM_MKT', 1),
    (7, 3,    '개발팀',       'TEAM_DEV', 2);

INSERT INTO position (position_id, position_name, position_level) VALUES
    (1, '사원', 1),
    (2, '대리', 2),
    (3, '과장', 3),
    (4, '차장', 4),
    (5, '부장', 5),
    (6, '이사', 6);

-- 사원 20명.
-- 비밀번호는 전원 'flowmate1!' 이다. pgcrypto 의 bf 방식은 $2a$ 형식을 만들고
-- Spring Security 의 BCryptPasswordEncoder 가 이를 그대로 검증한다 (교차 검증 완료).
-- ASCII 비밀번호만 쓴다 (pgcrypto bf 의 8bit 문자 처리 이슈 회피).
--
-- ★ 부서마다 position_level 최고값이 정확히 1명씩만 나오도록 배치했다.
--   이후 결재선 정책이 "같은 부서의 최고 직급" 으로 부서장을 판정하기 때문이다.
--   이 배치를 바꾸면 결재선이 비결정적으로 바뀐다.
INSERT INTO employee (emp_id, emp_no, emp_name, dept_id, position_id, email, hire_date, password_hash, role) VALUES
    ( 1, '2015001', '정도현', 1, 6, 'dohyun.jeong@flowmate.co.kr',  '2015-03-02', crypt('flowmate1!', gen_salt('bf', 10)), 'ADMIN'),
    ( 2, '2016001', '김성일', 2, 5, 'sungil.kim@flowmate.co.kr',    '2016-01-04', crypt('flowmate1!', gen_salt('bf', 10)), 'MANAGER'),
    ( 3, '2016002', '박현주', 3, 5, 'hyunju.park@flowmate.co.kr',   '2016-03-01', crypt('flowmate1!', gen_salt('bf', 10)), 'MANAGER'),
    ( 4, '2017001', '최민석', 4, 4, 'minseok.choi@flowmate.co.kr',  '2017-02-01', crypt('flowmate1!', gen_salt('bf', 10)), 'ADMIN'),
    ( 5, '2018001', '한지우', 4, 2, 'jiwoo.han@flowmate.co.kr',     '2018-07-02', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    ( 6, '2020001', '서다인', 4, 1, 'dain.seo@flowmate.co.kr',      '2020-03-02', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    ( 7, '2017002', '오세훈', 5, 3, 'sehoon.oh@flowmate.co.kr',     '2017-09-01', crypt('flowmate1!', gen_salt('bf', 10)), 'MANAGER'),
    ( 8, '2019001', '임채원', 5, 2, 'chaewon.lim@flowmate.co.kr',   '2019-04-01', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    ( 9, '2021001', '강태윤', 5, 1, 'taeyoon.kang@flowmate.co.kr',  '2021-01-04', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (10, '2016003', '윤서영', 6, 4, 'seoyoung.yoon@flowmate.co.kr', '2016-06-01', crypt('flowmate1!', gen_salt('bf', 10)), 'MANAGER'),
    (11, '2018002', '이준호', 6, 2, 'junho.lee@flowmate.co.kr',     '2018-03-05', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (12, '2020002', '조미래', 6, 1, 'mirae.jo@flowmate.co.kr',      '2020-09-01', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (13, '2021002', '배성우', 6, 1, 'sungwoo.bae@flowmate.co.kr',   '2021-07-01', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (14, '2016004', '신동혁', 7, 3, 'donghyuk.shin@flowmate.co.kr', '2016-11-01', crypt('flowmate1!', gen_salt('bf', 10)), 'MANAGER'),
    (15, '2017003', '문가영', 7, 2, 'gayoung.moon@flowmate.co.kr',  '2017-05-02', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (16, '2018003', '황준영', 7, 2, 'junyoung.hwang@flowmate.co.kr','2018-08-01', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (17, '2019002', '노은지', 7, 2, 'eunji.no@flowmate.co.kr',      '2019-10-01', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (18, '2020003', '곽수빈', 7, 1, 'subin.kwak@flowmate.co.kr',    '2020-03-02', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (19, '2021003', '유하람', 7, 1, 'haram.yu@flowmate.co.kr',      '2021-03-01', crypt('flowmate1!', gen_salt('bf', 10)), 'USER'),
    (20, '2022001', '심재현', 7, 1, 'jaehyun.shim@flowmate.co.kr',  '2022-01-03', crypt('flowmate1!', gen_salt('bf', 10)), 'USER');

-- ★ BIGSERIAL 시퀀스를 시드 최대값으로 밀어 둔다.
--   PK 를 명시해서 INSERT 하면 시퀀스가 여전히 1 에 머물러 있어
--   다음 INSERT 가 dept_id=1 을 시도해 PK 충돌이 난다.
--   pg_get_serial_sequence() 를 쓰면 시퀀스 이름을 손으로 적지 않아도 된다.
SELECT setval(pg_get_serial_sequence('department', 'dept_id'),     (SELECT MAX(dept_id)     FROM department));
SELECT setval(pg_get_serial_sequence('position',   'position_id'), (SELECT MAX(position_id) FROM position));
SELECT setval(pg_get_serial_sequence('employee',   'emp_id'),      (SELECT MAX(emp_id)      FROM employee));
