# Phase 4 실행 계획 — 근태 코어와 결재 연동

> 이 계획서는 설계서 §5.3~5.4(연차·근태 스키마), §6.3(근태관리), §9 Phase 4를 실행 단위로 옮긴 것이다.
> 로드맵: [2026-08-05-flowmate-roadmap.md](2026-08-05-flowmate-roadmap.md) — §3 규약이 전제다.
> 원본 설계서: [2026-08-05-flowmate-design.md](../specs/2026-08-05-flowmate-design.md)
> 작성일: 2026-08-08 · 분량: 3.0일 · 태그: `phase-4-attendance`

---

## 시작 상태

`main` 이 `phase-3-ai-gateway` 태그 상태다.

- 단위 **81** · 통합 **61** · BUILD SUCCESS
- 시드 `depts=7 emps=20 docs=6 lines=8 hist=15 rejects=1 attach=0 cache=0 log=0`
- 전자결재 전 과정이 화면에서 동작하고, AI 게이트웨이가 조립되어 있다 (화면은 없음)
- `com.flowmate.attendance` 패키지는 **아직 없다**

## 이 Phase가 끝나면 무엇이 동작하는가

설계서 §6.3이 **"프로젝트의 척추"** 라고 부른 것이 여기서 선다.

- 출퇴근을 등록하면 근무시간·지각·연장근무가 판정된다
- 부서 월간 근태 현황이 조직도 계층을 따라 집계된다
- **연차 신청서를 승인하면 잔여 연차가 줄고 해당 일자의 근태가 '연차'로 바뀐다. 중간에 실패하면 전부 롤백된다**

마지막 줄이 이 Phase의 전부다. 나머지는 그것을 가능하게 하는 재료다.

## 왜 이것이 척추인가

포트폴리오로서 이 프로젝트가 증명해야 하는 것은 "화면을 만들 수 있다"가 아니다. 그건 아무나 한다. 증명해야 하는 것은 **두 모듈이 하나의 트랜잭션 안에서 정확히 맞물린다**는 것이고, 그룹웨어 커스터마이징 업무의 난이도는 정확히 거기에 있다.

"승인은 됐는데 연차가 안 깎였다"는 실제 그룹웨어에서 흔한 사고다. 설계서 §6.3이 Spring 이벤트를 거부하고 같은 트랜잭션의 직접 호출을 선택한 이유가 이것이다.

---

## 이 계획서가 전제하는 확정 사항

### D1. ★ 모듈 의존은 한 방향이다 — `approval` → `attendance`, 되돌아오지 않는다

설계서 §6.3의 코드는 이렇게 되어 있다:

```java
leaveApplyService.applyFromApproval(approvalId);   // approvalId 만 넘긴다
```

**이대로 쓰면 순환 의존이 된다.** `attendance` 가 `approvalId` 만 받으면 사원·기간·일수를 알아내려고 `leave_request` 를 읽어야 하는데, 그건 `approval` 의 테이블이고, 프로젝트 규약(설계서 §4.3)은 모듈 간 접근을 Service 인터페이스로 강제한다. 그러면 `attendance` → `approval` 호출이 생겨 양방향이 된다.

**확정: 값을 넘긴다.**

```java
public interface LeaveApplyService {
    void apply(LeaveApplyCommand command);
}
```

`LeaveApplyCommand` 가 `approvalId, empId, leaveType, startDate, endDate, days` 를 나른다. `approval` 이 자기 테이블(`leave_request`)을 읽어 명령을 만들고, `attendance` 는 받은 값만 쓴다.

- 의존이 한 방향이라 순환이 없다
- `attendance` 를 단위 테스트할 때 `approval` 을 전혀 몰라도 된다
- `leave_request` 의 소유권이 `approval` 에 남는다 — 설계서 §5.3이 그것을 "결재 문서의 유형별 확장"이라 부른 것과 일치한다

인터페이스와 구현은 둘 다 `com.flowmate.attendance.service` 에 둔다. `approval` 은 인터페이스만 본다.

### D2. ★★ PostgreSQL 트랜잭션 중단 — Phase 2가 남긴 경고를 여기서 지킨다

Phase 2 리뷰가 잡은 Critical 결함이 이것이었다. 문서번호 채번이 `DuplicateKeyException` 을 잡고 같은 트랜잭션에서 재시도했는데, **PostgreSQL 은 제약 위반이 나면 트랜잭션 전체를 중단 상태로 만들어** 이후 모든 쿼리가 `25P02` 로 죽는다. 재시도 코드가 정확히 그 상황에서만 작동하지 않았다.

Phase 4는 같은 함정에 **더 세게** 노출된다:

- `leave_usage` 에 `UNIQUE(approval_id)` 가 있다 (설계서 §5.4가 "중복 반영 방지 장치"라 부른 것)
- 그 제약을 "멱등성 확보"를 위해 잡고 싶어지는 것이 자연스럽다
- 그리고 이 코드는 **`ApprovalService.approve()` 의 트랜잭션 안에서** 돈다 — 터지면 승인까지 같이 죽는다

**확정: 제약 위반을 잡지 않는다. 먼저 조회해서 분기한다.**

```java
// 이미 반영됐는지 먼저 확인한다. 제약 위반을 잡아서 판단하지 않는다.
if (leaveUsageMapper.existsByApprovalId(approvalId)) {
    return;   // 이미 반영됨 — 조용히 통과
}
leaveUsageMapper.insert(usage);
```

`approve()` 가 이미 `findByIdForUpdate` 로 `approval_doc` 행을 잠근 상태이므로, 같은 문서를 두 트랜잭션이 동시에 통과할 수 없다. **조회 후 삽입이 이 잠금 안에서는 안전하다.** `UNIQUE` 제약은 그래도 남긴다 — 최후 방어선이고, 걸리면 예외가 밖으로 나가 전체가 롤백되는 것이 올바른 동작이다.

**Oracle 은 문 단위 롤백이라 동작이 다르다.** 그래서 잡고-재시도하는 코드는 이식되지 않는다. 조회-후-분기는 양쪽에서 같은 의미를 갖는다.

### D3. 잔여 연차 차감은 행을 잠근다

```sql
SELECT granted_days, used_days FROM leave_balance
 WHERE emp_id = #{empId} AND year = #{year}
   FOR UPDATE
```

잠그지 않으면 **갱신 분실**이 난다. 같은 사원의 연차 신청서 2건이 동시에 승인되면 둘 다 `used_days=3` 을 읽고 둘 다 `5` 로 쓴다 — 하루가 사라진다. 화면에서 재현하기 어렵고, 재현되면 원인을 찾기 매우 어려운 종류의 버그다.

`approval_doc` 잠금과는 별개다. 그건 **문서**를 잠그고, 이건 **잔여 연차**를 잠근다. 서로 다른 문서 2건이 같은 사원의 잔여를 건드리는 것이 문제이므로 둘 다 필요하다.

**잠금 순서를 고정한다: `approval_doc` → `leave_balance`.** 항상 같은 순서로 잡아야 교착이 없다. `approve()` 가 먼저 문서를 잠그고 그 안에서 잔여를 잠그므로 자연히 이 순서가 된다 — 어기려면 일부러 어겨야 한다.

### D4. 잔여가 부족하면 승인을 실패시킨다

설계서가 정하지 않았다. 확정한다.

**잔여보다 많이 쓰려 하면 `IllegalStateException` 을 던진다.** 그러면 `approve()` 전체가 롤백되고, 문서는 `PENDING` 으로 남고, 결재자는 오류 메시지를 본다.

대안(그냥 승인하고 잔여를 음수로)이 더 나쁘다. 데이터가 조용히 틀리고, 그 틀림이 연말 정산까지 발견되지 않는다.

**단, 이 검사가 승인 시점에만 있으면 기안자가 헛수고를 한다.** 그래서 두 곳에서 본다:

| 시점 | 목적 | 성격 |
|---|---|---|
| 기안·상신 화면 | 잔여를 보여주고 초과 시 경고 | 안내 — 우회 가능 |
| **승인 (잠금 안)** | 실제 차감 직전 검증 | **권위 — 우회 불가** |

화면 검사는 신뢰하지 않는다. 상신과 승인 사이에 다른 문서가 승인되어 잔여가 줄 수 있다.

### D5. 영업일 계산은 서버가 한다 — 기안자가 일수를 입력하지 않는다

`leave_request.days` 를 사람이 입력하게 하면 금요일~월요일을 4일로 적는 실수가 반드시 난다.

`BusinessDayCalculator` 가 `holiday` 테이블 + 주말을 빼고 계산한다. 반차(`HALF_AM`/`HALF_PM`)는 0.5일이고 하루짜리만 허용한다.

`holiday` 테이블에 **2026년 한국 공휴일**을 시드로 넣는다. 대체공휴일까지 정확히 맞추는 것은 이 프로젝트의 목표가 아니므로 고정 날짜 목록으로 충분하다 — 다만 **그 사실을 시드 주석에 적는다**, 나중에 "왜 대체공휴일이 없지"를 다시 조사하지 않도록.

### D6. 근태 반영은 UPSERT 다 — `ON CONFLICT` 를 쓰고 Oracle 대응을 기록한다

`attendance` 에 `UNIQUE(emp_id, work_date)` 가 있다. 연차를 반영할 때 그날 행이 **이미 있을 수 있다** — 오전에 출근 찍고 오후에 반차를 냈다면.

```sql
INSERT INTO attendance (emp_id, work_date, status, note)
VALUES (#{empId}, #{workDate}, #{status}, #{note})
ON CONFLICT (emp_id, work_date)
DO UPDATE SET status = EXCLUDED.status, note = EXCLUDED.note
```

**여기서도 제약 위반을 잡지 않는다** (D2와 같은 이유). `ON CONFLICT` 는 예외를 만들지 않고 DB 안에서 해결하므로 트랜잭션이 중단되지 않는다.

Oracle 에는 `ON CONFLICT` 가 없다 → `MERGE INTO`. `docs/oracle-mapping.md` 에 기록한다.

### D7. 커스터마이징 지점 2와 3을 여기서 증명한다 — 각각 구현 2개

Phase 2가 결재선 정책으로 지점 1을, Phase 3이 `LlmClient` 로 지점 4를 증명했다. Phase 4는 **두 개를 한 번에** 한다.

| 지점 | 인터페이스 | 구현 A | 구현 B |
|---|---|---|---|
| 2 | `WorkTimePolicy` | `DefaultWorkTimePolicy` 09:00–18:00, 09:01 지각 | `FlexWorkTimePolicy` 지각 개념 없음, 총 근무시간만 본다 |
| 3 | `LeaveGrantPolicy` | `FlatLeaveGrantPolicy` 전원 15일 | `TenureBasedLeaveGrantPolicy` 1년 미만 월 1일, 이후 15일 + 2년마다 1일 (최대 25일) |

지점 3의 두 구현은 설계서 §6.3이 이미 지정했다. 지점 2의 두 번째(`FlexWorkTimePolicy`)는 이 계획서가 정한다 — 자율출퇴근제는 한국 회사에 실재하고, **같은 입력에 확실히 다른 결과**를 낸다(09:30 출근이 한쪽은 지각, 한쪽은 정상).

설정 키는 Phase 2·3과 같은 형태로 둔다:

```yaml
flowmate:
  attendance:
    work-time-policy: default      # default | flex
    leave-grant-policy: flat       # flat | tenure
```

### D8. 스키마는 Phase 3이 정한 방식으로 적용한다

`down -v` 를 쓰지 않는다. `CREATE TABLE IF NOT EXISTS` 로 쓰고, 새 환경은 init 이 자동 실행하고, 기존 환경은 `psql -f` 로 한 번 적용한다. 계획서 3 D5가 정한 관례이고 이제 표준이다.

**두 번 실행해 멱등성을 확인하는 단계를 반드시 거친다.** 실패한 init 은 컨테이너를 죽이지 않아서, 테이블 없이 healthy 한 컨테이너가 만들어진다.

### D9. `ApprovalService` 에 손대는 범위를 최소로 한다

`approve()` 는 Phase 2에서 완성되어 통합 테스트 16건이 지키고 있다. 여기에 근태 호출을 넣는 것이 이 Phase의 목적이지만, **그 외의 것은 건드리지 않는다.**

추가되는 것은 정확히 이만큼이다:

```java
if (doc.isCompleted() && DocType.LEAVE.equals(doc.getDocType())) {
    leaveApplyService.apply(buildLeaveCommand(approvalId));
}
```

`doc.isCompleted()` 가 없으면 `ApprovalDoc` 에 추가한다 — 상태 판정은 도메인 객체가 한다는 Phase 2의 규칙을 유지한다. Service 가 `APPROVED.equals(doc.getStatus())` 를 직접 쓰지 않는다.

**기존 16건이 전부 통과하는 것을 매 Task 끝에 확인한다.** 하나라도 깨지면 그 자리에서 멈춘다.

---

## 파일 구조

```
src/main/java/com/flowmate/attendance/
├─ domain/
│   ├─ Attendance.java          (attId, empId, workDate, checkIn, checkOut,
│   │                            workMinutes, overtimeMinutes, status, note)
│   ├─ AttendanceStatus.java    NORMAL/LATE/EARLY_LEAVE/ABSENT/LEAVE/HALF_LEAVE/HOLIDAY
│   ├─ LeaveBalance.java        (empId, year, grantedDays, usedDays) + getRemainingDays()
│   ├─ LeaveUsage.java
│   ├─ LeaveType.java           ANNUAL/HALF_AM/HALF_PM/SICK
│   ├─ WorkTimeResult.java      (workMinutes, overtimeMinutes, status)
│   ├─ LeaveApplyCommand.java   ★ D1 — 모듈 경계를 넘는 값
│   └─ AttendanceSearchCond.java
├─ policy/
│   ├─ WorkTimePolicy.java              ★ 커스터마이징 지점 2
│   ├─ DefaultWorkTimePolicy.java
│   ├─ FlexWorkTimePolicy.java          ★ 두 번째 구현
│   ├─ LeaveGrantPolicy.java            ★ 커스터마이징 지점 3
│   ├─ FlatLeaveGrantPolicy.java
│   └─ TenureBasedLeaveGrantPolicy.java ★ 두 번째 구현
├─ calc/
│   └─ BusinessDayCalculator.java       주말 + holiday 제외
├─ service/
│   ├─ AttendanceService.java           출퇴근 등록
│   ├─ AttendanceQueryService.java      개인/부서 조회
│   ├─ LeaveApplyService.java           ★ 인터페이스 (approval 이 보는 유일한 지점)
│   └─ DefaultLeaveApplyService.java    ★ 척추
├─ mapper/
│   ├─ AttendanceMapper.java
│   ├─ LeaveBalanceMapper.java
│   ├─ LeaveUsageMapper.java
│   └─ HolidayMapper.java
└─ controller/
    └─ AttendanceController.java

src/main/java/com/flowmate/approval/
├─ domain/LeaveRequest.java              (approval 소유 — 설계서 §5.3)
├─ mapper/LeaveRequestMapper.java
└─ service/ApprovalService.java          ★ 수정 (D9 범위 안에서만)

src/main/java/com/flowmate/config/
└─ AttendancePolicyConfig.java           정책 2종 교체 배선

docker/postgres/init/
├─ 40-schema-attendance.sql              (IF NOT EXISTS)
└─ 41-seed-attendance.sql                공휴일 + 연차잔여 20명

src/main/webapp/WEB-INF/views/attendance/
├─ my.jsp          내 근태 (월간)
└─ dept.jsp        부서 근태 현황
```

**예상 테스트:** 단위 81 → **약 111** (+30), 통합 61 → **약 78** (+17).

---

## Task 1: 근태 스키마 5종과 시드

`40-schema-attendance.sql` 에 `leave_request` · `attendance` · `leave_balance` · `leave_usage` · `holiday` 를 설계서 §5.3~5.4 그대로 만든다. 전부 `IF NOT EXISTS`.

`leave_request` 는 `approval` 소유지만 파일은 이 Phase에 둔다 — 연차 기능과 함께 처음 쓰이기 때문이다.

`41-seed-attendance.sql`:

- **2026년 한국 공휴일** 고정 날짜 목록. 주석에 "대체공휴일은 반영하지 않는다 — 이 프로젝트의 목표가 아니다"를 적는다
- **연차 잔여 20명분**, 2026년. 입사일 기준으로 `TenureBasedLeaveGrantPolicy` 가 낼 값과 비슷하게 넣되, **정책이 계산한 값을 시드에 박지 않는다.** 시드는 손으로 만든 고정값이고 정책은 코드다 — 둘이 같아야 할 이유가 없고, 같다고 가정하면 정책을 고칠 때 시드가 조용히 틀려진다 (Phase 2가 결재선 시드에서 같은 판단을 했다)
- 데모용으로 **일부 사원의 `used_days` 를 0이 아닌 값**으로 둔다. 전부 0이면 "잔여가 준다"를 화면에서 보여줄 때 대비가 없다

적용:

```powershell
docker exec -i flowmate-postgres psql -U flowmate -d flowmate -f /docker-entrypoint-initdb.d/40-schema-attendance.sql
docker exec -i flowmate-postgres psql -U flowmate -d flowmate -f /docker-entrypoint-initdb.d/41-seed-attendance.sql
```

**검증:** 스키마는 두 번 실행해 멱등성 확인. 시드는 두 번 실행하면 PK 충돌이 나므로 `ON CONFLICT DO NOTHING` 을 붙이거나 한 번만 실행한다 — **어느 쪽인지 파일 주석에 명시한다.**

기존 데이터 확인: `docs=6 emps=20` + `holidays=N balances=20`.

---

## Task 2: `WorkTimePolicy` 2종 (TDD) ★

**단위 12건 예상.**

```java
public interface WorkTimePolicy {
    WorkTimeResult evaluate(LocalDateTime checkIn, LocalDateTime checkOut, LocalDate date);
}
```

### `DefaultWorkTimePolicy`

09:00 시작 · 18:00 종료 · 소정 8시간. 09:01 이후 = `LATE`, 18:00 이전 퇴근 = `EARLY_LEAVE`, 8시간 초과분 = 연장근무.

함정들:

- **점심시간 1시간을 뺀다.** 09:00–18:00 은 9시간이고 소정은 8시간이다. 빼지 않으면 정시 출퇴근한 사람이 매일 1시간 연장근무한 것이 된다
- **`checkOut` 이 없으면** `ABSENT` 가 아니라 "퇴근 미등록"이다. 출근은 했으니 `ABSENT` 로 적으면 거짓이 된다 — `workMinutes=0` + 상태는 그대로 두고 화면이 "퇴근 미등록"을 표시한다
- **지각과 조퇴가 동시에** 일어날 수 있다. 상태는 하나뿐이므로 우선순위를 정한다 — `LATE` 를 우선한다. 그리고 그 결정을 주석에 적는다

### `FlexWorkTimePolicy` ★ 두 번째 구현

지각·조퇴 개념이 없다. 총 근무시간만 본다. 8시간 이상이면 `NORMAL`, 미만이면 `EARLY_LEAVE`(부족).

**교체 증명 테스트:** 09:30 출근 · 18:30 퇴근을 두 정책에 넣는다.
Default → `LATE`. Flex → `NORMAL`. **같은 입력, 다른 결과.**

---

## Task 3: 출퇴근 등록

`AttendanceService.checkIn(empId)` / `checkOut(empId)`.

- 출근은 하루 1회. 이미 있으면 기존 값을 유지하고 조용히 통과할지 예외를 던질지 — **예외를 던진다.** 두 번 누른 사용자가 "됐나?"를 모르는 것보다 "이미 등록됐습니다"를 보는 것이 낫다
- 퇴근 시 `WorkTimePolicy` 를 태워 `workMinutes`/`overtimeMinutes`/`status` 를 계산해 저장한다
- **출근 없이 퇴근**을 막는다

화면은 홈에 버튼 두 개를 얹는 수준으로 최소화한다. `home.jsp` 에 추가하고, 오늘 상태를 보여준다.

**통합 3건:** 출근→퇴근 정상 / 중복 출근 거부 / 출근 없는 퇴근 거부.

---

## Task 4: `LeaveGrantPolicy` 2종과 잔여 관리 (TDD)

**단위 10건 예상.**

`FlatLeaveGrantPolicy` 전원 15일.
`TenureBasedLeaveGrantPolicy` — 1년 미만은 근속 월수만큼(월 1일), 이후 15일 + 2년마다 1일, **최대 25일**.

경계를 테스트로 고정한다: 입사 11개월(11일) / 정확히 1년(15일) / 3년(16일) / 아주 오래(25일 상한).

`LeaveBalance.getRemainingDays()` 는 `granted - used` 로 계산한다. **잔여를 컬럼으로 두지 않는다** — 설계서 §5.4가 "불일치 방지"라고 명시했다.

`LeaveBalanceMapper.findForUpdate(empId, year)` 를 D3대로 `FOR UPDATE` 로 만든다.

---

## Task 5: `BusinessDayCalculator` 와 연차 신청서 기안

**단위 8건 예상.**

`BusinessDayCalculator.countBusinessDays(start, end)` — 주말과 `holiday` 를 뺀다.

경계: 금~월(2일) / 연휴 포함 / 시작=종료(1일) / 종료가 시작보다 앞(예외) / 전부 공휴일(0일 — 이건 신청 자체를 막아야 한다).

기안 화면 확장: `docType=LEAVE` 를 고르면 기간·유형 입력이 나타나고, 서버가 일수를 계산해 `leave_request` 에 저장한다. **잔여 연차를 화면에 보여주고 초과 시 경고**한다 (D4의 안내 계층).

반차는 하루짜리만 허용하고 0.5일로 고정한다.

---

## Task 6: ★★ 승인 → 근태 반영 (이 Phase의 척추)

**통합 6건 이상. 여기가 Phase 4의 존재 이유다.**

### `DefaultLeaveApplyService.apply(LeaveApplyCommand)`

순서가 중요하다:

1. **이미 반영됐는지 조회** (`leave_usage.existsByApprovalId`) → 있으면 return **(D2 — 제약 위반을 잡지 않는다)**
2. `leave_balance` 를 **`FOR UPDATE` 로 잠그고** 읽는다 (D3)
3. **잔여 검사** — 부족하면 `IllegalStateException` (D4)
4. `used_days` 갱신
5. `leave_usage` 삽입
6. 기간의 **영업일마다** `attendance` UPSERT — `status=LEAVE` (반차는 `HALF_LEAVE`) **(D6)**

### `ApprovalService.approve()` 수정 — D9 범위 안에서만

```java
if (doc.isCompleted() && DocType.LEAVE.equals(doc.getDocType())) {
    leaveApplyService.apply(buildLeaveCommand(approvalId));
}
```

### 통합 테스트

| # | 이름 | 무엇을 증명하는가 |
|---|---|---|
| 1 | ★ `approvingLeaveReducesBalanceAndMarksAttendance` | 승인 → 잔여 감소 + 해당 일자 `attendance.status=LEAVE`. **완료 기준 그 자체** |
| 2 | ★ `attendanceFailureRollsBackTheApproval` | 근태 반영을 실패시키면 **문서 상태도 되돌아간다**. 한 트랜잭션임의 증명 |
| 3 | ★ `insufficientBalanceBlocksApproval` | 잔여 부족 → 승인 실패, 문서는 `PENDING`, 잔여는 그대로 |
| 4 | `applyingTwiceDoesNotDoubleCount` | 같은 결재를 두 번 반영해도 1회만 (D2의 조회-후-분기) |
| 5 | `weekendsAndHolidaysAreNotMarked` | 금~월 연차가 토·일에 근태를 만들지 않는다 |
| 6 | `halfDayLeaveMarksHalfLeave` | 반차 → `HALF_LEAVE`, 잔여 0.5 감소 |
| 7 | `nonLeaveApprovalTouchesNothing` | 지출결의서 승인은 근태를 건드리지 않는다 |

**2번이 특히 중요하다.** 설계서가 Spring 이벤트를 거부한 이유가 이것이고, 이 테스트가 없으면 그 판단이 증명되지 않는다. 근태 쪽을 일부러 터뜨리고 `approval_doc.status` 가 되돌아왔는지 확인한다.

**★ 매 단계마다 Phase 2의 기존 통합 16건이 전부 통과하는지 확인한다** (D9).

---

## Task 7: 근태 조회 화면

- **내 근태** — 월간 목록 + 합계(근무일수·지각·연장·연차사용). `?ym=2026-08` 로 이동
- **부서 근태 현황** — 조직도 계층을 따라 하위 부서까지 집계 (설계서 §6.3). **Phase 1의 하향 재귀 CTE 를 재사용한다**

Phase 2에서 확립한 규약을 따른다:

- 상태 판정은 Service 가 하고 JSP 는 boolean 만 쓴다
- 페이지 보정(`totalPages` 클램프)을 잊지 않는다 — 없으면 페이징이 조용히 죽는다
- 클래스 이름은 의미 기반, **CSS 규칙은 쓰지 않는다** (Phase 6)
- 권한: 부서 현황은 **본인 부서와 하위만** 볼 수 있다. `empId` 를 로그인 주체로 덮어쓴다

---

## Task 8: 마감

- [ ] `mvnw clean verify` — 실제 숫자 기록. **Phase 2 통합 16건 포함 전부 통과**
- [ ] 시드 무결 + 테스트가 남긴 근태 행 정리
- [ ] `docs/oracle-mapping.md` — **`ON CONFLICT` → `MERGE INTO`** (D6), `FOR UPDATE` 재확인
- [ ] `README.md` — Phase 4 체크, 테스트 수, **설계 판단 기록**: "Spring 이벤트 대신 같은 트랜잭션 직접 호출을 선택한 이유" (설계서 §6.3이 README에 적으라고 명시했다)
- [ ] 로드맵 §6 갱신
- [ ] `style.css` 클래스 목록에 이름만 추가
- [ ] merge → tag `phase-4-attendance` → push

### 다음 계획서로 넘기는 것

| # | 항목 | 왜 지금 안 하는가 |
|---|---|---|
| B1 | 근태 3개월치 대량 시드 | Phase 5-2가 AI 평가셋과 함께 만든다 |
| B2 | 연차 맥락 인식 결재 (기능 3) | Phase 5-4 |
| B3 | 대체공휴일 | 이 프로젝트의 목표가 아니다 (D5) |
| B4 | 반차의 시간 단위 근태 | `HALF_LEAVE` 상태로 충분하다. 시간 단위는 요구가 없다 |

---

## 부록 — 설계서 §9 Phase 4 대응 확인

| 설계서 요구 | 이 계획서 | Task |
|---|---|---|
| 근태 스키마 4종 | ✅ **5종** (`leave_request` 포함) | 1 |
| 출퇴근 등록 | ✅ | 3 |
| `WorkTimePolicy` + 단위 테스트 | ✅ **구현 2종** | 2 |
| `LeaveGrantPolicy` + 테스트 | ✅ 구현 2종 | 4 |
| 연차 잔여 관리 | ✅ `FOR UPDATE` 포함 | 4 |
| 개인/부서 근태 조회 화면 | ✅ | 7 |
| **연차 승인 시 근태 반영 (`@Transactional`)** | ✅ | 6 |
| **통합 테스트 3건** | ✅ **7건** | 6 |
| 완료 기준: 잔여 감소 + 근태 '연차' | ✅ 테스트 1 | 6 |
| 완료 기준: 중간 실패 시 전부 롤백 | ✅ 테스트 2 | 6 |
