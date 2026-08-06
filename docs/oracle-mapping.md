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

### 2.2 사원 목록 페이징 — 다음 Task 에서 추가한다
