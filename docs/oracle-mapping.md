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
