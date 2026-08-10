# PostgreSQL → Oracle 문법 대응표

FlowMate 는 PostgreSQL 16 으로 개발하지만, 목표 환경(그룹웨어 커스터마이징)은 Oracle 이 표준이다.
**이 문서는 사후에 작성하지 않는다.** PostgreSQL 전용 문법을 쓸 때마다 그 자리에서 한 줄 추가한다.

## 1. 공통 대응

| PostgreSQL | Oracle |
|---|---|
| `BIGSERIAL` | `NUMBER` + `SEQUENCE.NEXTVAL` |
| `WITH RECURSIVE` | `CONNECT BY PRIOR` (또는 11gR2+ 의 재귀 WITH) |
| `LIMIT n OFFSET m` | `OFFSET m ROWS FETCH NEXT n ROWS ONLY` (12c+) / `ROWNUM` 서브쿼리 (11g 이하) |
| `COALESCE` | `NVL` (`COALESCE` 도 동작) |
| `CURRENT_TIMESTAMP` | `SYSTIMESTAMP` |
| `TEXT` | `CLOB` |
| `||` 문자열 결합 | 동일 |
| `CAST(x AS VARCHAR(n))` | `TO_CHAR(x)` (`CAST` 도 되지만 `VARCHAR2` 를 쓴다) |
| `VARCHAR(n)` | `VARCHAR2(n)` |
| `CHAR(1)` | 동일 |
| `NUMERIC(p,s)` | `NUMBER(p,s)` |
| `COUNT(*) FILTER (WHERE ...)` | `COUNT(CASE WHEN ... THEN 1 END)` |
| `pgcrypto` 의 `crypt()` / `gen_salt()` | 대응 없음. 해시를 애플리케이션에서 만들어 INSERT 한다 |
| `pg_get_serial_sequence()` | 대응 없음. 시퀀스 이름을 직접 적는다 |

## 2. 실제 사용 위치별 대응

### 2.1 조직도 계층 조회 — `mapper/org/DepartmentMapper.xml#findDeptTree`

PostgreSQL:

```sql
WITH RECURSIVE dept_tree AS (
    SELECT d.dept_id, d.parent_dept_id, d.dept_name, d.dept_code, 1 AS depth,
           LPAD(CAST(d.sort_order AS VARCHAR(5)), 5, '0') || '-' ||
           LPAD(CAST(d.dept_id AS VARCHAR(10)), 10, '0') AS sort_path
      FROM department d
     WHERE d.parent_dept_id IS NULL AND d.use_yn = 'Y'
    UNION ALL
    SELECT c.dept_id, c.parent_dept_id, c.dept_name, c.dept_code, t.depth + 1,
           t.sort_path || '>' || LPAD(...) || '-' || LPAD(...)
      FROM department c JOIN dept_tree t ON c.parent_dept_id = t.dept_id
     WHERE c.use_yn = 'Y'
)
SELECT ... FROM dept_tree t ORDER BY t.sort_path
```

Oracle:

```sql
SELECT d.dept_id, d.parent_dept_id, d.dept_name, d.dept_code,
       LEVEL AS depth
  FROM department d
 START WITH d.parent_dept_id IS NULL
CONNECT BY PRIOR d.dept_id = d.parent_dept_id
       AND d.use_yn = 'Y'
 ORDER SIBLINGS BY d.sort_order, d.dept_id
```

**차이가 나는 지점 세 가지:**

1. `depth` → Oracle 은 의사열 `LEVEL` 을 쓴다.
2. **`sort_path` 와 `LPAD` 조립이 아예 필요 없다.** Oracle 은 `ORDER SIBLINGS BY` 로
   형제 정렬을 문법으로 지원한다. PostgreSQL 에 그 구문이 없어서 문자열 경로를 만들어
   정렬하는 것이므로, Oracle 로 옮기면 쿼리가 오히려 짧아진다.
3. **`use_yn` 조건의 위치가 의미를 바꾼다.** PostgreSQL CTE 는 anchor 와 recursive term
   양쪽에 조건을 두어 "사용하지 않는 부서의 하위까지 제외" 를 얻는다.
   Oracle 에서 같은 동작을 원하면 조건을 `WHERE` 가 아니라 **`CONNECT BY` 절에** 넣어야 한다.
   `WHERE` 에 두면 해당 노드만 빠지고 하위는 남는다.

### 2.2 사원 목록 페이징 — `mapper/org/EmployeeMapper.xml#search`

PostgreSQL:

```sql
 ORDER BY p.position_level DESC, e.hire_date, e.emp_id
 LIMIT #{limit} OFFSET #{offset}
```

Oracle 12c 이상:

```sql
 ORDER BY p.position_level DESC, e.hire_date, e.emp_id
 OFFSET #{offset} ROWS FETCH NEXT #{limit} ROWS ONLY
```

Oracle 11g 이하:

```sql
SELECT * FROM (
    SELECT inner_q.*, ROWNUM AS rn FROM (
        SELECT ... ORDER BY p.position_level DESC, e.hire_date, e.emp_id
    ) inner_q
     WHERE ROWNUM <= #{offset} + #{limit}
)
 WHERE rn > #{offset}
```

**주의:** 11g 방식은 `ROWNUM` 을 정렬 뒤에 매기기 위해 인라인 뷰가 두 겹 필요하다.
한 겹으로 쓰면 정렬 전에 번호가 매겨져 엉뚱한 행이 나온다.

### 2.3 부분 일치 검색과 와일드카드 이스케이프 — 같은 파일 `searchWhere`

```sql
e.emp_name LIKE '%' || #{keywordEscaped} || '%' ESCAPE '\'
```

`||` 결합과 `ESCAPE` 절은 Oracle 에서 동일하게 동작한다. 변환이 필요 없다.

주의할 차이 두 가지:

1. Oracle 은 빈 문자열을 `NULL` 로 취급하므로 `#{keywordEscaped}` 가 `''` 이면
   조건 전체가 `NULL` 이 되어 아무 행도 걸리지 않는다. FlowMate 는
   `EmployeeSearchCond` 의 setter 가 빈 문자열을 `null` 로 바꾸고 매퍼가
   `<if test="keyword != null">` 로 감싸므로 이 경로에 들어가지 않는다.
2. 이스케이프 문자로 백슬래시를 쓰면 Oracle 에서도 `ESCAPE '\'` 를 명시해야 한다.
   기본 이스케이프 문자는 없다.

### 2.4 부서장 체인 조회 (상향 재귀) — `mapper/org/DepartmentMapper.xml#findDeptHeadChain`

PostgreSQL 은 `WITH RECURSIVE` 의 JOIN 방향을 뒤집어 위로 올라간다.

```sql
WITH RECURSIVE up_chain AS (
    SELECT d.dept_id, d.parent_dept_id, 1 AS depth
      FROM department d WHERE d.dept_id = #{deptId} AND d.use_yn = 'Y'
    UNION ALL
    SELECT p.dept_id, p.parent_dept_id, c.depth + 1
      FROM department p JOIN up_chain c ON p.dept_id = c.parent_dept_id
     WHERE p.use_yn = 'Y'
)
```

Oracle 은 `CONNECT BY` 의 `PRIOR` 위치만 바꾸면 된다.

```sql
SELECT d.dept_id, d.parent_dept_id, LEVEL AS depth
  FROM department d
 START WITH d.dept_id = #{deptId}
CONNECT BY PRIOR d.parent_dept_id = d.dept_id
       AND d.use_yn = 'Y'
```

**차이:** 하향은 `PRIOR d.dept_id = d.parent_dept_id`, 상향은 `PRIOR d.parent_dept_id = d.dept_id` 다.
`PRIOR` 가 어느 쪽에 붙는지가 방향을 결정한다.

`ROW_NUMBER() OVER (PARTITION BY ... ORDER BY ...)` 는 Oracle 에서 **동일하게 동작한다.** 변환이 필요 없다.

### 2.5 동시 결재 방지 행 잠금 — `mapper/approval/ApprovalDocMapper.xml#findByIdForUpdate`

```sql
SELECT ... FROM approval_doc a ... FOR UPDATE OF a
```

Oracle 에서도 `FOR UPDATE OF <별칭 또는 컬럼>` 이 동작한다. 다만 두 가지가 다르다.

1. Oracle 의 `FOR UPDATE OF` 는 **컬럼**을 적는 것이 정석이다 (`FOR UPDATE OF a.status`).
   별칭만 적는 PostgreSQL 문법이 Oracle 에서도 통하지만, 이식할 때 컬럼을 명시하는 편이 안전하다.
2. 대기 정책이 다르다. PostgreSQL 은 기본이 무한 대기이고 `NOWAIT` / `SKIP LOCKED` 를 붙일 수 있다.
   Oracle 도 같은 옵션이 있으나 `WAIT n` 초 지정이 추가로 가능하다.

FlowMate 는 결재 문서 한 건에 대한 동시 클릭만 막으면 되므로 기본 대기로 충분하다.

### 2.6 문서번호 채번 직렬화 — `mapper/approval/ApprovalDocMapper.xml#lockDocNoSeq`

PostgreSQL:

```sql
SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtext('EXP-2026'))) locked
```

Oracle 에는 `pg_advisory_xact_lock` 이 없다. 두 가지 대안이 있다.

1. **채번 테이블 + `SELECT ... FOR UPDATE`** (이식성이 가장 높다)

   ```sql
   SELECT next_seq FROM doc_no_seq
    WHERE prefix = 'EXP' AND seq_year = 2026
      FOR UPDATE;
   ```

   행 잠금이 트랜잭션 종료까지 유지되므로 자문 잠금과 같은 효과를 낸다.
   PostgreSQL 에서도 동일하게 동작하므로, 이식성을 최우선한다면 처음부터 이 방식을 쓸 수 있다.

2. **`DBMS_LOCK.REQUEST`** — 자문 잠금에 가장 가깝지만 패키지 실행 권한이 필요하고
   잠금 핸들을 직접 할당해야 해서 운영 부담이 크다.

**왜 재시도가 아니라 잠금인가:** PostgreSQL 은 제약 위반이 나면 트랜잭션을 중단 상태로 만들어
같은 트랜잭션의 이후 쿼리가 전부 `25P02` 로 실패한다. Oracle 은 문 단위 롤백이라 제약 위반 후에도
같은 트랜잭션을 계속 쓸 수 있다 — **두 DB 의 동작이 다르므로 재시도 방식은 이식되지 않는다.**
잠금 방식은 양쪽에서 같은 의미를 갖는다.

### 2.7 AI 게이트웨이 스키마 (`ai_result_cache`, `ai_call_log`) — Oracle 특이사항 없음

두 테이블 모두 `BIGSERIAL`(§1 대응)과 표준 `VARCHAR`/`TEXT`/`TIMESTAMP`/`CHAR(1)` 컬럼,
평범한 `CREATE INDEX` 뿐이다. 재귀 쿼리·자문 잠금·`LIMIT`/`FETCH` 같은 PostgreSQL 전용
문법을 전혀 쓰지 않으므로 Oracle 이식 시 `BIGSERIAL → NUMBER + SEQUENCE` 치환 외에는
손댈 곳이 없다.

### 2.8 근태 UPSERT — `mapper/attendance/AttendanceMapper.xml#upsertForLeave`

연차를 근태에 반영할 때 그날 `attendance` 행이 이미 있을 수 있다(계획서 4 D6 — 오전에
출근을 찍고 오후에 반차를 낸 경우). PostgreSQL:

```sql
INSERT INTO attendance (emp_id, work_date, status, note)
VALUES (#{empId}, #{workDate}, #{status}, #{note})
ON CONFLICT (emp_id, work_date)
DO UPDATE SET status = EXCLUDED.status, note = EXCLUDED.note
```

Oracle 은 `ON CONFLICT` 가 없다 → `MERGE INTO`.

```sql
MERGE INTO attendance a
USING (SELECT #{empId} AS emp_id, #{workDate} AS work_date FROM dual) src
   ON (a.emp_id = src.emp_id AND a.work_date = src.work_date)
 WHEN MATCHED THEN
      UPDATE SET a.status = #{status}, a.note = #{note}
 WHEN NOT MATCHED THEN
      INSERT (emp_id, work_date, status, note)
      VALUES (#{empId}, #{workDate}, #{status}, #{note})
```

**차이가 나는 지점 두 가지:**

1. PostgreSQL 은 `UNIQUE(emp_id, work_date)` 제약을 직접 대상으로 지정한다
   (`ON CONFLICT (emp_id, work_date)`). Oracle 의 `MERGE INTO` 는 제약이 아니라
   `ON` 절의 조건식으로 매칭한다 — 두 컬럼이 그 제약과 일치해야 의미가 같아진다.
2. `EXCLUDED.status` (PostgreSQL 이 "이번에 넣으려던 값"을 가리키는 가상 테이블)에
   대응하는 것이 Oracle 에는 없다. 대신 `USING` 절의 소스 별칭(`src`)이나, 여기처럼
   바인드 변수를 `WHEN MATCHED`/`WHEN NOT MATCHED` 양쪽에 그대로 반복해 적는다.

여기서도 D2·D6 이 말한 원칙이 그대로 적용된다: 두 문법 모두 충돌을 예외로 만들지
않고 DB 안에서 해결하므로, 애플리케이션 쪽에서 제약 위반을 잡아 재시도하는 코드가
필요 없다 — PostgreSQL 트랜잭션이 이런 재시도를 어떻게 망가뜨리는지는 §2.6 참조.

**`SELECT ... FOR UPDATE` 는 두 DB 에서 동일하게 동작한다.** `leave_balance` 잠금
(`LeaveBalanceMapper.xml#findForUpdate`, 계획서 4 D3):

```sql
SELECT granted_days, used_days FROM leave_balance
 WHERE emp_id = #{empId} AND year = #{year}
   FOR UPDATE
```

이 문장은 PostgreSQL·Oracle 모두 같은 의미로 행 잠금을 건다 — §2.5(`approval_doc`
잠금)에서 이미 확인한 것과 같은 결론이다. 대기 정책의 세부(무한 대기 vs `WAIT n`)만
다를 뿐 기본 동작(잠금을 잡고 해제될 때까지 대기)은 동일하므로, 이 문장은 변환 없이
그대로 이식된다.

### 2.9 대량 데모 시드의 행 생성 — `docker/postgres/init/50-seed-demo.sql`

계획서 5 D6 이 정한 대로 문서 200건·반려 40건(유형별 편중)·근태 3개월을 애플리케이션
코드가 아니라 SQL 안에서 만든다. 두 가지 형태의 `generate_series` 를 쓴다.

**(a) 정수 범위 — 합성 행 개수만큼 반복.** 반려 이력을 부서·문서유형별로 편중시키는
부분(`rejected_dev_purchase` 등 CTE):

```sql
SELECT
    gs AS ord,
    (ARRAY[15,16,19,20]::bigint[])[1 + ((gs - 1) % 4)] AS drafter_id,
    (CASE WHEN gs <= 8 THEN 'MISSING_EVIDENCE' ELSE 'PROCEDURE_ERROR' END) AS reason_category
FROM generate_series(1, 12) AS gs
```

Oracle:

```sql
SELECT
    LEVEL AS ord,
    CASE MOD(LEVEL - 1, 4)
        WHEN 0 THEN 15 WHEN 1 THEN 16 WHEN 2 THEN 19 ELSE 20
    END AS drafter_id,
    CASE WHEN LEVEL <= 8 THEN 'MISSING_EVIDENCE' ELSE 'PROCEDURE_ERROR' END AS reason_category
  FROM dual
CONNECT BY LEVEL <= 12
```

**(b) 날짜 범위 + 간격 — 근태 3개월(영업일) 시드.** `attendance` 대량 삽입이 캘린더를
만드는 부분:

```sql
SELECT gs::date AS work_date
  FROM generate_series(date '2026-02-01', date '2026-04-30', interval '1 day') AS gs
 WHERE extract(dow FROM gs) NOT IN (0, 6)         -- 주말 제외
   AND gs::date NOT IN (SELECT holiday_date FROM holiday)
```

Oracle:

```sql
SELECT work_date FROM (
    SELECT DATE '2026-02-01' + (LEVEL - 1) AS work_date
      FROM dual
    CONNECT BY LEVEL <= (DATE '2026-04-30' - DATE '2026-02-01' + 1)
) d
 WHERE TO_CHAR(work_date, 'D') NOT IN ('1', '7')   -- NLS_TERRITORY 에 따라 요일 번호가 다르다 - 실제 이식 시 확인
   AND work_date NOT IN (SELECT holiday_date FROM holiday)
```

**차이가 나는 지점 세 가지:**

1. **행 소스 자체가 없다.** PostgreSQL 의 `generate_series` 는 함수 하나가 곧 테이블(행
   집합)이다. Oracle 에는 대응하는 테이블 함수가 없으므로 `FROM dual` + `CONNECT BY LEVEL`
   로 우회한다 - `dual` 은 그 자체로 1행이므로 `CONNECT BY` 가 그 1행을 `LEVEL` 번 복제하는
   식으로 동작한다.
2. **날짜 간격 계산 방식이 다르다.** PostgreSQL 은 `generate_series(시작, 끝, interval)`
   가 간격을 인자로 직접 받는다. Oracle 은 반복 횟수(`LEVEL`)만 셀 수 있으므로 "끝 - 시작"
   일수를 먼저 계산해 그 값을 `CONNECT BY LEVEL <=` 의 상한으로 넣고, 각 행의 날짜는
   `시작 + (LEVEL - 1)` 로 역산한다.
3. **요일 판정 함수가 다르다.** PostgreSQL 의 `extract(dow FROM x)` 는 항상 0(일)~6(토)의
   고정된 숫자를 돌려준다. Oracle 의 `TO_CHAR(x, 'D')` 는 세션의 `NLS_TERRITORY` 설정에
   따라 무엇을 1로 볼지 달라진다 - 이식할 때 그 자리에서 실제 값을 확인해야 한다(고정된
   상수로 가정하면 안 된다).

**`ON CONFLICT (emp_id, work_date) DO NOTHING`** (근태 삽입 마지막 줄)은 §2.8 이 이미
다룬 것과 같은 `MERGE INTO ... WHEN NOT MATCHED THEN INSERT`(이 경우 `WHEN MATCHED` 절은
아예 쓰지 않는다) 로 옮긴다.
