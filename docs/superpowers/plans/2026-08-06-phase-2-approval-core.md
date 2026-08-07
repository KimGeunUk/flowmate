# FlowMate Phase 2 — 전자결재 코어 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 사원이 문서를 기안해 상신하면 결재선이 자동 생성되고, 결재자들이 순서대로 승인하거나 유형을 선택해 반려할 수 있으며, 그 전 과정이 내 결재함과 문서 상세 화면에서 보인다.

**Architecture:** 상태 전이를 `ApprovalDoc` 도메인 객체 안에 가두고 Service 는 트랜잭션과 이력 기록만 맡는다. 결재선 생성은 `ApprovalLinePolicy` 인터페이스 뒤로 분리해 구현체 2종을 설정으로 교체한다. 반려는 유형 선택을 필수로 만들어 `approval_reject_history` 에 쌓고, 그것이 Phase 5 AI 사전점검의 학습 원천이 된다.

**Tech Stack:** Java 17, Spring Boot 3.5.16 (WAR), JSP + Jakarta JSTL 3.0, jQuery 3.7.1, MyBatis 3 (`mybatis-spring-boot-starter` 3.0.5), PostgreSQL 16 (Docker), Spring Security 6.5, Maven, JUnit 5 + AssertJ + Mockito

**참조 문서:**
- 설계서: [2026-08-05-flowmate-design.md](../specs/2026-08-05-flowmate-design.md) §5.2 · §6.2 · §7 · §9
- 로드맵·공통 규약: [2026-08-05-flowmate-roadmap.md](2026-08-05-flowmate-roadmap.md) — 특히 §3 규약, §2.0 이월 5건, §5.1 결재선 정책
- 직전 계획서: [2026-08-05-phase-0-1-foundation.md](2026-08-05-phase-0-1-foundation.md)

---

## 시작 상태 (Phase 1 종료 시점)

`main` 이 `phase-1-org-user` 태그 + 사전점검 머지(`e8ed4f9`) 지점이다. 동작하는 것:

- 로그인(`formLogin` + BCrypt), 사원 목록(동적 SQL·페이징·검색), 조직도(재귀 CTE 3단 계층)
- `mvnw clean verify` → Surefire **22** · Failsafe **21** · BUILD SUCCESS
- `flowmate.war` 가 **실제 외부 Tomcat 10.1.57 컨테이너에 배포되어 동작함이 검증됨** (컨텍스트 경로 `/flowmate` 포함)
- `style.css` 에 최소 스타일 66개 규칙 적용 — 화면이 데모 가능한 상태
- 시드: 부서 7(3단 계층) · 직급 6 · 사원 20

재사용할 자산:

| 자산 | 위치 | Phase 2 에서의 쓰임 |
|---|---|---|
| `Page<T>` | `common.web` | 내 결재함 페이징 |
| `Page.totalPagesOf(long,int)` | `common.web` | Service 페이지 보정 |
| 재귀 CTE | `mapper/org/DepartmentMapper.xml` | **`DefaultApprovalLinePolicy` 의 부서 트리 상향 탐색에 재사용** |
| `LoginEmployee` | `org.security` | `@AuthenticationPrincipal` 로 기안자·결재자 식별 |
| 공통 조각 5종 | `WEB-INF/views/common/` | 모든 신규 화면 |
| `pagination.jsp` 규약 | 같은 곳 | `paging` 모델명 + `#searchForm` + hidden `page` |

---

## 이 계획서가 전제하는 확정 사항

사전점검에서 이미 결정된 것들이다. 구현 중에 다시 논의하지 않는다.

### D1. 결재선 생성 정책 (로드맵 §5.1)

`DefaultApprovalLinePolicy` — 순서대로 적용:

1. 기안자 부서에서 시작해 **부서 트리를 루트까지 올라간다**
2. 각 부서의 최고 `position_level` 1명을 뽑는다 (동급이면 `hire_date` 이른 사람)
3. 그 사람이 **기안자보다 직급이 높고**, **`position_level < 6`** 이고, 기안자 본인이 아니면 결재선에 추가
4. `amount > 3,000,000` 이면 **이사(L6)** 를 마지막에 추가 (이미 있거나 기안자 본인이면 생략)
5. 여기까지 결재선이 비었고 기안자가 L6 이 아니면 이사 1명 추가 (빈 결재선 방지)
6. 그래도 비면 **결재자 0명** — 상신 즉시 완료

**3번의 `position_level < 6` 이 핵심이다.** 이걸 빼면 이사가 항상 들어와 4번의 금액 조건이 죽는다.

시드로 검증된 기대값 (Task 3 의 단위 테스트가 이 표를 그대로 고정한다):

| 기안자 | 금액 | 결재선 |
|---|---:|---|
| 곽수빈 (개발팀 사원 L1, emp_id 18) | 1,000,000 | 신동혁(14) → 박현주(3) |
| 곽수빈 | 5,000,000 | 신동혁(14) → 박현주(3) → 정도현(1) |
| 신동혁 (개발팀 과장 L3, emp_id 14) | 1,000,000 | 박현주(3) |
| 서다인 (인사팀 사원 L1, emp_id 6) | 1,000,000 | 최민석(4) → 김성일(2) |
| 박현주 (사업본부 부장 L5, emp_id 3) | 1,000,000 | 정도현(1) |
| 정도현 (이사 L6, emp_id 1) | 1,000,000 | *(없음)* |

`SimpleTwoStepLinePolicy` — 트리를 오르지 않고 소속 부서 최고 직급 1명만. 금액 무관.
곽수빈이 5,000,000원을 기안해도 결재선은 신동혁 1명.

### D2. 결재자 0명일 때의 상태 전이

`submit(approverCount)` 가 `approverCount == 0` 이면 `DRAFT → APPROVED` 로 **직행**한다.
`PENDING` 을 거치지 않는다 — 거칠 이유가 없고, `PENDING` 인데 결재선이 비어 있는 상태가 만들어지면
"내 결재함"의 "내 차례인 문서" 판정 쿼리가 영원히 못 찾는 유령 문서가 된다.

이력은 `SUBMIT` 한 건만 남긴다. 승인한 사람이 없으므로 `APPROVE` 이력을 지어내지 않는다.

### D3. 첨부파일 업로드 경로 (로드맵 Q6 해소)

- 설정 키: `flowmate.upload.base-dir`, 기본값 `./upload`
- 실제 저장 경로: `{base-dir}/approval/{yyyy}/{MM}/{UUID}.{ext}`
- **`.gitignore` 에 `/upload/` 를 추가한다** (Task 10). 저장소는 Phase 6 이후 public 이 되므로
  업로드된 파일이 커밋되면 지워도 이력에 남는다
- 원본 파일명은 `approval_attachment.file_name` 에만 두고 디스크에는 UUID 로 저장한다
  (경로 조작·중복·한글 파일명 문제를 한 번에 없앤다)

### D4. 로드맵 §2.0 이월 항목 중 이 Phase 에서 처리할 것

| # | 항목 | 이 계획서에서 |
|---|---|---|
| C2 | CSRF hidden input 이 파일마다 복붙 | **Task 6 에서 `common/csrf-input.jsp` 조각을 만들고** 이후 모든 POST 폼이 include 한다 |
| C1 | `eraseCredentials()` 가 공유 `Employee` 를 변경 | 이 Phase 는 매퍼에 캐시를 붙이지 않는다. **붙이지 않는 것이 조치다** — 붙이는 순간 로그인이 깨진다는 사실을 Task 5 주석에 남긴다 |
| C5 | `defaultSuccessUrl("/", true)` 가 저장된 요청을 버림 | **Task 9 에서 해소한다.** 내 결재함이 딥링크 대상이 되므로 `alwaysUse` 를 `false` 로 바꾼다 |

---

## 파일 구조

```
docker/postgres/init/
├─ 20-schema-approval.sql          결재 5종 테이블 + 인덱스
└─ 21-seed-approval.sql            데모용 문서 6건 (상태별로 하나씩)

src/main/java/com/flowmate/approval/
├─ domain/
│   ├─ ApprovalStatus.java         상태 문자열 상수 + 판정 헬퍼
│   ├─ LineStatus.java             결재선 상태 상수
│   ├─ LineType.java               APPROVAL/AGREEMENT/REFERENCE 상수
│   ├─ DocType.java                문서 유형 5종 상수 + 문서번호 접두사
│   ├─ HistoryAction.java          이력 액션 상수
│   ├─ RejectReason.java           반려 유형 6종 상수 + 화면 표시명
│   ├─ ApprovalDoc.java            ★ 상태 기계
│   ├─ ApprovalLine.java
│   ├─ ApprovalHistory.java
│   ├─ RejectHistory.java
│   ├─ ApprovalAttachment.java
│   └─ ApprovalSearchCond.java     내 결재함 검색 조건
├─ policy/
│   ├─ ApprovalLinePolicy.java     ★ 커스터마이징 지점 1
│   ├─ DefaultApprovalLinePolicy.java
│   ├─ SimpleTwoStepLinePolicy.java
│   └─ ApproverCandidate.java      정책 입력용 값 객체
├─ mapper/
│   ├─ ApprovalDocMapper.java
│   ├─ ApprovalLineMapper.java
│   ├─ ApprovalHistoryMapper.java
│   ├─ RejectHistoryMapper.java
│   └─ ApprovalAttachmentMapper.java
├─ service/
│   ├─ ApprovalService.java        ★ 트랜잭션 경계
│   ├─ ApprovalQueryService.java   조회 전용 (내 결재함 · 상세)
│   └─ AttachmentStorage.java      파일 저장/삭제
└─ controller/
    ├─ ApprovalWriteController.java  기안·임시저장·상신
    ├─ ApprovalActionController.java 승인·반려·회수
    ├─ ApprovalBoxController.java    내 결재함·상세
    └─ AttachmentController.java     업로드·다운로드

src/main/java/com/flowmate/config/
└─ ApprovalPolicyConfig.java       ★ 정책 구현체 교체 지점

src/main/resources/mapper/approval/
├─ ApprovalDocMapper.xml
├─ ApprovalLineMapper.xml
├─ ApprovalHistoryMapper.xml
├─ RejectHistoryMapper.xml
└─ ApprovalAttachmentMapper.xml

src/main/webapp/WEB-INF/views/
├─ common/csrf-input.jsp           ★ C2 해소
└─ approval/
    ├─ write.jsp                   기안 작성·수정
    ├─ box.jsp                     내 결재함 (탭 4종)
    ├─ detail.jsp                  문서 상세 + 결재선 + 이력
    └─ reject-modal.jsp            반려 유형 선택 (detail 에 include)

src/test/java/com/flowmate/approval/
├─ domain/ApprovalDocTest.java             (단위 13) ★
├─ domain/DocTypeTest.java                 (단위 4)
├─ policy/DefaultApprovalLinePolicyTest.java (단위 10) ★
├─ policy/SimpleTwoStepLinePolicyTest.java   (단위 3)
├─ mapper/ApprovalDocMapperIT.java          (통합 6)
├─ service/ApprovalServiceIT.java           (통합 9) ★
└─ service/ApprovalQueryServiceIT.java      (통합 5)
```

**테스트 목표:** 단위 22 → **50**, 통합 21 → **41**.
설계서 §10 의 "단위 테스트 40건 이상"을 이 Phase 에서 넘긴다.

---

## Phase 2 착수

- [ ] **작업 브랜치를 만든다**

```powershell
git switch main
git pull
git switch -c feat/phase-2-approval-core
```

> **환경 규약 (로드맵 §3.2 · §3.7).** 매 Task 에서 그대로 적용한다.
>
> | 상황 | 규칙 |
> |---|---|
> | `java`/`mvn`/`docker` 실행 | 앞에 `$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User')` 와 `$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')` |
> | Maven `-D` 속성 | **개별 인용** — `.\mvnw.cmd verify "-Dit.test=ApprovalServiceIT"` |
> | 한글 파일 쓰기 | `Write`/`Edit` 도구만. `Set-Content`·`Out-File`·`>` 금지 |
> | 한글 파일 검증 | `Read` 도구만. `Get-Content` 는 CP949 라 정상 파일도 깨져 보인다 |
> | HTTP 응답 확인 | 파일로 받아 `[System.Text.Encoding]::UTF8.GetString([System.IO.File]::ReadAllBytes($p))` |
> | 여러 줄 커밋 메시지 | 파일에 쓴 뒤 `git commit -F <파일>` |
> | PowerShell 문자열의 `$` | 백틱 이스케이프 (`` `$ ``). 백슬래시 아님 |
> | 네이티브 명령 | `2>&1` 파이프 금지 — 성공해도 `NativeCommandError` 로 실패 처리된다 |
> | **`docker compose down -v`** | **금지.** 시드 데이터가 날아간다. 스키마를 추가할 때만 예외이며 Task 1 에서 한 번 쓴다 |

---

## Task 1: 결재 스키마 5종과 데모 시드

**Files:**
- Create: `docker/postgres/init/20-schema-approval.sql`
- Create: `docker/postgres/init/21-seed-approval.sql`

- [ ] **Step 1: `20-schema-approval.sql` 을 만든다**

설계서 §5.2 그대로에 인덱스와 `COMMENT ON` 을 더했다.

```sql
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
```

> `doc_type` 과 `dept_id` 가 `approval_doc` 에 이미 있는데 여기 또 있는 것은 **의도한 비정규화**다.
> 사전점검은 상신 버튼을 누를 때마다 도는 조회이므로 매번 조인하지 않고
> 이 테이블 + `idx_reject_type_dept` 만으로 끝낸다. 주석에 그 이유를 남겨 뒀다.

- [ ] **Step 2: `21-seed-approval.sql` 을 만든다**

상태별로 하나씩 두어 화면을 만들자마자 확인할 수 있게 한다. 결재선 정책과 무관하게
**손으로 만든 고정 데이터**다 — 정책이 바뀌어도 이 시드는 흔들리지 않는다.

```sql
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
```

> `approval_line` · `approval_history` 는 PK 를 명시하지 않고 시퀀스에 맡겼지만
> `setval` 은 그래도 넣는다 — 넣어도 무해하고, 나중에 PK 를 명시하는 행이 추가될 때를 대비한 방어다.
> `approval_attachment` 는 시드가 없어 `setval` 도 없다.

- [ ] **Step 3: 볼륨을 지우고 다시 올려 적용한다**

새 스키마 파일은 **빈 볼륨에서만** 실행된다.

```powershell
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
docker compose down -v
docker compose up -d postgres
```

`healthy` 가 될 때까지 폴링한다.

```powershell
docker inspect --format '{{.State.Health.Status}}' flowmate-postgres
```

> **이 Phase 에서 `down -v` 를 쓰는 것은 여기 한 번뿐이다.** 이후 Task 에서는 금지다 —
> 조직 시드까지 같이 날아가 앞선 통합 테스트가 전부 깨진다.

- [ ] **Step 4: init 로그에 오류가 없는지 확인한다**

실패한 스크립트가 컨테이너를 멈추지 않는 경우가 있으므로 로그를 직접 본다.

```powershell
docker compose logs postgres > "$env:TEMP\pglog2.txt"
```

`Read` 도구로 열어 다음을 확인한다:
- `00-extension.sql`, `10-schema-org.sql`, `11-seed-org.sql`, `20-schema-approval.sql`, `21-seed-approval.sql` 다섯 개가 모두 `running` 으로 찍혔는가
- `ERROR` / `FATAL` 이 있는가

- [ ] **Step 5: 적용 결과를 검증한다**

```powershell
docker exec -i flowmate-postgres psql -U flowmate -d flowmate -t -A -c "SELECT 'depts='||(SELECT COUNT(*) FROM department)||' emps='||(SELECT COUNT(*) FROM employee)||' docs='||(SELECT COUNT(*) FROM approval_doc)||' lines='||(SELECT COUNT(*) FROM approval_line)||' hist='||(SELECT COUNT(*) FROM approval_history)||' rejects='||(SELECT COUNT(*) FROM approval_reject_history);"
```

기대: `depts=7 emps=20 docs=6 lines=8 hist=15 rejects=1`

상태별 분포도 확인한다.

```powershell
docker exec -i flowmate-postgres psql -U flowmate -d flowmate -t -A -c "SELECT status||'='||COUNT(*) FROM approval_doc GROUP BY status ORDER BY status;"
```

기대: `APPROVED=1`, `CANCELED=1`, `DRAFT=1`, `PENDING=2`, `REJECTED=1`

- [ ] **Step 6: 기존 테스트가 여전히 통과하는지 확인한다**

스키마가 늘었을 뿐 조직 데이터는 그대로여야 한다.

```powershell
.\mvnw.cmd clean verify
```

기대: Surefire `Tests run: 22`, Failsafe `Tests run: 21`, `BUILD SUCCESS`.
여기서 깨지면 시드가 잘못 재적용된 것이다.

- [ ] **Step 7: 커밋한다**

```powershell
git add docker/postgres/init/20-schema-approval.sql docker/postgres/init/21-seed-approval.sql
git status -s
```

메시지 (파일로 넘긴다):

```
feat: 결재 스키마 5종과 상태별 데모 시드 추가

approval_reject_history 의 doc_type/dept_id 는 approval_doc 과 중복이지만 의도한 비정규화다.
사전점검은 상신할 때마다 도는 조회이므로 매번 조인하지 않고 이 테이블과 인덱스만으로 끝낸다.

시드는 상태별로 문서를 하나씩 둔다. 내 결재함의 탭 4종을 화면이 생기자마자 확인할 수 있고,
결재선 정책이 바뀌어도 손으로 만든 이 데이터는 흔들리지 않는다.

idx_line_approver 는 내 결재함의 '내 차례인 문서' 판정이 타는 인덱스다.
```

---

## Task 2: `ApprovalDoc` 상태 기계 (TDD) ★

> 설계서 §6.2 가 **"이 프로젝트에서 가장 중요한 도메인 로직"** 이라고 지목한 부분이다.
> 허용되지 않는 전이는 `IllegalStateException` 으로 즉시 차단하고 그것을 테스트로 고정한다.
> DB 도 Spring 컨텍스트도 필요 없는 순수 단위 테스트다.

**Files:**
- Create: `src/main/java/com/flowmate/approval/domain/ApprovalStatus.java`
- Create: `src/main/java/com/flowmate/approval/domain/HistoryAction.java`
- Test: `src/test/java/com/flowmate/approval/domain/ApprovalDocTest.java`
- Create: `src/main/java/com/flowmate/approval/domain/ApprovalDoc.java`

- [ ] **Step 1: 상태 상수 두 개를 먼저 만든다**

테스트가 이 상수를 참조하므로 이것만 선행한다. `enum` 대신 `String` 상수를 쓰는 이유는
MyBatis 의 컬럼 매핑과 JSP EL 비교(`${doc.status == 'PENDING'}`)가 그대로 동작하게 하기 위한 것이다.
설계서 §6.2 의 예시 코드도 문자열 비교를 쓴다.

`src/main/java/com/flowmate/approval/domain/ApprovalStatus.java`:

```java
package com.flowmate.approval.domain;

/**
 * 결재 문서 상태.
 *
 * enum 이 아니라 String 상수인 이유:
 * MyBatis 가 VARCHAR 컬럼을 그대로 읽고, JSP EL 이 ${doc.status == 'PENDING'} 으로
 * 비교할 수 있어야 한다. enum 으로 두면 양쪽에 타입 핸들러와 변환이 필요해진다.
 */
public final class ApprovalStatus {

    /** 임시저장. 기안자만 보이고 수정·삭제할 수 있다 */
    public static final String DRAFT = "DRAFT";
    /** 상신되어 결재 진행 중 */
    public static final String PENDING = "PENDING";
    /** 최종 승인 완료 */
    public static final String APPROVED = "APPROVED";
    /** 반려됨 */
    public static final String REJECTED = "REJECTED";
    /** 기안자가 회수함 */
    public static final String CANCELED = "CANCELED";

    private ApprovalStatus() {
    }

    /** 더 이상 상태가 바뀌지 않는 종결 상태인가 */
    public static boolean isTerminal(String status) {
        return APPROVED.equals(status) || REJECTED.equals(status) || CANCELED.equals(status);
    }
}
```

`src/main/java/com/flowmate/approval/domain/HistoryAction.java`:

```java
package com.flowmate.approval.domain;

/** 결재 이력에 남는 행위 */
public final class HistoryAction {

    public static final String DRAFT = "DRAFT";
    public static final String SUBMIT = "SUBMIT";
    public static final String APPROVE = "APPROVE";
    public static final String REJECT = "REJECT";
    public static final String CANCEL = "CANCEL";

    private HistoryAction() {
    }
}
```

- [ ] **Step 2: 실패하는 테스트를 쓴다**

`src/test/java/com/flowmate/approval/domain/ApprovalDocTest.java`:

```java
package com.flowmate.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 결재 문서의 상태 기계. 설계서 §6.2 가 정의한 전이만 허용하고 나머지는 즉시 예외로 막는다.
 * DB 도 Spring 도 없는 순수 단위 테스트다.
 */
class ApprovalDocTest {

    private static final Long DRAFTER_ID = 18L;   // 곽수빈
    private static final Long APPROVER_ID = 14L;  // 신동혁
    private static final Long OTHER_ID = 3L;      // 박현주

    private ApprovalDoc doc;

    @BeforeEach
    void setUp() {
        doc = new ApprovalDoc();
        doc.setApprovalId(1L);
        doc.setDocNo("EXP-2026-0001");
        doc.setDocType(DocType.EXPENSE);
        doc.setTitle("3월 출장비 정산");
        doc.setDrafterId(DRAFTER_ID);
        doc.setDeptId(7L);
        doc.setAmount(new BigDecimal("540000"));
        doc.setStatus(ApprovalStatus.DRAFT);
        doc.setCurrentStep(0);
        doc.setDraftedAt(LocalDateTime.of(2026, 3, 2, 9, 10));
    }

    @Test
    @DisplayName("임시저장 문서를 상신하면 결재 진행 중이 되고 1단계부터 시작한다")
    void submitMovesDraftToPending() {
        doc.submit(2);

        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(doc.getCurrentStep()).isEqualTo(1);
        assertThat(doc.getSubmittedAt()).isNotNull();
        assertThat(doc.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("결재자가 없으면 상신 즉시 승인 완료된다")
    void submitWithNoApproverCompletesImmediately() {
        // 이사가 기안한 문서처럼 위에 결재할 사람이 없는 경우다.
        // PENDING 을 거치면 결재선이 빈 채로 대기하는 유령 문서가 되므로 직행시킨다.
        doc.submit(0);

        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(doc.getCurrentStep()).isZero();
        assertThat(doc.getSubmittedAt()).isNotNull();
        assertThat(doc.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("임시저장이 아닌 문서는 상신할 수 없다")
    void cannotSubmitUnlessDraft() {
        doc.submit(2);

        assertThatThrownBy(() -> doc.submit(2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("임시저장");
    }

    @Test
    @DisplayName("결재자 수가 음수면 상신 자체를 거부한다")
    void rejectsNegativeApproverCount() {
        assertThatThrownBy(() -> doc.submit(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("중간 승인은 진행 중을 유지하고 단계만 하나 올린다")
    void intermediateApprovalAdvancesStep() {
        doc.submit(2);

        doc.approve(2);

        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(doc.getCurrentStep()).isEqualTo(2);
        assertThat(doc.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("마지막 단계에서 승인하면 완료된다")
    void finalApprovalCompletesDocument() {
        doc.submit(2);
        doc.approve(2);

        doc.approve(2);

        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(doc.getCompletedAt()).isNotNull();
        assertThat(doc.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("결재자가 1명이면 첫 승인이 곧 최종 승인이다")
    void singleApproverCompletesOnFirstApproval() {
        doc.submit(1);

        doc.approve(1);

        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(doc.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("진행 중이 아닌 문서는 승인할 수 없다")
    void cannotApproveUnlessPending() {
        assertThatThrownBy(() -> doc.approve(2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("진행 중");
    }

    @Test
    @DisplayName("반려하면 즉시 종결된다")
    void rejectTerminatesDocument() {
        doc.submit(2);

        doc.reject();

        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(doc.getCompletedAt()).isNotNull();
        assertThat(doc.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("진행 중이 아닌 문서는 반려할 수 없다")
    void cannotRejectUnlessPending() {
        assertThatThrownBy(doc::reject)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("진행 중");
    }

    @Test
    @DisplayName("임시저장 문서는 기안자가 회수할 수 있다")
    void drafterCanCancelDraft() {
        doc.cancel(DRAFTER_ID);

        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.CANCELED);
        assertThat(doc.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("첫 승인 전이면 상신한 문서도 회수할 수 있다")
    void drafterCanCancelBeforeFirstApproval() {
        doc.submit(2);

        doc.cancel(DRAFTER_ID);

        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.CANCELED);
    }

    @Test
    @DisplayName("한 단계라도 승인된 문서는 기안자도 회수할 수 없다")
    void cannotCancelAfterFirstApproval() {
        doc.submit(2);
        doc.approve(2);

        assertThatThrownBy(() -> doc.cancel(DRAFTER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 결재가 진행");
    }

    @Test
    @DisplayName("기안자가 아니면 회수할 수 없다")
    void nonDrafterCannotCancel() {
        assertThatThrownBy(() -> doc.cancel(OTHER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("기안자");
    }

    @Test
    @DisplayName("종결된 문서는 어떤 전이도 받지 않는다")
    void terminalDocumentAcceptsNoTransition() {
        doc.submit(1);
        doc.approve(1);

        assertThatThrownBy(() -> doc.submit(1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> doc.approve(1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(doc::reject).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> doc.cancel(DRAFTER_ID)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("임시저장 문서만 수정할 수 있다")
    void onlyDraftIsEditable() {
        assertThat(doc.isEditable()).isTrue();

        doc.submit(2);

        assertThat(doc.isEditable()).isFalse();
    }

    @Test
    @DisplayName("결재자가 지금 이 문서를 처리할 차례인지 판정한다")
    void identifiesCurrentApproverStep() {
        doc.submit(2);

        assertThat(doc.isAwaitingStep(1)).isTrue();
        assertThat(doc.isAwaitingStep(2)).isFalse();

        doc.approve(2);

        assertThat(doc.isAwaitingStep(1)).isFalse();
        assertThat(doc.isAwaitingStep(2)).isTrue();
    }
}
```

> `APPROVER_ID` 상수는 지금 쓰이지 않지만 남겨 둔다 — Task 7 의 Service 통합 테스트가
> 같은 사원 번호를 쓰므로 값의 출처를 한 곳에 적어 두는 편이 낫다.
> 사용하지 않는 상수 경고가 거슬리면 그 테스트에서 참조할 때까지 지워도 된다.

- [ ] **Step 3: 테스트가 실패하는지 확인한다**

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User')
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
.\mvnw.cmd test "-Dtest=ApprovalDocTest"
```

기대: 컴파일 실패 — `cannot find symbol: class ApprovalDoc`, `class DocType`.
`DocType` 은 다음 Task 에서 만들므로 지금은 **`ApprovalDoc` 과 함께 최소 형태로** 만든다 (Step 4).

- [ ] **Step 4: `DocType` 을 최소 형태로 만든다**

문서번호 생성 규칙은 Task 6 에서 쓰지만, 테스트가 상수를 참조하므로 여기서 만든다.

`src/main/java/com/flowmate/approval/domain/DocType.java`:

```java
package com.flowmate.approval.domain;

/**
 * 문서 유형 5종 (설계서 §12 확정).
 *
 * prefixOf 가 문서번호 접두사를 준다. 문서번호는 {접두사}-{연도}-{4자리 일련번호} 형식이다.
 */
public final class DocType {

    public static final String EXPENSE = "EXPENSE";
    public static final String PURCHASE = "PURCHASE";
    public static final String LEAVE = "LEAVE";
    public static final String CONTRACT = "CONTRACT";
    public static final String GENERAL = "GENERAL";

    private DocType() {
    }

    /** 문서번호 접두사. 알 수 없는 유형은 GEN 으로 떨어뜨린다 */
    public static String prefixOf(String docType) {
        if (EXPENSE.equals(docType)) {
            return "EXP";
        }
        if (PURCHASE.equals(docType)) {
            return "PUR";
        }
        if (LEAVE.equals(docType)) {
            return "LEV";
        }
        if (CONTRACT.equals(docType)) {
            return "CON";
        }
        return "GEN";
    }

    /** 화면에 보여줄 한글 이름 */
    public static String labelOf(String docType) {
        if (EXPENSE.equals(docType)) {
            return "지출결의";
        }
        if (PURCHASE.equals(docType)) {
            return "구매요청";
        }
        if (LEAVE.equals(docType)) {
            return "연차신청";
        }
        if (CONTRACT.equals(docType)) {
            return "계약서";
        }
        return "일반문서";
    }
}
```

- [ ] **Step 5: `ApprovalDoc` 을 구현한다**

`src/main/java/com/flowmate/approval/domain/ApprovalDoc.java`:

```java
package com.flowmate.approval.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 결재 문서. 상태 전이 규칙을 이 객체 안에 가둔다.
 *
 * Service 가 상태 문자열을 직접 바꾸지 않는 것이 이 설계의 요점이다.
 * 허용되지 않는 전이는 여기서 즉시 예외가 되므로, 잘못된 순서로 부르는 코드가
 * DB 에 닿기 전에 죽는다.
 *
 * drafterName / deptName / docTypeLabel 은 조인·변환 결과를 담는 조회 표시용 필드다.
 * approval_doc 테이블의 컬럼이 아니다.
 */
public class ApprovalDoc implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long approvalId;
    private String docNo;
    private String docType;
    private String title;
    private String content;
    private Long drafterId;
    private Long deptId;
    private BigDecimal amount;
    private String status;
    private int currentStep;
    private LocalDateTime draftedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;

    // 조회 표시용
    private String drafterName;
    private String deptName;
    private String drafterPositionName;

    // ── 상태 전이 ────────────────────────────────────────────────

    /**
     * 상신한다.
     *
     * @param approverCount 생성된 결재선의 결재자 수. 0이면 승인할 사람이 없다는 뜻이다.
     */
    public void submit(int approverCount) {
        if (approverCount < 0) {
            throw new IllegalArgumentException("결재자 수는 0 이상이어야 합니다: " + approverCount);
        }
        if (!ApprovalStatus.DRAFT.equals(this.status)) {
            throw new IllegalStateException("임시저장 상태만 상신할 수 있습니다: " + this.status);
        }
        this.submittedAt = LocalDateTime.now();
        if (approverCount == 0) {
            // 결재할 사람이 없다. PENDING 을 거치면 결재선이 빈 채로 대기하는
            // 유령 문서가 되어 내 결재함이 영원히 찾지 못한다.
            this.status = ApprovalStatus.APPROVED;
            this.currentStep = 0;
            this.completedAt = this.submittedAt;
            return;
        }
        this.status = ApprovalStatus.PENDING;
        this.currentStep = 1;
    }

    /**
     * 현재 단계를 승인한다.
     *
     * @param totalStep 이 문서의 전체 결재 단계 수. Service 가 결재선 길이로 넘긴다.
     */
    public void approve(int totalStep) {
        if (totalStep < 1) {
            throw new IllegalArgumentException("전체 단계 수는 1 이상이어야 합니다: " + totalStep);
        }
        if (!ApprovalStatus.PENDING.equals(this.status)) {
            throw new IllegalStateException("결재 진행 중인 문서만 승인할 수 있습니다: " + this.status);
        }
        if (this.currentStep >= totalStep) {
            this.status = ApprovalStatus.APPROVED;
            this.completedAt = LocalDateTime.now();
            return;
        }
        this.currentStep = this.currentStep + 1;
    }

    /** 반려한다. 남은 단계는 Service 가 SKIPPED 로 정리한다. */
    public void reject() {
        if (!ApprovalStatus.PENDING.equals(this.status)) {
            throw new IllegalStateException("결재 진행 중인 문서만 반려할 수 있습니다: " + this.status);
        }
        this.status = ApprovalStatus.REJECTED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 기안자가 회수한다.
     *
     * 임시저장이거나, 상신했더라도 아직 아무도 승인하지 않았을 때만 가능하다.
     * currentStep 이 1이라는 것은 1단계가 아직 대기 중이라는 뜻이므로 승인 이력이 없다.
     */
    public void cancel(Long actorId) {
        if (!Objects.equals(this.drafterId, actorId)) {
            throw new IllegalStateException("기안자만 회수할 수 있습니다");
        }
        boolean cancelableDraft = ApprovalStatus.DRAFT.equals(this.status);
        boolean cancelablePending = ApprovalStatus.PENDING.equals(this.status) && this.currentStep <= 1;
        if (!cancelableDraft && !cancelablePending) {
            throw new IllegalStateException(
                    "이미 결재가 진행된 문서는 회수할 수 없습니다: " + this.status + " step=" + this.currentStep);
        }
        this.status = ApprovalStatus.CANCELED;
        this.completedAt = LocalDateTime.now();
    }

    // ── 판정 ────────────────────────────────────────────────────

    /** 더 이상 상태가 바뀌지 않는가 */
    public boolean isCompleted() {
        return ApprovalStatus.isTerminal(this.status);
    }

    /** 기안자가 내용을 고칠 수 있는가 */
    public boolean isEditable() {
        return ApprovalStatus.DRAFT.equals(this.status);
    }

    /** 주어진 단계가 지금 처리를 기다리는 단계인가 */
    public boolean isAwaitingStep(int stepNo) {
        return ApprovalStatus.PENDING.equals(this.status) && this.currentStep == stepNo;
    }

    /** 화면 표시용 문서 유형 한글명 */
    public String getDocTypeLabel() {
        return DocType.labelOf(this.docType);
    }

    // ── getter / setter ─────────────────────────────────────────

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public String getDocNo() {
        return docNo;
    }

    public void setDocNo(String docNo) {
        this.docNo = docNo;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getDrafterId() {
        return drafterId;
    }

    public void setDrafterId(Long drafterId) {
        this.drafterId = drafterId;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(int currentStep) {
        this.currentStep = currentStep;
    }

    public LocalDateTime getDraftedAt() {
        return draftedAt;
    }

    public void setDraftedAt(LocalDateTime draftedAt) {
        this.draftedAt = draftedAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getDrafterName() {
        return drafterName;
    }

    public void setDrafterName(String drafterName) {
        this.drafterName = drafterName;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getDrafterPositionName() {
        return drafterPositionName;
    }

    public void setDrafterPositionName(String drafterPositionName) {
        this.drafterPositionName = drafterPositionName;
    }
}
```

> **`submit()` 이 상태 검사보다 인자 검사를 먼저 하는 이유:** `submit(-1)` 은 호출자의 버그이지
> 문서 상태의 문제가 아니다. 상태 검사를 먼저 하면 DRAFT 가 아닐 때 `-1` 이 묻혀 버린다.

- [ ] **Step 6: 테스트가 통과하는지 확인한다**

```powershell
.\mvnw.cmd test "-Dtest=ApprovalDocTest"
```

기대: `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 7: 전체 단위 테스트를 돌린다**

```powershell
.\mvnw.cmd test
```

기대: `Tests run: 39, Failures: 0, Errors: 0, Skipped: 0` (기존 22 + 17), **DB 없이** 통과.

- [ ] **Step 8: 커밋한다**

```powershell
git add src/main/java/com/flowmate/approval/domain src/test/java/com/flowmate/approval/domain
git status -s
```

메시지:

```
feat: 결재 문서 상태 기계와 단위 테스트 17건

상태 전이를 ApprovalDoc 안에 가둔다. Service 가 상태 문자열을 직접 바꾸지 않으므로
잘못된 순서로 부르는 코드가 DB 에 닿기 전에 IllegalStateException 으로 죽는다.

결재자가 0명이면 상신 즉시 완료시킨다. PENDING 을 거치면 결재선이 빈 채로 대기하는
유령 문서가 되어 내 결재함이 영원히 찾지 못한다.

회수 가능 조건을 currentStep <= 1 로 판정한다. currentStep 이 1이라는 것은
1단계가 아직 대기 중이라는 뜻이므로 승인 이력이 없다.

상태를 enum 이 아니라 String 상수로 둔다. MyBatis 가 VARCHAR 를 그대로 읽고
JSP EL 이 문자열 비교를 그대로 쓸 수 있어야 한다.
```

---

## Task 3: `ApprovalLinePolicy` 와 기본 구현 (TDD) ★

> 설계서 §7 이 꼽은 **커스터마이징 지점 1번**. "고객사마다 결재선이 다르다"를 코드로 증명하는 부분이다.
> 로드맵 §5.1 의 검증 표를 그대로 테스트로 고정한다.

### ★ 설계서와 다른 점 — 인터페이스 시그니처

설계서 §6.2 는 이렇게 적었다.

```java
public interface ApprovalLinePolicy {
    List<ApprovalLine> determineLines(ApprovalDoc doc, Employee drafter);
}
```

**이 시그니처를 쓰지 않는다.** 이 형태라면 정책 구현체가 부서 트리와 부서장을 스스로 조회해야 하므로
매퍼를 주입받아야 하고, 그러면 **DB 없이 단위 테스트할 수 없다.**
그런데 설계서 §8 은 "결재선 정책 2종 | JUnit **단위** | 6건" 을 명시한다 — 설계서의 두 요구가 서로 충돌한다.

테스트 가능성을 택한다. 조회는 Service 가 하고 정책은 **후보 목록을 받아 계산만** 한다.

```java
List<ApprovalLine> determineLines(ApprovalDoc doc, ApproverCandidate drafter,
                                  List<ApproverCandidate> deptHeadChain);
```

`deptHeadChain` 은 기안자 부서에서 루트까지 각 부서의 최고 직급자 1명, **가까운 부서가 먼저**다.
Task 5 에서 재귀 CTE 한 번으로 이 목록을 만든다.

**Files:**
- Create: `src/main/java/com/flowmate/approval/domain/LineStatus.java`
- Create: `src/main/java/com/flowmate/approval/domain/LineType.java`
- Create: `src/main/java/com/flowmate/approval/domain/ApprovalLine.java`
- Create: `src/main/java/com/flowmate/approval/policy/ApproverCandidate.java`
- Test: `src/test/java/com/flowmate/approval/policy/DefaultApprovalLinePolicyTest.java`
- Create: `src/main/java/com/flowmate/approval/policy/ApprovalLinePolicy.java`
- Create: `src/main/java/com/flowmate/approval/policy/DefaultApprovalLinePolicy.java`

- [ ] **Step 1: 결재선 상수와 도메인, 후보 값 객체를 만든다**

`LineStatus.java`:

```java
package com.flowmate.approval.domain;

/** 결재선 한 단계의 상태 */
public final class LineStatus {

    /** 아직 순서가 오지 않음 */
    public static final String WAITING = "WAITING";
    /** 지금 이 사람이 처리할 차례 */
    public static final String CURRENT = "CURRENT";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    /** 앞 단계에서 반려되어 차례가 오지 않음 */
    public static final String SKIPPED = "SKIPPED";

    private LineStatus() {
    }
}
```

`LineType.java`:

```java
package com.flowmate.approval.domain;

/**
 * 결재선 종류.
 *
 * Phase 2 는 APPROVAL 만 쓴다. AGREEMENT/REFERENCE 는 설계서 §9 가 정한
 * 잘라내기 순서에서 2순위이므로 상수만 두고 화면·로직은 만들지 않는다.
 */
public final class LineType {

    /** 결재 — 승인/반려 권한이 있다 */
    public static final String APPROVAL = "APPROVAL";
    /** 합의 — 의견만 남긴다 (Phase 2 범위 밖) */
    public static final String AGREEMENT = "AGREEMENT";
    /** 참조 — 열람만 한다 (Phase 2 범위 밖) */
    public static final String REFERENCE = "REFERENCE";

    private LineType() {
    }
}
```

`ApprovalLine.java`:

```java
package com.flowmate.approval.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 결재선 한 단계.
 *
 * approverName / approverPositionName / approverDeptName 은 조인 결과를 담는
 * 조회 표시용 파생 필드다. approval_line 테이블의 컬럼이 아니다.
 */
public class ApprovalLine implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long lineId;
    private Long approvalId;
    private int stepNo;
    private Long approverId;
    private String lineType;
    private String status;
    private String comment;
    private LocalDateTime processedAt;

    // 조회 표시용
    private String approverName;
    private String approverPositionName;
    private String approverDeptName;

    /** 이 단계가 지금 처리를 기다리는가 */
    public boolean isCurrent() {
        return LineStatus.CURRENT.equals(this.status);
    }

    /** 처리가 끝난 단계인가 */
    public boolean isProcessed() {
        return LineStatus.APPROVED.equals(this.status) || LineStatus.REJECTED.equals(this.status);
    }

    public Long getLineId() {
        return lineId;
    }

    public void setLineId(Long lineId) {
        this.lineId = lineId;
    }

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public int getStepNo() {
        return stepNo;
    }

    public void setStepNo(int stepNo) {
        this.stepNo = stepNo;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    public String getLineType() {
        return lineType;
    }

    public void setLineType(String lineType) {
        this.lineType = lineType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public String getApproverName() {
        return approverName;
    }

    public void setApproverName(String approverName) {
        this.approverName = approverName;
    }

    public String getApproverPositionName() {
        return approverPositionName;
    }

    public void setApproverPositionName(String approverPositionName) {
        this.approverPositionName = approverPositionName;
    }

    public String getApproverDeptName() {
        return approverDeptName;
    }

    public void setApproverDeptName(String approverDeptName) {
        this.approverDeptName = approverDeptName;
    }
}
```

`src/main/java/com/flowmate/approval/policy/ApproverCandidate.java`:

```java
package com.flowmate.approval.policy;

/**
 * 결재선 정책이 판정에 쓰는 후보 1명. 불변 값 객체다.
 *
 * Employee 를 그대로 넘기지 않는 이유:
 * 정책이 필요한 것은 사원번호·부서·직급뿐이고, Employee 에는 passwordHash 가 있다.
 * 정책 단위 테스트에서 Employee 전체를 조립하는 것도 불필요하게 무겁다.
 */
public class ApproverCandidate {

    private final Long empId;
    private final String empName;
    private final Long deptId;
    private final int positionLevel;
    private final String positionName;

    public ApproverCandidate(Long empId, String empName, Long deptId,
                             int positionLevel, String positionName) {
        this.empId = empId;
        this.empName = empName;
        this.deptId = deptId;
        this.positionLevel = positionLevel;
        this.positionName = positionName;
    }

    public Long getEmpId() {
        return empId;
    }

    public String getEmpName() {
        return empName;
    }

    public Long getDeptId() {
        return deptId;
    }

    public int getPositionLevel() {
        return positionLevel;
    }

    public String getPositionName() {
        return positionName;
    }
}
```

- [ ] **Step 2: 실패하는 테스트를 쓴다 — 로드맵 §5.1 검증 표를 그대로 옮긴다**

`src/test/java/com/flowmate/approval/policy/DefaultApprovalLinePolicyTest.java`:

```java
package com.flowmate.approval.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.LineStatus;
import com.flowmate.approval.domain.LineType;

/**
 * 기본 결재선 정책. 로드맵 §5.1 이 확정한 규칙과 검증 표를 그대로 고정한다.
 *
 * 후보 목록을 인자로 받으므로 DB 없이 돈다 — 이것이 이 인터페이스를 설계서 원안과
 * 다르게 잡은 이유다.
 */
class DefaultApprovalLinePolicyTest {

    private final ApprovalLinePolicy policy = new DefaultApprovalLinePolicy();

    // 시드 조직도: 대표이사실(1) → 경영지원본부(2) · 사업본부(3) → 인사팀(4) · 재무팀(5) · 마케팅팀(6) · 개발팀(7)
    private static final ApproverCandidate KWAK   = cand(18L, "곽수빈", 7L, 1, "사원");
    private static final ApproverCandidate SHIN   = cand(14L, "신동혁", 7L, 3, "과장");
    private static final ApproverCandidate PARK   = cand(3L,  "박현주", 3L, 5, "부장");
    private static final ApproverCandidate JEONG  = cand(1L,  "정도현", 1L, 6, "이사");
    private static final ApproverCandidate SEO    = cand(6L,  "서다인", 4L, 1, "사원");
    private static final ApproverCandidate CHOI   = cand(4L,  "최민석", 4L, 4, "차장");
    private static final ApproverCandidate KIM    = cand(2L,  "김성일", 2L, 5, "부장");

    /** 개발팀 사원이 기안했을 때의 부서장 체인: 개발팀 → 사업본부 → 대표이사실 */
    private static final List<ApproverCandidate> DEV_CHAIN = List.of(SHIN, PARK, JEONG);
    /** 인사팀 사원이 기안했을 때의 체인: 인사팀 → 경영지원본부 → 대표이사실 */
    private static final List<ApproverCandidate> HR_CHAIN = List.of(CHOI, KIM, JEONG);
    /** 사업본부 부장이 기안했을 때의 체인: 사업본부 → 대표이사실 */
    private static final List<ApproverCandidate> BIZ_CHAIN = List.of(PARK, JEONG);
    /** 대표이사실 이사가 기안했을 때의 체인: 대표이사실뿐 */
    private static final List<ApproverCandidate> CEO_CHAIN = List.of(JEONG);

    private static ApproverCandidate cand(Long id, String name, Long deptId, int level, String position) {
        return new ApproverCandidate(id, name, deptId, level, position);
    }

    private static ApprovalDoc doc(ApproverCandidate drafter, String amount) {
        ApprovalDoc d = new ApprovalDoc();
        d.setApprovalId(100L);
        d.setDocType(DocType.EXPENSE);
        d.setTitle("테스트 문서");
        d.setDrafterId(drafter.getEmpId());
        d.setDeptId(drafter.getDeptId());
        d.setAmount(new BigDecimal(amount));
        d.setStatus(ApprovalStatus.DRAFT);
        return d;
    }

    @Test
    @DisplayName("사원이 소액을 기안하면 팀장과 본부장 2단계가 된다 - 설계서 완료 기준과 같은 모양")
    void staffSmallAmountGetsTeamLeadAndDivisionHead() {
        List<ApprovalLine> lines = policy.determineLines(doc(KWAK, "1000000"), KWAK, DEV_CHAIN);

        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(14L, 3L);
        assertThat(lines).extracting(ApprovalLine::getStepNo).containsExactly(1, 2);
    }

    @Test
    @DisplayName("금액이 300만원을 넘으면 이사가 마지막에 붙는다")
    void largeAmountAppendsExecutive() {
        List<ApprovalLine> lines = policy.determineLines(doc(KWAK, "5000000"), KWAK, DEV_CHAIN);

        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(14L, 3L, 1L);
        assertThat(lines).extracting(ApprovalLine::getStepNo).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("300만원 정확히면 임원이 붙지 않는다 - 초과일 때만 붙는다")
    void exactThresholdDoesNotAppendExecutive() {
        List<ApprovalLine> lines = policy.determineLines(doc(KWAK, "3000000"), KWAK, DEV_CHAIN);

        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(14L, 3L);
    }

    @Test
    @DisplayName("기안자가 자기 부서 최고 직급이면 그 부서는 건너뛴다")
    void skipsOwnDepartmentWhenDrafterIsItsHead() {
        List<ApprovalLine> lines = policy.determineLines(doc(SHIN, "1000000"), SHIN, DEV_CHAIN);

        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(3L);
    }

    @Test
    @DisplayName("다른 본부에서도 같은 모양으로 2단계가 만들어진다")
    void sameShapeInAnotherDivision() {
        List<ApprovalLine> lines = policy.determineLines(doc(SEO, "1000000"), SEO, HR_CHAIN);

        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(4L, 2L);
    }

    @Test
    @DisplayName("결재자가 아무도 없으면 소액이어도 이사를 붙인다 - 빈 결재선 방지")
    void appendsExecutiveWhenLineWouldBeEmpty() {
        List<ApprovalLine> lines = policy.determineLines(doc(PARK, "1000000"), PARK, BIZ_CHAIN);

        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(1L);
    }

    @Test
    @DisplayName("이사가 기안하면 결재자가 없다 - 상신 즉시 완료되는 경로")
    void executiveDraftHasNoApprover() {
        List<ApprovalLine> lines = policy.determineLines(doc(JEONG, "1000000"), JEONG, CEO_CHAIN);

        assertThat(lines).isEmpty();
    }

    @Test
    @DisplayName("이사가 큰 금액을 기안해도 자기 자신을 결재자로 넣지 않는다")
    void executiveDraftDoesNotSelfApproveEvenForLargeAmount() {
        List<ApprovalLine> lines = policy.determineLines(doc(JEONG, "50000000"), JEONG, CEO_CHAIN);

        assertThat(lines).isEmpty();
    }

    @Test
    @DisplayName("생성된 결재선은 전부 대기 상태이고 결재 종류다")
    void createdLinesAreWaitingApprovalType() {
        List<ApprovalLine> lines = policy.determineLines(doc(KWAK, "1000000"), KWAK, DEV_CHAIN);

        assertThat(lines).allSatisfy(line -> {
            assertThat(line.getStatus()).isEqualTo(LineStatus.WAITING);
            assertThat(line.getLineType()).isEqualTo(LineType.APPROVAL);
            assertThat(line.getApprovalId()).isEqualTo(100L);
            assertThat(line.getProcessedAt()).isNull();
        });
    }

    @Test
    @DisplayName("금액이 null 이어도 예외 없이 소액으로 취급한다")
    void nullAmountIsTreatedAsSmall() {
        ApprovalDoc d = doc(KWAK, "0");
        d.setAmount(null);

        List<ApprovalLine> lines = policy.determineLines(d, KWAK, DEV_CHAIN);

        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(14L, 3L);
    }
}
```

- [ ] **Step 3: 테스트가 실패하는지 확인한다**

```powershell
.\mvnw.cmd test "-Dtest=DefaultApprovalLinePolicyTest"
```

기대: 컴파일 실패 — `cannot find symbol: class ApprovalLinePolicy`, `class DefaultApprovalLinePolicy`.

- [ ] **Step 4: 인터페이스를 만든다**

`src/main/java/com/flowmate/approval/policy/ApprovalLinePolicy.java`:

```java
package com.flowmate.approval.policy;

import java.util.ArrayList;
import java.util.List;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.LineStatus;
import com.flowmate.approval.domain.LineType;

/**
 * ★ 커스터마이징 지점 1 — 고객사마다 결재선 규칙이 다르다.
 *
 * 조회를 하지 않고 넘겨받은 후보로 계산만 하는 이유:
 * 매퍼를 주입받으면 DB 없이 단위 테스트할 수 없다. 설계서 §8 이 이 정책을
 * "JUnit 단위 테스트" 대상으로 잡았으므로 순수 로직으로 유지한다.
 * 조회는 ApprovalService 가 담당한다.
 */
public interface ApprovalLinePolicy {

    /**
     * 결재선을 만든다.
     *
     * @param doc           결재선을 붙일 문서. docType 과 amount 를 참조한다
     * @param drafter       기안자
     * @param deptHeadChain 기안자 부서에서 루트까지 각 부서의 최고 직급자 1명.
     *                      **가까운 부서가 먼저**여야 한다
     * @return 1단계부터 순서대로. 결재자가 없으면 빈 리스트
     */
    List<ApprovalLine> determineLines(ApprovalDoc doc, ApproverCandidate drafter,
                                      List<ApproverCandidate> deptHeadChain);

    /**
     * 결재자 목록을 결재선으로 바꾼다. 구현체가 공유하는 조립 로직이다.
     *
     * 전부 WAITING 으로 만든다. 결재선은 기안(임시저장) 시점에 생성되므로
     * 아직 아무 단계도 진행 중이 아니다. 1단계를 CURRENT 로 바꾸는 것은
     * 상신 시점에 ApprovalService 가 한다.
     */
    default List<ApprovalLine> toApprovalLines(Long approvalId, List<ApproverCandidate> approvers) {
        List<ApprovalLine> lines = new ArrayList<>();
        int stepNo = 1;
        for (ApproverCandidate approver : approvers) {
            ApprovalLine line = new ApprovalLine();
            line.setApprovalId(approvalId);
            line.setStepNo(stepNo);
            line.setApproverId(approver.getEmpId());
            line.setLineType(LineType.APPROVAL);
            line.setStatus(LineStatus.WAITING);
            line.setApproverName(approver.getEmpName());
            line.setApproverPositionName(approver.getPositionName());
            lines.add(line);
            stepNo++;
        }
        return lines;
    }
}
```

- [ ] **Step 5: 기본 구현을 만든다**

`src/main/java/com/flowmate/approval/policy/DefaultApprovalLinePolicy.java`:

```java
package com.flowmate.approval.policy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalLine;

/**
 * 기본 결재선 정책 — 부서 트리를 올라가며 상위 결재자를 모으고, 큰 금액이면 임원을 붙인다.
 *
 * 규칙 (로드맵 §5.1 확정):
 *   1. 기안자 부서에서 루트까지 올라간다
 *   2. 각 부서의 최고 직급자 1명이 후보다
 *   3. 기안자보다 직급이 높고, 임원 미만이고, 본인이 아니면 결재자로 추가
 *   4. 금액이 기준을 넘으면 임원을 마지막에 추가
 *   5. 그래도 결재선이 비면 임원을 추가 (빈 결재선 방지)
 *   6. 그래도 비면 결재자 0명 — 상신 즉시 완료된다
 *
 * ★ 3번에서 임원을 걸러내는 것이 이 정책의 핵심이다.
 *   걸러내지 않으면 루트 부서장(이사)이 항상 체인에 있으므로 결재선에 늘 들어오고,
 *   4번의 금액 조건이 아무 의미가 없어진다.
 */
public class DefaultApprovalLinePolicy implements ApprovalLinePolicy {

    /** 이 직급 이상을 임원으로 본다. 시드에서는 이사(6)만 해당한다 */
    private static final int EXECUTIVE_LEVEL = 6;

    /** 이 금액을 **초과**하면 임원 결재가 붙는다 */
    private static final BigDecimal EXECUTIVE_THRESHOLD = new BigDecimal("3000000");

    @Override
    public List<ApprovalLine> determineLines(ApprovalDoc doc, ApproverCandidate drafter,
                                             List<ApproverCandidate> deptHeadChain) {
        List<ApproverCandidate> approvers = new ArrayList<>();

        for (ApproverCandidate head : deptHeadChain) {
            if (head.getPositionLevel() <= drafter.getPositionLevel()) {
                continue;   // 기안자와 같거나 낮은 직급은 결재자가 아니다
            }
            if (head.getPositionLevel() >= EXECUTIVE_LEVEL) {
                continue;   // ★ 임원은 트리 탐색으로 넣지 않는다. 금액 규칙으로만 붙인다
            }
            if (Objects.equals(head.getEmpId(), drafter.getEmpId())) {
                continue;   // 본인
            }
            if (containsEmp(approvers, head.getEmpId())) {
                continue;   // 같은 사람이 두 부서의 장인 경우
            }
            approvers.add(head);
        }

        ApproverCandidate executive = findExecutive(deptHeadChain);
        if (executive != null && !Objects.equals(executive.getEmpId(), drafter.getEmpId())) {
            boolean overThreshold = doc.getAmount() != null
                    && doc.getAmount().compareTo(EXECUTIVE_THRESHOLD) > 0;
            boolean wouldBeEmpty = approvers.isEmpty();
            if ((overThreshold || wouldBeEmpty) && !containsEmp(approvers, executive.getEmpId())) {
                approvers.add(executive);
            }
        }

        return toApprovalLines(doc.getApprovalId(), approvers);
    }

    /** 체인에서 가장 직급이 높은 임원. 없으면 null */
    private ApproverCandidate findExecutive(List<ApproverCandidate> deptHeadChain) {
        ApproverCandidate found = null;
        for (ApproverCandidate c : deptHeadChain) {
            if (c.getPositionLevel() < EXECUTIVE_LEVEL) {
                continue;
            }
            if (found == null || c.getPositionLevel() > found.getPositionLevel()) {
                found = c;
            }
        }
        return found;
    }

    private boolean containsEmp(List<ApproverCandidate> list, Long empId) {
        for (ApproverCandidate c : list) {
            if (Objects.equals(c.getEmpId(), empId)) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 6: 테스트가 통과하는지 확인한다**

```powershell
.\mvnw.cmd test "-Dtest=DefaultApprovalLinePolicyTest"
```

기대: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`

실패 시 진단:

| 증상 | 원인 |
|---|---|
| 결재선에 이사가 항상 포함됨 | 규칙 3의 `>= EXECUTIVE_LEVEL` 필터 누락 |
| 3,000,000 에서 이사가 붙음 | `compareTo(...) >= 0` 으로 썼다. **초과**여야 하므로 `> 0` |
| 박현주 기안 시 결재선이 빔 | 규칙 5(빈 결재선 방지) 누락 |
| 이사 기안 시 자기 자신이 들어옴 | `executive` 의 본인 비교 누락 |

- [ ] **Step 7: 커밋한다**

```powershell
git add src/main/java/com/flowmate/approval src/test/java/com/flowmate/approval/policy
git status -s
```

메시지:

```
feat: 결재선 정책 인터페이스와 기본 구현

설계서 §6.2 의 determineLines(doc, drafter) 시그니처를 쓰지 않는다. 그 형태라면
정책이 부서 트리를 스스로 조회해야 해서 매퍼를 주입받게 되고, DB 없이 단위 테스트할 수 없다.
설계서 §8 이 이 정책을 단위 테스트 대상으로 잡았으므로 후보 목록을 받아 계산만 하게 바꿨다.

트리 탐색에서 임원을 걸러내는 것이 핵심이다. 걸러내지 않으면 루트 부서장인 이사가
항상 체인에 있어 결재선에 늘 들어오고, 금액 조건이 아무 의미가 없어진다.

로드맵 §5.1 의 검증 표 6가지를 테스트로 그대로 고정했다.
```

---

## Task 4: `SimpleTwoStepLinePolicy` 와 교체 시연 (TDD)

> 설계서 §7 은 **"각 지점마다 구현체를 2개씩 만들어 교체를 실제로 시연한다"** 고 했다.
> 구현체 하나만 있으면 인터페이스는 장식이다. 같은 입력에 다른 결재선이 나오는 것을
> 나란히 놓은 테스트가 이 Phase 의 커스터마이징 증명이다.

**Files:**
- Test: `src/test/java/com/flowmate/approval/policy/SimpleTwoStepLinePolicyTest.java`
- Create: `src/main/java/com/flowmate/approval/policy/SimpleTwoStepLinePolicy.java`
- Create: `src/main/java/com/flowmate/config/ApprovalPolicyConfig.java`
- Modify: `src/main/resources/application.yml`

- [ ] **Step 1: 실패하는 테스트를 쓴다**

`src/test/java/com/flowmate/approval/policy/SimpleTwoStepLinePolicyTest.java`:

```java
package com.flowmate.approval.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.DocType;

/**
 * 소규모 고객사용 결재선 정책 — 소속 부서장 1명으로 끝난다.
 *
 * 이 클래스의 존재 이유는 기능이 아니라 **교체 가능성의 증명**이다.
 * 마지막 테스트가 같은 입력에 두 정책이 서로 다른 결재선을 내는 것을 나란히 보여준다.
 */
class SimpleTwoStepLinePolicyTest {

    private final ApprovalLinePolicy simple = new SimpleTwoStepLinePolicy();
    private final ApprovalLinePolicy defaultPolicy = new DefaultApprovalLinePolicy();

    private static final ApproverCandidate KWAK  = new ApproverCandidate(18L, "곽수빈", 7L, 1, "사원");
    private static final ApproverCandidate SHIN  = new ApproverCandidate(14L, "신동혁", 7L, 3, "과장");
    private static final ApproverCandidate PARK  = new ApproverCandidate(3L,  "박현주", 3L, 5, "부장");
    private static final ApproverCandidate JEONG = new ApproverCandidate(1L,  "정도현", 1L, 6, "이사");

    private static final List<ApproverCandidate> DEV_CHAIN = List.of(SHIN, PARK, JEONG);

    private static ApprovalDoc doc(ApproverCandidate drafter, String amount) {
        ApprovalDoc d = new ApprovalDoc();
        d.setApprovalId(200L);
        d.setDocType(DocType.PURCHASE);
        d.setTitle("테스트 문서");
        d.setDrafterId(drafter.getEmpId());
        d.setDeptId(drafter.getDeptId());
        d.setAmount(new BigDecimal(amount));
        d.setStatus(ApprovalStatus.DRAFT);
        return d;
    }

    @Test
    @DisplayName("소속 부서장 1명만 결재자로 둔다")
    void onlyImmediateDepartmentHead() {
        List<ApprovalLine> lines = simple.determineLines(doc(KWAK, "1000000"), KWAK, DEV_CHAIN);

        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(14L);
        assertThat(lines).extracting(ApprovalLine::getStepNo).containsExactly(1);
    }

    @Test
    @DisplayName("기안자가 자기 부서 최고 직급이면 결재선이 비어 있다")
    void emptyWhenDrafterIsOwnDepartmentHead() {
        List<ApprovalLine> lines = simple.determineLines(doc(SHIN, "1000000"), SHIN, DEV_CHAIN);

        assertThat(lines).isEmpty();
    }

    @Test
    @DisplayName("★ 같은 입력에 두 정책이 서로 다른 결재선을 만든다 - 교체 가능성의 증명")
    void twoPoliciesProduceDifferentLinesForSameInput() {
        ApprovalDoc largeAmount = doc(KWAK, "5000000");

        List<ApprovalLine> byDefault = defaultPolicy.determineLines(largeAmount, KWAK, DEV_CHAIN);
        List<ApprovalLine> bySimple = simple.determineLines(largeAmount, KWAK, DEV_CHAIN);

        // 기본 정책: 팀장 → 본부장 → 이사 (금액이 크므로 임원까지)
        assertThat(byDefault).extracting(ApprovalLine::getApproverId).containsExactly(14L, 3L, 1L);
        // 소규모 정책: 팀장 한 명. 금액을 보지 않는다
        assertThat(bySimple).extracting(ApprovalLine::getApproverId).containsExactly(14L);

        assertThat(bySimple).hasSizeLessThan(byDefault.size());
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```powershell
.\mvnw.cmd test "-Dtest=SimpleTwoStepLinePolicyTest"
```

기대: 컴파일 실패 — `cannot find symbol: class SimpleTwoStepLinePolicy`.

- [ ] **Step 3: 구현체를 만든다**

`src/main/java/com/flowmate/approval/policy/SimpleTwoStepLinePolicy.java`:

```java
package com.flowmate.approval.policy;

import java.util.List;
import java.util.Objects;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalLine;

/**
 * 소규모 고객사용 결재선 정책 — 기안자 → 소속 부서장 2단계로 고정한다.
 *
 * 기본 정책과 다른 점:
 *   - 부서 트리를 오르지 않는다. 체인의 첫 항목(소속 부서)만 본다
 *   - 금액을 보지 않는다. 임원 결재가 붙지 않는다
 *
 * 조직이 작아 본부 계층이 사실상 없는 고객사를 상정한 것이다.
 * 결재 단계가 늘어나면 오히려 업무가 막히는 규모에서 쓴다.
 */
public class SimpleTwoStepLinePolicy implements ApprovalLinePolicy {

    @Override
    public List<ApprovalLine> determineLines(ApprovalDoc doc, ApproverCandidate drafter,
                                             List<ApproverCandidate> deptHeadChain) {
        if (deptHeadChain.isEmpty()) {
            return toApprovalLines(doc.getApprovalId(), List.of());
        }
        ApproverCandidate head = deptHeadChain.get(0);
        if (Objects.equals(head.getEmpId(), drafter.getEmpId())) {
            return toApprovalLines(doc.getApprovalId(), List.of());
        }
        if (head.getPositionLevel() <= drafter.getPositionLevel()) {
            return toApprovalLines(doc.getApprovalId(), List.of());
        }
        return toApprovalLines(doc.getApprovalId(), List.of(head));
    }
}
```

- [ ] **Step 4: 설정으로 교체 가능하게 배선한다**

`src/main/java/com/flowmate/config/ApprovalPolicyConfig.java`:

```java
package com.flowmate.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.flowmate.approval.policy.ApprovalLinePolicy;
import com.flowmate.approval.policy.DefaultApprovalLinePolicy;
import com.flowmate.approval.policy.SimpleTwoStepLinePolicy;

/**
 * ★ 커스터마이징 지점의 교체 배선.
 *
 * application.yml 의 flowmate.approval.line-policy 값으로 구현체를 바꾼다.
 *   default      → DefaultApprovalLinePolicy (부서 트리 상향 + 금액별 임원)
 *   simple-two-step → SimpleTwoStepLinePolicy (소속 부서장 1명)
 *
 * 설정이 없으면 default 를 쓴다. 고객사별 납품에서 코드를 고치지 않고
 * 설정 파일만 바꿔 결재 규칙을 교체하는 것이 이 구조의 목적이다.
 */
@Configuration
public class ApprovalPolicyConfig {

    @Bean
    @ConditionalOnProperty(name = "flowmate.approval.line-policy", havingValue = "simple-two-step")
    public ApprovalLinePolicy simpleTwoStepLinePolicy() {
        return new SimpleTwoStepLinePolicy();
    }

    @Bean
    @ConditionalOnProperty(name = "flowmate.approval.line-policy", havingValue = "default",
                           matchIfMissing = true)
    public ApprovalLinePolicy defaultApprovalLinePolicy() {
        return new DefaultApprovalLinePolicy();
    }
}
```

> `matchIfMissing = true` 를 기본 구현에만 두었으므로 설정이 없어도 빈이 정확히 하나 만들어진다.
> 두 조건이 배타적이라 `@Primary` 나 `@Qualifier` 가 필요 없다.

`src/main/resources/application.yml` 의 `logging:` 블록 **앞에** 추가한다:

```yaml
flowmate:
  approval:
    # 결재선 정책 교체 지점. default | simple-two-step
    # 고객사별 납품에서 코드를 고치지 않고 이 값만 바꾼다.
    line-policy: default
```

- [ ] **Step 5: 테스트가 통과하는지 확인한다**

```powershell
.\mvnw.cmd test
```

기대: `Tests run: 52, Failures: 0, Errors: 0, Skipped: 0`
(기존 22 + ApprovalDoc 17 + DefaultPolicy 10 + SimpleTwoStep 3)

**설계서 §10 의 "단위 테스트 40건 이상"을 여기서 넘긴다.**

- [ ] **Step 6: 컨텍스트가 뜨는지 확인한다**

정책 빈이 둘 다 만들어지면 주입이 모호해져 컨텍스트가 실패한다. 그것을 여기서 잡는다.

```powershell
docker compose ps
.\mvnw.cmd verify "-Dit.test=FlowmateApplicationIT"
```

기대: `Tests run: 1`, `BUILD SUCCESS`.
`NoUniqueBeanDefinitionException` 이 나면 `@ConditionalOnProperty` 조건이 겹친 것이다.

- [ ] **Step 7: 교체가 실제로 되는지 확인한다**

```powershell
.\mvnw.cmd verify "-Dit.test=FlowmateApplicationIT" "-Dspring-boot.run.arguments=--flowmate.approval.line-policy=simple-two-step"
```

이 방식이 통하지 않으면 `application.yml` 의 값을 `simple-two-step` 으로 잠깐 바꿔
`verify` 를 돌린 뒤 되돌린다. 확인할 것은 **컨텍스트가 뜨는지**다 — 빈이 교체되어도 배선이 깨지지 않아야 한다.
확인 후 반드시 `default` 로 되돌린다.

- [ ] **Step 8: 커밋한다**

```powershell
git add src/main/java/com/flowmate/approval/policy src/main/java/com/flowmate/config/ApprovalPolicyConfig.java src/main/resources/application.yml src/test/java/com/flowmate/approval/policy
git status -s
```

메시지:

```
feat: 소규모 고객사용 결재선 정책과 설정 기반 교체 배선

구현체가 하나면 인터페이스는 장식이다. 같은 입력에 두 정책이 서로 다른 결재선을 내는 것을
나란히 놓은 테스트가 이 Phase 의 커스터마이징 증명이다.

곽수빈이 500만원을 기안하면 기본 정책은 팀장/본부장/이사 3단계를, 소규모 정책은
팀장 1명만 만든다. 소규모 정책은 부서 트리를 오르지 않고 금액도 보지 않는다.

ConditionalOnProperty 두 개를 배타적으로 두어 설정이 없어도 빈이 정확히 하나만 생긴다.
Primary 나 Qualifier 가 필요없다.
```

---

## Task 5: 매퍼와 부서장 체인 조회 (TDD)

> 정책이 받을 `deptHeadChain` 을 만드는 쿼리가 이 Task 의 핵심이다.
> **조직도에서 쓴 재귀 CTE 를 방향만 뒤집어 재사용한다** — 아래로 내려가는 대신 위로 올라간다.
> 공고의 SQL 항목을 한 번 더 증명하는 지점이다.

### 모듈 경계

설계서 §4.3: *"모듈 간 호출은 Service 인터페이스 경유"*. 따라서 `approval` 은
`org.mapper.DepartmentMapper` 를 직접 부르지 않고 `org.service.DepartmentService` 를 부른다.
그리고 **`org` 이 `approval` 의 타입(`ApproverCandidate`)을 알지 않게** 한다 —
`DepartmentService` 는 자기 모듈 타입인 `List<Employee>` 를 돌려주고, 변환은 `ApprovalService` 가 한다.

**Files:**
- Modify: `src/main/java/com/flowmate/org/mapper/DepartmentMapper.java`
- Modify: `src/main/resources/mapper/org/DepartmentMapper.xml`
- Modify: `src/main/java/com/flowmate/org/service/DepartmentService.java`
- Create: `src/main/java/com/flowmate/approval/domain/ApprovalHistory.java`
- Create: `src/main/java/com/flowmate/approval/domain/RejectHistory.java`
- Create: `src/main/java/com/flowmate/approval/domain/RejectReason.java`
- Create: `src/main/java/com/flowmate/approval/mapper/ApprovalDocMapper.java` + XML
- Create: `src/main/java/com/flowmate/approval/mapper/ApprovalLineMapper.java` + XML
- Create: `src/main/java/com/flowmate/approval/mapper/ApprovalHistoryMapper.java` + XML
- Create: `src/main/java/com/flowmate/approval/mapper/RejectHistoryMapper.java` + XML
- Test: `src/test/java/com/flowmate/approval/mapper/ApprovalDocMapperIT.java`
- Modify: `docs/oracle-mapping.md`

- [ ] **Step 1: 실패하는 통합 테스트를 쓴다**

`src/test/java/com/flowmate/approval/mapper/ApprovalDocMapperIT.java`:

```java
package com.flowmate.approval.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.LineStatus;
import com.flowmate.approval.domain.LineType;
import com.flowmate.org.domain.Employee;
import com.flowmate.org.service.DepartmentService;

/**
 * 결재 매퍼와 부서장 체인 조회. Task 1 의 시드(문서 6건)를 전제로 한다.
 */
@SpringBootTest
@Transactional
class ApprovalDocMapperIT {

    @Autowired
    private ApprovalDocMapper approvalDocMapper;

    @Autowired
    private ApprovalLineMapper approvalLineMapper;

    @Autowired
    private DepartmentService departmentService;

    @Test
    @DisplayName("문서를 저장하면 생성된 PK 가 객체에 채워진다")
    void insertPopulatesGeneratedKey() {
        ApprovalDoc doc = newDraft();

        approvalDocMapper.insert(doc);

        assertThat(doc.getApprovalId()).isNotNull().isGreaterThan(6L);
    }

    @Test
    @DisplayName("문서를 조회하면 기안자명과 부서명이 함께 온다")
    void findByIdJoinsDrafterAndDept() {
        ApprovalDoc doc = approvalDocMapper.findById(1L);

        assertThat(doc).isNotNull();
        assertThat(doc.getDocNo()).isEqualTo("EXP-2026-0001");
        assertThat(doc.getDrafterName()).isEqualTo("곽수빈");
        assertThat(doc.getDeptName()).isEqualTo("개발팀");
        assertThat(doc.getDrafterPositionName()).isEqualTo("사원");
        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.DRAFT);
    }

    @Test
    @DisplayName("없는 문서를 조회하면 null 이다")
    void findByIdReturnsNullWhenAbsent() {
        assertThat(approvalDocMapper.findById(99999L)).isNull();
    }

    @Test
    @DisplayName("문서번호 일련번호는 같은 유형·같은 연도에서 이어진다")
    void docNoSequenceContinuesPerTypeAndYear() {
        // 시드에 EXP-2026-0001 ~ 0003 이 있다
        assertThat(approvalDocMapper.maxDocNoSeq("EXP", 2026)).isEqualTo(3);
        // PUR 은 0001 ~ 0002
        assertThat(approvalDocMapper.maxDocNoSeq("PUR", 2026)).isEqualTo(2);
        // 쓰이지 않은 접두사는 0
        assertThat(approvalDocMapper.maxDocNoSeq("CON", 2026)).isZero();
    }

    @Test
    @DisplayName("상태 전이 결과만 갱신한다")
    void updateStatusPersistsTransition() {
        ApprovalDoc doc = approvalDocMapper.findById(1L);
        doc.submit(2);

        approvalDocMapper.updateStatus(doc);

        ApprovalDoc reloaded = approvalDocMapper.findById(1L);
        assertThat(reloaded.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(reloaded.getCurrentStep()).isEqualTo(1);
        assertThat(reloaded.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("결재선을 한 번에 저장하고 결재자 정보와 함께 조회한다")
    void insertAllAndFindLines() {
        ApprovalDoc doc = newDraft();
        approvalDocMapper.insert(doc);

        ApprovalLine first = line(doc.getApprovalId(), 1, 14L);
        ApprovalLine second = line(doc.getApprovalId(), 2, 3L);
        approvalLineMapper.insertAll(List.of(first, second));

        List<ApprovalLine> lines = approvalLineMapper.findByApprovalId(doc.getApprovalId());

        assertThat(lines).hasSize(2);
        assertThat(lines).extracting(ApprovalLine::getStepNo).containsExactly(1, 2);
        assertThat(lines).extracting(ApprovalLine::getApproverName).containsExactly("신동혁", "박현주");
        assertThat(lines).extracting(ApprovalLine::getApproverPositionName).containsExactly("과장", "부장");
    }

    @Test
    @DisplayName("★ 부서장 체인은 기안자 부서에서 루트까지 가까운 순서로 온다")
    void deptHeadChainWalksUpwardNearestFirst() {
        // 개발팀(7) → 사업본부(3) → 대표이사실(1)
        List<Employee> chain = departmentService.findDeptHeadChain(7L);

        assertThat(chain).extracting(Employee::getEmpName)
                .containsExactly("신동혁", "박현주", "정도현");
        assertThat(chain).extracting(Employee::getPositionLevel)
                .containsExactly(3, 5, 6);
        assertThat(chain).extracting(Employee::getDeptName)
                .containsExactly("개발팀", "사업본부", "대표이사실");
    }

    @Test
    @DisplayName("다른 본부의 체인도 같은 모양이다")
    void deptHeadChainForAnotherDivision() {
        // 인사팀(4) → 경영지원본부(2) → 대표이사실(1)
        List<Employee> chain = departmentService.findDeptHeadChain(4L);

        assertThat(chain).extracting(Employee::getEmpName)
                .containsExactly("최민석", "김성일", "정도현");
    }

    @Test
    @DisplayName("루트 부서의 체인은 자기 자신 하나뿐이다")
    void deptHeadChainForRootHasOnlyItself() {
        List<Employee> chain = departmentService.findDeptHeadChain(1L);

        assertThat(chain).extracting(Employee::getEmpName).containsExactly("정도현");
    }

    @Test
    @DisplayName("부서마다 최고 직급 1명씩만 나온다 - 동급이 있어도 중복되지 않는다")
    void deptHeadChainReturnsExactlyOnePerDepartment() {
        List<Employee> chain = departmentService.findDeptHeadChain(7L);

        assertThat(chain).extracting(Employee::getDeptId).doesNotHaveDuplicates();
    }

    private ApprovalDoc newDraft() {
        ApprovalDoc doc = new ApprovalDoc();
        doc.setDocNo("EXP-2026-9001");
        doc.setDocType(DocType.EXPENSE);
        doc.setTitle("통합 테스트 문서");
        doc.setContent("본문");
        doc.setDrafterId(18L);
        doc.setDeptId(7L);
        doc.setAmount(new BigDecimal("100000"));
        doc.setStatus(ApprovalStatus.DRAFT);
        doc.setCurrentStep(0);
        doc.setDraftedAt(LocalDateTime.now());
        return doc;
    }

    private ApprovalLine line(Long approvalId, int stepNo, Long approverId) {
        ApprovalLine line = new ApprovalLine();
        line.setApprovalId(approvalId);
        line.setStepNo(stepNo);
        line.setApproverId(approverId);
        line.setLineType(LineType.APPROVAL);
        line.setStatus(LineStatus.WAITING);
        return line;
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```powershell
docker compose ps
.\mvnw.cmd verify "-Dit.test=ApprovalDocMapperIT"
```

기대: 컴파일 실패 — `cannot find symbol: class ApprovalDocMapper`, `method findDeptHeadChain`.

- [ ] **Step 3: ★ 부서장 체인 조회를 만든다 — 재귀 CTE 를 위로 뒤집는다**

`DepartmentMapper.java` 에 메서드를 추가한다 (기존 `findDeptTree` 는 그대로).

```java
    /**
     * 주어진 부서에서 루트까지 올라가며 각 부서의 최고 직급자 1명을 반환한다.
     * 가까운 부서가 먼저 온다. 결재선 정책이 이 순서에 의존한다.
     */
    List<Employee> findDeptHeadChain(@Param("deptId") Long deptId);
```

`import org.apache.ibatis.annotations.Param;` 와 `import com.flowmate.org.domain.Employee;` 를 추가한다.

`mapper/org/DepartmentMapper.xml` 의 `</mapper>` 앞에 추가한다:

```xml
    <!--
      부서장 체인 조회 — 조직도의 재귀 CTE 를 방향만 뒤집었다.
      조직도는 루트에서 아래로 내려가고, 이것은 주어진 부서에서 위로 올라간다.
      JOIN 조건이 p.dept_id = c.parent_dept_id 인 것이 그 차이다.

      각 부서에서 최고 직급 1명만 뽑기 위해 ROW_NUMBER() 로 부서별 순위를 매긴다.
      동급이 있으면 입사일 이른 사람, 그래도 같으면 emp_id 로 결정한다 —
      결재선이 실행마다 달라지지 않게 하는 안정 정렬이다.

      ORDER BY depth 로 가까운 부서가 먼저 오게 한다. 결재선 정책이 이 순서를 전제한다.
    -->
    <select id="findDeptHeadChain" resultType="Employee">
        WITH RECURSIVE up_chain AS (
            SELECT d.dept_id, d.parent_dept_id, 1 AS depth
              FROM department d
             WHERE d.dept_id = #{deptId}
               AND d.use_yn = 'Y'
            UNION ALL
            SELECT p.dept_id, p.parent_dept_id, c.depth + 1
              FROM department p
              JOIN up_chain c ON p.dept_id = c.parent_dept_id
             WHERE p.use_yn = 'Y'
        ),
        ranked AS (
            SELECT e.emp_id, e.emp_no, e.emp_name, e.dept_id, e.position_id,
                   e.email, e.hire_date, e.role, e.use_yn,
                   d.dept_name, p.position_name, p.position_level,
                   u.depth,
                   ROW_NUMBER() OVER (PARTITION BY u.dept_id
                                      ORDER BY p.position_level DESC, e.hire_date, e.emp_id) AS rn
              FROM up_chain u
              JOIN employee   e ON e.dept_id     = u.dept_id AND e.use_yn = 'Y'
              JOIN department d ON d.dept_id     = e.dept_id
              JOIN position   p ON p.position_id = e.position_id
        )
        SELECT r.emp_id, r.emp_no, r.emp_name, r.dept_id, r.position_id,
               r.email, r.hire_date, r.role, r.use_yn,
               r.dept_name, r.position_name, r.position_level
          FROM ranked r
         WHERE r.rn = 1
         ORDER BY r.depth
    </select>
```

`DepartmentService.java` 에 위임 메서드를 추가한다:

```java
    /**
     * 결재선 생성을 위한 부서장 체인. 기안자 부서에서 루트까지, 가까운 부서가 먼저다.
     *
     * approval 모듈이 이 결과를 자기 타입(ApproverCandidate)으로 변환한다.
     * org 모듈이 approval 의 타입을 알지 않게 하려는 것이다 (설계서 §4.3).
     */
    @Transactional(readOnly = true)
    public List<Employee> findDeptHeadChain(Long deptId) {
        return departmentMapper.findDeptHeadChain(deptId);
    }
```

`import com.flowmate.org.domain.Employee;` 를 추가한다.

- [ ] **Step 4: 반려 유형과 이력 도메인을 만든다**

`src/main/java/com/flowmate/approval/domain/RejectReason.java`:

```java
package com.flowmate.approval.domain;

import java.util.List;

/**
 * 반려 유형 6종 (설계서 §5.2 확정).
 *
 * ★ 이 값이 Phase 5 AI 사전점검의 학습 원천이다.
 *   반려 화면에서 선택을 필수로 만드는 이유가 여기에 있다 —
 *   자유 텍스트만 받으면 유형별 빈도를 집계할 수 없고, 집계가 없으면
 *   "과거 반려 3건에 근거함" 같은 숫자를 제시할 수 없다.
 */
public final class RejectReason {

    public static final String INSUFFICIENT_CONTENT = "INSUFFICIENT_CONTENT";
    public static final String EXCESSIVE_AMOUNT = "EXCESSIVE_AMOUNT";
    public static final String MISSING_EVIDENCE = "MISSING_EVIDENCE";
    public static final String PROCEDURE_ERROR = "PROCEDURE_ERROR";
    public static final String BUDGET_EXCEEDED = "BUDGET_EXCEEDED";
    public static final String OTHER = "OTHER";

    /** 화면의 선택 상자 순서 */
    public static final List<String> ALL = List.of(
            INSUFFICIENT_CONTENT, EXCESSIVE_AMOUNT, MISSING_EVIDENCE,
            PROCEDURE_ERROR, BUDGET_EXCEEDED, OTHER);

    private RejectReason() {
    }

    /** 화면에 보여줄 한글 이름 */
    public static String labelOf(String category) {
        if (INSUFFICIENT_CONTENT.equals(category)) {
            return "문서 내용 불충분";
        }
        if (EXCESSIVE_AMOUNT.equals(category)) {
            return "금액 과다 · 근거 부족";
        }
        if (MISSING_EVIDENCE.equals(category)) {
            return "증빙 누락";
        }
        if (PROCEDURE_ERROR.equals(category)) {
            return "결재 절차 오류";
        }
        if (BUDGET_EXCEEDED.equals(category)) {
            return "예산 초과";
        }
        return "기타";
    }

    /** 유효한 유형인가. 화면에서 넘어온 값을 신뢰하지 않기 위한 검사다 */
    public static boolean isValid(String category) {
        return ALL.contains(category);
    }
}
```

`src/main/java/com/flowmate/approval/domain/ApprovalHistory.java`:

```java
package com.flowmate.approval.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 결재 이력 한 줄. 문서 상세 화면의 타임라인이 이 목록을 그린다.
 *
 * actorName / actorPositionName 은 조인 결과를 담는 조회 표시용 파생 필드다.
 */
public class ApprovalHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long historyId;
    private Long approvalId;
    private Long actorId;
    private String action;
    private String comment;
    private LocalDateTime createdAt;

    // 조회 표시용
    private String actorName;
    private String actorPositionName;

    /**
     * 화면에 보여줄 한글 행위명.
     *
     * JSP 가 ${h.actionLabel} 로 읽는다. 정적 메서드 호출은 EL 에서 번거로우므로
     * 도메인 객체에 파생 getter 로 둔다.
     */
    public String getActionLabel() {
        if (HistoryAction.DRAFT.equals(this.action)) {
            return "기안";
        }
        if (HistoryAction.SUBMIT.equals(this.action)) {
            return "상신";
        }
        if (HistoryAction.APPROVE.equals(this.action)) {
            return "승인";
        }
        if (HistoryAction.REJECT.equals(this.action)) {
            return "반려";
        }
        if (HistoryAction.CANCEL.equals(this.action)) {
            return "회수";
        }
        return this.action;
    }

    public Long getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Long historyId) {
        this.historyId = historyId;
    }

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getActorPositionName() {
        return actorPositionName;
    }

    public void setActorPositionName(String actorPositionName) {
        this.actorPositionName = actorPositionName;
    }
}
```

`src/main/java/com/flowmate/approval/domain/RejectHistory.java`:

```java
package com.flowmate.approval.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 반려 이력. ★ Phase 5 AI 사전점검이 읽는 표다.
 *
 * docType / deptId 가 approval_doc 과 중복이지만 의도한 비정규화다 (설계서 §5.2).
 * 사전점검은 상신할 때마다 도는 조회이므로 매번 조인하지 않는다.
 */
public class RejectHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long approvalId;
    private String docType;
    private Long deptId;
    private Long rejectorId;
    private String reasonCategory;
    private String reasonText;
    private LocalDateTime rejectedAt;

    /** 화면 표시용 반려 유형 한글명 */
    public String getReasonLabel() {
        return RejectReason.labelOf(this.reasonCategory);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getRejectorId() {
        return rejectorId;
    }

    public void setRejectorId(Long rejectorId) {
        this.rejectorId = rejectorId;
    }

    public String getReasonCategory() {
        return reasonCategory;
    }

    public void setReasonCategory(String reasonCategory) {
        this.reasonCategory = reasonCategory;
    }

    public String getReasonText() {
        return reasonText;
    }

    public void setReasonText(String reasonText) {
        this.reasonText = reasonText;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(LocalDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }
}
```

- [ ] **Step 5: 매퍼 인터페이스 4종을 만든다**

`ApprovalDocMapper.java`:

```java
package com.flowmate.approval.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalSearchCond;

/**
 * ★ 이 매퍼에 캐시(@Cacheable 등)를 붙이지 않는다.
 *   로드맵 §2.0 C1 과 같은 이유다 — 조회 결과 객체를 여러 요청이 공유하면
 *   한쪽에서 상태를 전이시킨 것이 다른 요청에 보인다.
 */
@Mapper
public interface ApprovalDocMapper {

    /** 저장 후 생성된 PK 를 인자 객체에 채운다 */
    void insert(ApprovalDoc doc);

    /** 임시저장 문서의 내용 수정 */
    void update(ApprovalDoc doc);

    /** 상태 전이 결과만 갱신 (status, current_step, submitted_at, completed_at) */
    void updateStatus(ApprovalDoc doc);

    /** 기안자·부서 조인 포함. 없으면 null */
    ApprovalDoc findById(@Param("approvalId") Long approvalId);

    /**
     * 동시 결재 방지용 행 잠금 조회.
     * 두 결재자가 같은 문서를 동시에 누르면 둘 다 승인 처리되어 단계가 어긋난다.
     */
    ApprovalDoc findByIdForUpdate(@Param("approvalId") Long approvalId);

    /** 같은 접두사·연도의 최대 일련번호. 없으면 0 */
    int maxDocNoSeq(@Param("prefix") String prefix, @Param("year") int year);

    /** 내 결재함 목록 */
    List<ApprovalDoc> searchBox(ApprovalSearchCond cond);

    /** 내 결재함 건수 */
    long countBox(ApprovalSearchCond cond);
}
```

`ApprovalLineMapper.java`:

```java
package com.flowmate.approval.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.approval.domain.ApprovalLine;

@Mapper
public interface ApprovalLineMapper {

    /** 결재선 전체를 한 번에 저장한다 */
    void insertAll(@Param("lines") List<ApprovalLine> lines);

    /** 결재자 이름·직급·부서 조인 포함. step_no 순 */
    List<ApprovalLine> findByApprovalId(@Param("approvalId") Long approvalId);

    /** 특정 단계 하나. 없으면 null */
    ApprovalLine findStep(@Param("approvalId") Long approvalId, @Param("stepNo") int stepNo);

    /** 단계 처리 결과 갱신 (status, comment, processed_at) */
    void updateStep(ApprovalLine line);

    /** WAITING 단계를 CURRENT 로 올린다 */
    void activateStep(@Param("approvalId") Long approvalId, @Param("stepNo") int stepNo);

    /** 반려 시 남은 단계를 전부 SKIPPED 로 만든다 */
    void skipRemaining(@Param("approvalId") Long approvalId, @Param("fromStepNo") int fromStepNo);

    /** 결재선 길이. 상태 전이의 totalStep 으로 쓴다 */
    int countByApprovalId(@Param("approvalId") Long approvalId);
}
```

`ApprovalHistoryMapper.java` — `void insert(ApprovalHistory h);` 와
`List<ApprovalHistory> findByApprovalId(@Param("approvalId") Long approvalId);` 두 개.

`RejectHistoryMapper.java` — `void insert(RejectHistory h);` 하나.
(유형별 빈도 집계는 Phase 5 에서 추가한다 — 지금 만들면 쓰는 곳이 없다.)

- [ ] **Step 6: 매퍼 XML 4종을 만든다**

`src/main/resources/mapper/approval/ApprovalDocMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.flowmate.approval.mapper.ApprovalDocMapper">

    <!-- 문서 조회에 늘 붙는 조인. 기안자명·부서명·직급명을 화면이 그대로 쓴다 -->
    <sql id="docColumns">
        a.approval_id, a.doc_no, a.doc_type, a.title, a.content,
        a.drafter_id, a.dept_id, a.amount, a.status, a.current_step,
        a.drafted_at, a.submitted_at, a.completed_at,
        e.emp_name  AS drafter_name,
        p.position_name AS drafter_position_name,
        d.dept_name
    </sql>

    <sql id="docJoins">
        FROM approval_doc a
        JOIN employee   e ON e.emp_id      = a.drafter_id
        JOIN position   p ON p.position_id = e.position_id
        JOIN department d ON d.dept_id     = a.dept_id
    </sql>

    <insert id="insert" useGeneratedKeys="true" keyProperty="approvalId" keyColumn="approval_id">
        INSERT INTO approval_doc
            (doc_no, doc_type, title, content, drafter_id, dept_id, amount,
             status, current_step, drafted_at, submitted_at, completed_at)
        VALUES
            (#{docNo}, #{docType}, #{title}, #{content}, #{drafterId}, #{deptId}, #{amount},
             #{status}, #{currentStep}, #{draftedAt}, #{submittedAt}, #{completedAt})
    </insert>

    <!-- 임시저장 문서의 내용만 고친다. 상태 컬럼은 건드리지 않는다 -->
    <update id="update">
        UPDATE approval_doc
           SET doc_type = #{docType},
               title    = #{title},
               content  = #{content},
               amount   = #{amount}
         WHERE approval_id = #{approvalId}
    </update>

    <!-- 상태 전이 결과만 반영한다. 도메인 객체가 계산한 값을 그대로 쓴다 -->
    <update id="updateStatus">
        UPDATE approval_doc
           SET status       = #{status},
               current_step = #{currentStep},
               submitted_at = #{submittedAt},
               completed_at = #{completedAt}
         WHERE approval_id = #{approvalId}
    </update>

    <select id="findById" resultType="ApprovalDoc">
        SELECT <include refid="docColumns"/>
        <include refid="docJoins"/>
        WHERE a.approval_id = #{approvalId}
    </select>

    <!--
      FOR UPDATE 로 행을 잠근다.
      두 결재자가 같은 문서의 승인 버튼을 동시에 누르면, 잠금이 없으면 둘 다
      같은 current_step 을 읽어 각자 +1 해서 단계가 하나 건너뛰어진다.
      Oracle 대응은 docs/oracle-mapping.md 참조 (구문 동일).
    -->
    <select id="findByIdForUpdate" resultType="ApprovalDoc">
        SELECT <include refid="docColumns"/>
        <include refid="docJoins"/>
        WHERE a.approval_id = #{approvalId}
        FOR UPDATE OF a
    </select>

    <!--
      COALESCE 로 0을 돌려주는 이유: 해당 접두사·연도의 문서가 아직 없으면
      MAX 가 NULL 이고, int 로 받으면 MyBatis 가 예외를 던진다.
      문서번호 형식은 {접두사}-{연도}-{4자리}. 뒤 4자리만 잘라 숫자로 본다.
    -->
    <select id="maxDocNoSeq" resultType="int">
        SELECT COALESCE(MAX(CAST(SUBSTRING(a.doc_no FROM 10 FOR 4) AS INT)), 0)
          FROM approval_doc a
         WHERE a.doc_no LIKE #{prefix} || '-' || CAST(#{year} AS VARCHAR(4)) || '-%'
    </select>

    <!--
      내 결재함. 탭에 따라 조건이 크게 달라지므로 <choose> 로 갈라 쓴다.
        drafted  : 내가 기안한 것 전부
        pending  : 지금 내 차례인 것 (결재선 CURRENT + 내가 결재자)
        done     : 내가 처리를 끝낸 것
        rejected : 내가 기안했고 반려된 것
    -->
    <sql id="boxWhere">
        <where>
            <choose>
                <when test="tab == 'pending'">
                    a.status = 'PENDING'
                    AND EXISTS (SELECT 1 FROM approval_line l
                                 WHERE l.approval_id = a.approval_id
                                   AND l.approver_id = #{empId}
                                   AND l.status = 'CURRENT')
                </when>
                <when test="tab == 'done'">
                    EXISTS (SELECT 1 FROM approval_line l
                             WHERE l.approval_id = a.approval_id
                               AND l.approver_id = #{empId}
                               AND l.status IN ('APPROVED', 'REJECTED'))
                </when>
                <when test="tab == 'rejected'">
                    a.drafter_id = #{empId} AND a.status = 'REJECTED'
                </when>
                <otherwise>
                    a.drafter_id = #{empId}
                </otherwise>
            </choose>
            <if test="docType != null">
                AND a.doc_type = #{docType}
            </if>
            <if test="keyword != null">
                AND (a.title LIKE '%' || #{keywordEscaped} || '%' ESCAPE '\'
                  OR a.doc_no LIKE '%' || #{keywordEscaped} || '%' ESCAPE '\')
            </if>
        </where>
    </sql>

    <select id="searchBox" resultType="ApprovalDoc">
        SELECT <include refid="docColumns"/>
        <include refid="docJoins"/>
        <include refid="boxWhere"/>
        ORDER BY a.drafted_at DESC, a.approval_id DESC
        LIMIT #{limit} OFFSET #{offset}
    </select>

    <select id="countBox" resultType="long">
        SELECT COUNT(*)
          FROM approval_doc a
        <include refid="boxWhere"/>
    </select>

</mapper>
```

> **`countBox` 가 조인 없이 `boxWhere` 를 재사용할 수 있는 이유:** 조건이 `a.` 별칭과
> `EXISTS` 서브쿼리만 쓴다. Phase 1 의 `EmployeeMapper` 와 같은 패턴이고, 같은 이유로
> 목록과 건수의 조건이 어긋나는 사고가 구조적으로 불가능하다.

`ApprovalLineMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.flowmate.approval.mapper.ApprovalLineMapper">

    <insert id="insertAll">
        INSERT INTO approval_line
            (approval_id, step_no, approver_id, line_type, status, comment, processed_at)
        VALUES
        <foreach collection="lines" item="l" separator=",">
            (#{l.approvalId}, #{l.stepNo}, #{l.approverId}, #{l.lineType},
             #{l.status}, #{l.comment}, #{l.processedAt})
        </foreach>
    </insert>

    <sql id="lineSelect">
        SELECT l.line_id, l.approval_id, l.step_no, l.approver_id,
               l.line_type, l.status, l.comment, l.processed_at,
               e.emp_name      AS approver_name,
               p.position_name AS approver_position_name,
               d.dept_name     AS approver_dept_name
          FROM approval_line l
          JOIN employee   e ON e.emp_id      = l.approver_id
          JOIN position   p ON p.position_id = e.position_id
          JOIN department d ON d.dept_id     = e.dept_id
    </sql>

    <select id="findByApprovalId" resultType="ApprovalLine">
        <include refid="lineSelect"/>
        WHERE l.approval_id = #{approvalId}
        ORDER BY l.step_no
    </select>

    <select id="findStep" resultType="ApprovalLine">
        <include refid="lineSelect"/>
        WHERE l.approval_id = #{approvalId}
          AND l.step_no = #{stepNo}
    </select>

    <update id="updateStep">
        UPDATE approval_line
           SET status       = #{status},
               comment      = #{comment},
               processed_at  = #{processedAt}
         WHERE line_id = #{lineId}
    </update>

    <!-- 대기 중인 단계만 올린다. 이미 처리된 단계를 되돌리지 않기 위한 조건이다 -->
    <update id="activateStep">
        UPDATE approval_line
           SET status = 'CURRENT'
         WHERE approval_id = #{approvalId}
           AND step_no = #{stepNo}
           AND status = 'WAITING'
    </update>

    <!-- 반려 시 뒤 단계 정리. 이미 처리된 단계는 건드리지 않는다 -->
    <update id="skipRemaining">
        UPDATE approval_line
           SET status = 'SKIPPED'
         WHERE approval_id = #{approvalId}
           AND step_no >= #{fromStepNo}
           AND status IN ('WAITING', 'CURRENT')
    </update>

    <select id="countByApprovalId" resultType="int">
        SELECT COUNT(*) FROM approval_line WHERE approval_id = #{approvalId}
    </select>

</mapper>
```

> `step_no >= #{fromStepNo}` 의 `>=` 는 XML 에서 이스케이프가 필요 없다.
> `<` 만 문제가 되므로, 만약 `<=` 를 쓸 일이 생기면 `&lt;=` 로 쓰거나 `<![CDATA[ ]]>` 로 감싼다.

`src/main/resources/mapper/approval/ApprovalHistoryMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.flowmate.approval.mapper.ApprovalHistoryMapper">

    <insert id="insert" useGeneratedKeys="true" keyProperty="historyId" keyColumn="history_id">
        INSERT INTO approval_history (approval_id, actor_id, action, comment, created_at)
        VALUES (#{approvalId}, #{actorId}, #{action}, #{comment}, #{createdAt})
    </insert>

    <!-- history_id 를 두 번째 정렬키로 두는 이유: 같은 트랜잭션에서 두 이력이 쌓이면
         created_at 이 동일할 수 있고, 그러면 타임라인 순서가 실행마다 흔들린다 -->
    <select id="findByApprovalId" resultType="ApprovalHistory">
        SELECT h.history_id, h.approval_id, h.actor_id, h.action, h.comment, h.created_at,
               e.emp_name      AS actor_name,
               p.position_name AS actor_position_name
          FROM approval_history h
          JOIN employee e ON e.emp_id      = h.actor_id
          JOIN position p ON p.position_id = e.position_id
         WHERE h.approval_id = #{approvalId}
         ORDER BY h.created_at, h.history_id
    </select>

</mapper>
```

`src/main/resources/mapper/approval/RejectHistoryMapper.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.flowmate.approval.mapper.RejectHistoryMapper">

    <!--
      Phase 5 AI 사전점검의 학습 원천에 한 줄을 쌓는다.
      유형별 빈도 집계 쿼리는 Phase 5 에서 이 파일에 추가한다 — 지금 만들면 쓰는 곳이 없다.
    -->
    <insert id="insert" useGeneratedKeys="true" keyProperty="id" keyColumn="id">
        INSERT INTO approval_reject_history
            (approval_id, doc_type, dept_id, rejector_id, reason_category, reason_text, rejected_at)
        VALUES
            (#{approvalId}, #{docType}, #{deptId}, #{rejectorId},
             #{reasonCategory}, #{reasonText}, #{rejectedAt})
    </insert>

</mapper>
```

- [ ] **Step 7: `ApprovalSearchCond` 를 만든다**

Phase 1 의 `EmployeeSearchCond` 와 같은 규약이다 — setter 에서 값을 보정하고
`getKeywordEscaped()` 로 LIKE 와일드카드를 이스케이프한다.

```java
package com.flowmate.approval.domain;

/**
 * 내 결재함 검색 조건. Phase 1 의 EmployeeSearchCond 와 같은 규약을 따른다.
 * 보정을 setter 에서 끝내 잘못된 값이 SQL 까지 흘러가지 않게 한다.
 */
public class ApprovalSearchCond {

    private static final int DEFAULT_SIZE = 10;
    private static final int MAX_SIZE = 100;

    /** drafted | pending | done | rejected */
    private String tab = "drafted";
    private Long empId;
    private String docType;
    private String keyword;
    private int page = 1;
    private int size = DEFAULT_SIZE;

    public String getTab() {
        return tab;
    }

    /** 알 수 없는 탭 값은 기본 탭으로 떨어뜨린다. 화면에서 넘어온 값을 신뢰하지 않는다 */
    public void setTab(String tab) {
        if ("pending".equals(tab) || "done".equals(tab) || "rejected".equals(tab)) {
            this.tab = tab;
            return;
        }
        this.tab = "drafted";
    }

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = (docType == null || docType.trim().isEmpty()) ? null : docType.trim();
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = (keyword == null || keyword.trim().isEmpty()) ? null : keyword.trim();
    }

    /** LIKE 패턴용. 원본은 getKeyword() 가 돌려주므로 폼에 되돌릴 때는 그것을 쓴다 */
    public String getKeywordEscaped() {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(page, 1);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        if (size < 1) {
            this.size = DEFAULT_SIZE;
            return;
        }
        this.size = Math.min(size, MAX_SIZE);
    }

    public int getLimit() {
        return size;
    }

    public long getOffset() {
        return (long) (page - 1) * size;
    }
}
```

- [ ] **Step 8: 통합 테스트가 통과하는지 확인한다**

```powershell
.\mvnw.cmd verify "-Dit.test=ApprovalDocMapperIT"
```

기대: `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`

진단표:

| 증상 | 원인 |
|---|---|
| `approvalId` 가 null | `useGeneratedKeys` / `keyProperty` / `keyColumn` 누락 |
| 모든 필드 null | 컬럼 별칭이 카멜케이스와 안 맞는다 (`drafter_name` → `drafterName`) |
| `Invalid bound statement` | XML `namespace` 오타, 또는 `mapper-locations` 가 `mapper/**/*.xml` 이므로 경로가 `mapper/approval/` 인지 확인 |
| 체인이 역순 | `ORDER BY r.depth` 누락 |
| 체인에 부서당 2명 | `ROW_NUMBER()` / `WHERE r.rn = 1` 누락 |
| `maxDocNoSeq` 예외 | `COALESCE` 누락으로 NULL 이 int 에 매핑됨 |

- [ ] **Step 9: `docs/oracle-mapping.md` 에 §2.4 · §2.5 를 추가한다**

파일 끝에 붙인다.

```markdown
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
```

- [ ] **Step 10: 전체 빌드 후 커밋한다**

```powershell
.\mvnw.cmd clean verify
```

기대: Surefire `Tests run: 52`, Failsafe `Tests run: 31` (기존 21 + 10), `BUILD SUCCESS`.

```powershell
git add src/main/java/com/flowmate src/main/resources/mapper src/test/java/com/flowmate/approval docs/oracle-mapping.md
git status -s
```

메시지:

```
feat: 결재 매퍼 4종과 부서장 체인 조회

조직도의 재귀 CTE 를 방향만 뒤집어 부서장 체인을 만든다. 조직도는 루트에서 내려가고
이것은 기안자 부서에서 올라간다. JOIN 조건의 PRIOR 위치가 방향을 결정한다.

부서별 최고 직급 1명만 뽑기 위해 ROW_NUMBER 로 순위를 매긴다. 동급이면 입사일,
그래도 같으면 emp_id 로 결정해 결재선이 실행마다 달라지지 않게 한다.

승인 조회에 FOR UPDATE 를 붙인다. 두 결재자가 동시에 누르면 잠금이 없으면 둘 다
같은 current_step 을 읽어 각자 +1 해서 단계가 하나 건너뛰어진다.

approval 모듈은 org 의 매퍼를 직접 부르지 않고 DepartmentService 를 경유한다.
org 이 approval 의 타입을 알지 않도록 변환은 approval 쪽에서 한다.

이 매퍼들에 캐시를 붙이지 않는다. 조회 객체를 여러 요청이 공유하면 한쪽의 상태 전이가
다른 요청에 보인다.
```

---

## Task 6: 기안 작성과 임시저장 (TDD)

**Files:**
- Create: `src/main/java/com/flowmate/common/exception/FlowMateException.java`
- Create: `src/main/java/com/flowmate/common/exception/ApprovalNotFoundException.java`
- Create: `src/main/java/com/flowmate/common/exception/ApprovalAccessDeniedException.java`
- Create: `src/main/webapp/WEB-INF/views/common/csrf-input.jsp` ★ 로드맵 C2 해소
- Test: `src/test/java/com/flowmate/approval/service/ApprovalServiceIT.java` (일부)
- Create: `src/main/java/com/flowmate/approval/service/ApprovalService.java`
- Create: `src/main/java/com/flowmate/approval/controller/ApprovalWriteController.java`
- Create: `src/main/webapp/WEB-INF/views/approval/write.jsp`
- Modify: `src/main/webapp/WEB-INF/views/common/sidebar.jsp`

- [ ] **Step 1: 예외 3종을 만든다**

설계서 §4.2 가 지정한 것들이다.

```java
package com.flowmate.common.exception;

/** FlowMate 업무 예외의 뿌리 */
public class FlowMateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FlowMateException(String message) {
        super(message);
    }
}
```

```java
package com.flowmate.common.exception;

public class ApprovalNotFoundException extends FlowMateException {

    private static final long serialVersionUID = 1L;

    public ApprovalNotFoundException(Long approvalId) {
        super("결재 문서를 찾을 수 없습니다: " + approvalId);
    }
}
```

```java
package com.flowmate.common.exception;

/**
 * 문서 접근·처리 권한 없음.
 *
 * URL 인가로는 막을 수 없는 종류의 권한이다 — "내 결재선에 있는 문서만" 같은 판정은
 * 문서마다 다르므로 Service 에서 검사한다 (설계서 §6.1).
 */
public class ApprovalAccessDeniedException extends FlowMateException {

    private static final long serialVersionUID = 1L;

    public ApprovalAccessDeniedException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: ★ CSRF 공유 조각을 만든다 (로드맵 C2 해소)**

`src/main/webapp/WEB-INF/views/common/csrf-input.jsp`:

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%--
  POST 폼에 넣을 CSRF hidden input.

  Spring Security 6 은 일반 <form> 에 토큰을 자동 주입하지 않는다.
  이 줄을 빠뜨린 폼은 컴파일·템플릿 단계에서 아무 신호가 없다가 제출 시점에 403 이 된다.
  파일마다 복붙하면 언젠가 한 곳을 빠뜨리므로 조각으로 만들어 include 한다.

  사용법:
    <form method="post" action="...">
        <jsp:include page="../common/csrf-input.jsp"/>
        ...
    </form>
--%>
<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
```

> `login.jsp` 와 `header.jsp` 의 기존 hidden input 은 **이 Task 에서 바꾸지 않는다.**
> 동작하는 코드를 건드릴 이유가 없고, 조각의 목적은 **앞으로 만들 폼**의 누락을 막는 것이다.
> Task 11 에서 여유가 있으면 그때 통일한다.

- [ ] **Step 3: 실패하는 통합 테스트를 쓴다 (임시저장 부분)**

`src/test/java/com/flowmate/approval/service/ApprovalServiceIT.java`:

```java
package com.flowmate.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.LineStatus;
import com.flowmate.approval.mapper.ApprovalLineMapper;
import com.flowmate.common.exception.ApprovalAccessDeniedException;

/**
 * 결재 처리의 트랜잭션 경계. 시드(문서 6건)와 조직 시드(사원 20)를 전제로 한다.
 *
 * 사원번호: 곽수빈 18(개발팀 사원), 신동혁 14(개발팀 과장), 박현주 3(사업본부 부장), 정도현 1(이사)
 */
@SpringBootTest
@Transactional
class ApprovalServiceIT {

    private static final Long KWAK = 18L;
    private static final Long SHIN = 14L;
    private static final Long PARK = 3L;
    private static final Long JEONG = 1L;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalQueryService queryService;

    @Autowired
    private ApprovalLineMapper lineMapper;

    @Test
    @DisplayName("임시저장하면 문서번호가 부여되고 결재선이 자동 생성된다")
    void saveDraftGeneratesDocNoAndLines() {
        Long id = approvalService.saveDraft(newForm("소액 지출", "1000000"), KWAK);

        ApprovalDoc doc = queryService.findDoc(id, KWAK);
        assertThat(doc.getDocNo()).matches("EXP-\\d{4}-\\d{4}");
        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.DRAFT);
        assertThat(doc.getCurrentStep()).isZero();

        List<ApprovalLine> lines = lineMapper.findByApprovalId(id);
        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(SHIN, PARK);
        assertThat(lines).allSatisfy(l -> assertThat(l.getStatus()).isEqualTo(LineStatus.WAITING));
    }

    @Test
    @DisplayName("금액이 크면 결재선에 이사가 추가된다 - 정책이 실제로 적용된다")
    void largeAmountAddsExecutiveToLine() {
        Long id = approvalService.saveDraft(newForm("고액 구매", "5000000"), KWAK);

        assertThat(lineMapper.findByApprovalId(id))
                .extracting(ApprovalLine::getApproverId)
                .containsExactly(SHIN, PARK, JEONG);
    }

    @Test
    @DisplayName("이사가 기안하면 결재선이 비어 있다")
    void executiveDraftHasNoLine() {
        Long id = approvalService.saveDraft(newForm("이사 기안", "1000000"), JEONG);

        assertThat(lineMapper.findByApprovalId(id)).isEmpty();
    }

    @Test
    @DisplayName("문서번호는 같은 유형·연도에서 이어진다")
    void docNoIncrementsPerTypeAndYear() {
        Long first = approvalService.saveDraft(newForm("첫 번째", "10000"), KWAK);
        Long second = approvalService.saveDraft(newForm("두 번째", "10000"), KWAK);

        String firstNo = queryService.findDoc(first, KWAK).getDocNo();
        String secondNo = queryService.findDoc(second, KWAK).getDocNo();

        int firstSeq = Integer.parseInt(firstNo.substring(firstNo.length() - 4));
        int secondSeq = Integer.parseInt(secondNo.substring(secondNo.length() - 4));
        assertThat(secondSeq).isEqualTo(firstSeq + 1);
    }

    @Test
    @DisplayName("임시저장 문서는 기안자만 수정할 수 있다")
    void onlyDrafterCanEditDraft() {
        Long id = approvalService.saveDraft(newForm("원본", "10000"), KWAK);

        ApprovalForm changed = newForm("수정본", "20000");
        changed.setApprovalId(id);

        assertThatThrownBy(() -> approvalService.saveDraft(changed, SHIN))
                .isInstanceOf(ApprovalAccessDeniedException.class);

        approvalService.saveDraft(changed, KWAK);
        assertThat(queryService.findDoc(id, KWAK).getTitle()).isEqualTo("수정본");
    }

    @Test
    @DisplayName("수정할 때 금액이 바뀌면 결재선을 다시 만든다")
    void editingAmountRebuildsLine() {
        Long id = approvalService.saveDraft(newForm("소액", "1000000"), KWAK);
        assertThat(lineMapper.findByApprovalId(id)).hasSize(2);

        ApprovalForm changed = newForm("고액으로 수정", "5000000");
        changed.setApprovalId(id);
        approvalService.saveDraft(changed, KWAK);

        assertThat(lineMapper.findByApprovalId(id))
                .extracting(ApprovalLine::getApproverId)
                .containsExactly(SHIN, PARK, JEONG);
    }

    private ApprovalForm newForm(String title, String amount) {
        ApprovalForm form = new ApprovalForm();
        form.setDocType(DocType.EXPENSE);
        form.setTitle(title);
        form.setContent("본문 내용");
        form.setAmount(new BigDecimal(amount));
        return form;
    }
}
```

- [ ] **Step 4: `ApprovalForm` 을 만든다**

화면 입력을 담는 폼 객체다. 도메인과 분리하는 이유: 화면은 `docNo`·`status`·`currentStep` 을
보내지 않고, 보내오더라도 신뢰하지 않아야 한다.

```java
package com.flowmate.approval.domain;

import java.math.BigDecimal;

/**
 * 기안 화면 입력. 도메인 객체와 분리한다.
 *
 * 화면이 status·currentStep·docNo 를 보내오더라도 신뢰하지 않기 위한 경계다.
 * 그 값들은 Service 와 도메인 객체가 정한다.
 */
public class ApprovalForm {

    /** null 이면 신규, 값이 있으면 임시저장 문서 수정 */
    private Long approvalId;
    private String docType;
    private String title;
    private String content;
    private BigDecimal amount;

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = (title == null) ? null : title.trim();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    /** 금액을 비워 보내면 0으로 본다. 결재선 정책이 null 을 다루지 않게 한다 */
    public void setAmount(BigDecimal amount) {
        this.amount = (amount == null) ? BigDecimal.ZERO : amount;
    }
}
```

- [ ] **Step 5: `ApprovalService` 의 임시저장 부분을 만든다**

```java
package com.flowmate.approval.service;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalForm;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.HistoryAction;
import com.flowmate.approval.mapper.ApprovalDocMapper;
import com.flowmate.approval.mapper.ApprovalHistoryMapper;
import com.flowmate.approval.mapper.ApprovalLineMapper;
import com.flowmate.approval.mapper.RejectHistoryMapper;
import com.flowmate.approval.policy.ApprovalLinePolicy;
import com.flowmate.approval.policy.ApproverCandidate;
import com.flowmate.common.exception.ApprovalAccessDeniedException;
import com.flowmate.common.exception.ApprovalNotFoundException;
import com.flowmate.org.domain.Employee;
import com.flowmate.org.mapper.EmployeeMapper;
import com.flowmate.org.service.DepartmentService;

/**
 * 결재 처리의 트랜잭션 경계.
 *
 * 상태 문자열을 직접 바꾸지 않는다 — ApprovalDoc 의 전이 메서드만 부른다.
 * 그래야 허용되지 않는 순서로 부르는 코드가 DB 에 닿기 전에 죽는다.
 */
@Service
public class ApprovalService {

    private static final int DOC_NO_RETRY = 3;

    private final ApprovalDocMapper docMapper;
    private final ApprovalLineMapper lineMapper;
    private final ApprovalHistoryMapper historyMapper;
    private final RejectHistoryMapper rejectHistoryMapper;
    private final ApprovalLinePolicy linePolicy;
    private final DepartmentService departmentService;
    private final EmployeeMapper employeeMapper;

    public ApprovalService(ApprovalDocMapper docMapper,
                           ApprovalLineMapper lineMapper,
                           ApprovalHistoryMapper historyMapper,
                           RejectHistoryMapper rejectHistoryMapper,
                           ApprovalLinePolicy linePolicy,
                           DepartmentService departmentService,
                           EmployeeMapper employeeMapper) {
        this.docMapper = docMapper;
        this.lineMapper = lineMapper;
        this.historyMapper = historyMapper;
        this.rejectHistoryMapper = rejectHistoryMapper;
        this.linePolicy = linePolicy;
        this.departmentService = departmentService;
        this.employeeMapper = employeeMapper;
    }

    /**
     * 임시저장. 신규면 만들고, approvalId 가 있으면 수정한다.
     *
     * 수정 시 결재선을 **지우고 다시 만든다.** 금액이나 유형이 바뀌면 정책 결과가 달라지는데
     * 기존 결재선을 남겨두면 화면에 보이는 결재선과 실제 규칙이 어긋난다.
     *
     * @return 저장된 문서의 approvalId
     */
    @Transactional
    public Long saveDraft(ApprovalForm form, Long actorId) {
        if (form.getApprovalId() == null) {
            return createDraft(form, actorId);
        }
        return updateDraft(form, actorId);
    }

    private Long createDraft(ApprovalForm form, Long actorId) {
        Employee drafter = requireEmployee(actorId);

        ApprovalDoc doc = new ApprovalDoc();
        doc.setDocType(form.getDocType());
        doc.setTitle(form.getTitle());
        doc.setContent(form.getContent());
        doc.setAmount(form.getAmount());
        doc.setDrafterId(actorId);
        doc.setDeptId(drafter.getDeptId());
        doc.setStatus(ApprovalStatus.DRAFT);
        doc.setCurrentStep(0);
        doc.setDraftedAt(LocalDateTime.now());

        insertWithGeneratedDocNo(doc);
        rebuildLines(doc, drafter);
        historyMapper.insert(HistoryFactory.of(doc.getApprovalId(), actorId, HistoryAction.DRAFT, null));
        return doc.getApprovalId();
    }

    private Long updateDraft(ApprovalForm form, Long actorId) {
        ApprovalDoc doc = requireDoc(form.getApprovalId());
        if (!Objects.equals(doc.getDrafterId(), actorId)) {
            throw new ApprovalAccessDeniedException("기안자만 수정할 수 있습니다");
        }
        if (!doc.isEditable()) {
            throw new ApprovalAccessDeniedException("임시저장 상태만 수정할 수 있습니다: " + doc.getStatus());
        }
        doc.setDocType(form.getDocType());
        doc.setTitle(form.getTitle());
        doc.setContent(form.getContent());
        doc.setAmount(form.getAmount());
        docMapper.update(doc);

        rebuildLines(doc, requireEmployee(actorId));
        return doc.getApprovalId();
    }

    /**
     * 결재선을 정책으로 다시 만든다.
     *
     * 부서장 체인 조회는 org 모듈의 Service 를 경유한다 (설계서 §4.3).
     * 변환(Employee → ApproverCandidate)은 여기서 한다 — org 이 approval 의 타입을 알지 않게.
     */
    private void rebuildLines(ApprovalDoc doc, Employee drafter) {
        lineMapper.deleteByApprovalId(doc.getApprovalId());

        List<ApproverCandidate> chain = new ArrayList<>();
        for (Employee head : departmentService.findDeptHeadChain(drafter.getDeptId())) {
            chain.add(toCandidate(head));
        }
        List<ApprovalLine> lines = linePolicy.determineLines(doc, toCandidate(drafter), chain);
        if (!lines.isEmpty()) {
            lineMapper.insertAll(lines);
        }
    }

    private ApproverCandidate toCandidate(Employee e) {
        return new ApproverCandidate(e.getEmpId(), e.getEmpName(), e.getDeptId(),
                e.getPositionLevel(), e.getPositionName());
    }

    /**
     * 문서번호를 부여해 저장한다.
     *
     * MAX + 1 방식이라 동시에 두 명이 같은 유형을 기안하면 같은 번호가 나올 수 있다.
     * doc_no 의 UNIQUE 제약이 최후 방어선이고, 충돌하면 번호를 다시 계산해 재시도한다.
     * (운영 규모에서는 유형별 시퀀스를 쓰는 것이 정석이다 — 여기서는 제약 + 재시도로 충분하다.)
     */
    private void insertWithGeneratedDocNo(ApprovalDoc doc) {
        int year = Year.now().getValue();
        String prefix = DocType.prefixOf(doc.getDocType());
        for (int attempt = 1; attempt <= DOC_NO_RETRY; attempt++) {
            int seq = docMapper.maxDocNoSeq(prefix, year) + 1;
            doc.setDocNo(String.format("%s-%d-%04d", prefix, year, seq));
            try {
                docMapper.insert(doc);
                return;
            } catch (DuplicateKeyException e) {
                if (attempt == DOC_NO_RETRY) {
                    throw e;
                }
            }
        }
    }

    private ApprovalDoc requireDoc(Long approvalId) {
        ApprovalDoc doc = docMapper.findById(approvalId);
        if (doc == null) {
            throw new ApprovalNotFoundException(approvalId);
        }
        return doc;
    }

    private Employee requireEmployee(Long empId) {
        Employee e = employeeMapper.findById(empId);
        if (e == null) {
            throw new ApprovalAccessDeniedException("사원을 찾을 수 없습니다: " + empId);
        }
        return e;
    }
}
```

추가로 필요한 것 둘:

1. `ApprovalLineMapper` 에 `void deleteByApprovalId(@Param("approvalId") Long approvalId);` 와
   XML `<delete id="deleteByApprovalId">DELETE FROM approval_line WHERE approval_id = #{approvalId}</delete>`
2. `EmployeeMapper` 에 `Employee findById(@Param("empId") Long empId);` 와
   XML — `findByEmpNo` 를 복사해 `WHERE e.emp_id = #{empId}` 로 바꾸고 **`password_hash` 는 선택하지 않는다**
   (로그인 경로가 아니므로 해시를 읽을 이유가 없다)

`HistoryFactory` 는 작은 정적 헬퍼다:

```java
package com.flowmate.approval.service;

import java.time.LocalDateTime;

import com.flowmate.approval.domain.ApprovalHistory;

/** 이력 객체 조립. Service 가 매번 5줄을 쓰지 않게 한다 */
final class HistoryFactory {

    private HistoryFactory() {
    }

    static ApprovalHistory of(Long approvalId, Long actorId, String action, String comment) {
        ApprovalHistory h = new ApprovalHistory();
        h.setApprovalId(approvalId);
        h.setActorId(actorId);
        h.setAction(action);
        h.setComment(comment);
        h.setCreatedAt(LocalDateTime.now());
        return h;
    }
}
```

- [ ] **Step 6: `ApprovalQueryService` 를 만든다 (조회 전용)**

```java
package com.flowmate.approval.service;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalHistory;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.mapper.ApprovalDocMapper;
import com.flowmate.approval.mapper.ApprovalHistoryMapper;
import com.flowmate.approval.mapper.ApprovalLineMapper;
import com.flowmate.common.exception.ApprovalAccessDeniedException;
import com.flowmate.common.exception.ApprovalNotFoundException;

/**
 * 결재 조회. 쓰기와 분리한 이유는 트랜잭션 속성이 다르고(readOnly),
 * 문서 접근 권한 검사가 조회에만 필요하기 때문이다.
 */
@Service
public class ApprovalQueryService {

    private final ApprovalDocMapper docMapper;
    private final ApprovalLineMapper lineMapper;
    private final ApprovalHistoryMapper historyMapper;

    public ApprovalQueryService(ApprovalDocMapper docMapper,
                               ApprovalLineMapper lineMapper,
                               ApprovalHistoryMapper historyMapper) {
        this.docMapper = docMapper;
        this.lineMapper = lineMapper;
        this.historyMapper = historyMapper;
    }

    /**
     * 문서 하나. **기안자이거나 결재선에 있는 사람만** 볼 수 있다.
     *
     * URL 인가로는 막을 수 없는 권한이다 — 문서마다 볼 수 있는 사람이 다르다 (설계서 §6.1).
     */
    @Transactional(readOnly = true)
    public ApprovalDoc findDoc(Long approvalId, Long viewerId) {
        ApprovalDoc doc = docMapper.findById(approvalId);
        if (doc == null) {
            throw new ApprovalNotFoundException(approvalId);
        }
        if (!canView(doc, approvalId, viewerId)) {
            throw new ApprovalAccessDeniedException("이 문서를 볼 권한이 없습니다");
        }
        return doc;
    }

    @Transactional(readOnly = true)
    public List<ApprovalLine> findLines(Long approvalId) {
        return lineMapper.findByApprovalId(approvalId);
    }

    @Transactional(readOnly = true)
    public List<ApprovalHistory> findHistories(Long approvalId) {
        return historyMapper.findByApprovalId(approvalId);
    }

    private boolean canView(ApprovalDoc doc, Long approvalId, Long viewerId) {
        if (Objects.equals(doc.getDrafterId(), viewerId)) {
            return true;
        }
        for (ApprovalLine line : lineMapper.findByApprovalId(approvalId)) {
            if (Objects.equals(line.getApproverId(), viewerId)) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 7: 테스트가 통과하는지 확인한다**

```powershell
.\mvnw.cmd verify "-Dit.test=ApprovalServiceIT"
```

기대: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0` (임시저장 6건)

- [ ] **Step 8: 기안 화면과 컨트롤러를 만든다**

`ApprovalWriteController.java`:

```java
package com.flowmate.approval.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.flowmate.approval.domain.ApprovalForm;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.service.ApprovalQueryService;
import com.flowmate.approval.service.ApprovalService;
import com.flowmate.org.security.LoginEmployee;

@Controller
@RequestMapping("/approval")
public class ApprovalWriteController {

    private final ApprovalService approvalService;
    private final ApprovalQueryService queryService;

    public ApprovalWriteController(ApprovalService approvalService, ApprovalQueryService queryService) {
        this.approvalService = approvalService;
        this.queryService = queryService;
    }

    /** 새 기안 또는 임시저장 문서 수정 */
    @GetMapping("/write")
    public String writeForm(@RequestParam(required = false) Long approvalId,
                            @AuthenticationPrincipal LoginEmployee loginEmployee,
                            Model model) {
        ApprovalForm form = new ApprovalForm();
        if (approvalId != null) {
            // var 를 쓰지 않는다 — 설계서 §3 이 Java 8 에 없는 문법을 최소화하라고 했다
            ApprovalDoc doc = queryService.findDoc(approvalId, loginEmployee.getEmpId());
            form.setApprovalId(doc.getApprovalId());
            form.setDocType(doc.getDocType());
            form.setTitle(doc.getTitle());
            form.setContent(doc.getContent());
            form.setAmount(doc.getAmount());
            model.addAttribute("doc", doc);
            model.addAttribute("lines", queryService.findLines(approvalId));
        }
        model.addAttribute("form", form);
        model.addAttribute("docTypes", DocType.ALL);
        return "approval/write";
    }

    /** 임시저장. 저장 후 같은 화면으로 돌아와 결재선을 보여준다 */
    @PostMapping("/draft")
    public String saveDraft(@ModelAttribute ApprovalForm form,
                           @AuthenticationPrincipal LoginEmployee loginEmployee) {
        Long id = approvalService.saveDraft(form, loginEmployee.getEmpId());
        return "redirect:/approval/write?approvalId=" + id;
    }
}
```

`DocType` 에 화면용 목록을 추가한다:

```java
    /** 화면 선택 상자 순서 */
    public static final List<String> ALL = List.of(EXPENSE, PURCHASE, LEAVE, CONTRACT, GENERAL);
```

`import java.util.List;` 를 추가한다.

`src/main/webapp/WEB-INF/views/approval/write.jsp`:

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="../common/head.jsp">
    <jsp:param name="pageTitle" value="기안 작성"/>
</jsp:include>
<body>
<jsp:include page="../common/header.jsp"/>
<div class="layout">
    <jsp:include page="../common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title">
            <c:choose>
                <c:when test="${form.approvalId != null}">기안 수정</c:when>
                <c:otherwise>기안 작성</c:otherwise>
            </c:choose>
        </h2>

        <c:if test="${doc != null}">
            <p class="result-count">
                문서번호 <strong><c:out value="${doc.docNo}"/></strong>
                · 상태 <span class="status status--${fn:toLowerCase(doc.status)}"><c:out value="${doc.status}"/></span>
            </p>
        </c:if>

        <form class="doc-form" method="post" action="${pageContext.request.contextPath}/approval/draft">
            <jsp:include page="../common/csrf-input.jsp"/>
            <c:if test="${form.approvalId != null}">
                <input type="hidden" name="approvalId" value="${form.approvalId}">
            </c:if>

            <div class="form-row">
                <label class="form-label" for="docType">문서 유형</label>
                <select class="form-input" id="docType" name="docType" required>
                    <c:forEach items="${docTypes}" var="t">
                        <option value="${t}" ${t eq form.docType ? 'selected' : ''}>
                            <c:out value="${t}"/>
                        </option>
                    </c:forEach>
                </select>
            </div>

            <div class="form-row">
                <label class="form-label" for="title">제목</label>
                <input class="form-input" type="text" id="title" name="title" maxlength="200" required
                       value="${fn:escapeXml(form.title)}">
            </div>

            <div class="form-row">
                <label class="form-label" for="amount">금액</label>
                <input class="form-input" type="number" id="amount" name="amount" min="0" step="1"
                       value="${form.amount}">
            </div>

            <div class="form-row">
                <label class="form-label" for="content">본문</label>
                <textarea class="form-input" id="content" name="content" rows="10"><c:out value="${form.content}"/></textarea>
            </div>

            <div class="form-row">
                <button class="btn btn--primary" type="submit">임시저장</button>
                <a class="btn btn--plain" href="${pageContext.request.contextPath}/approval/box">내 결재함</a>
            </div>
        </form>

        <c:if test="${not empty lines}">
            <h3 class="page-title">자동 생성된 결재선</h3>
            <ul class="approval-line">
                <c:forEach items="${lines}" var="line">
                    <li class="approval-line__item">
                        <span class="approval-line__step">${line.stepNo}</span>
                        <span class="approval-line__name"><c:out value="${line.approverName}"/></span>
                        <span class="approval-line__position"><c:out value="${line.approverPositionName}"/></span>
                        <span class="status status--${fn:toLowerCase(line.status)}"><c:out value="${line.status}"/></span>
                    </li>
                </c:forEach>
            </ul>
            <form method="post" action="${pageContext.request.contextPath}/approval/${form.approvalId}/submit">
                <jsp:include page="../common/csrf-input.jsp"/>
                <button class="btn btn--primary" type="submit">상신</button>
            </form>
        </c:if>

        <c:if test="${form.approvalId != null and empty lines}">
            <p class="alert alert--info">결재할 상위자가 없어 상신하면 즉시 완료됩니다.</p>
            <form method="post" action="${pageContext.request.contextPath}/approval/${form.approvalId}/submit">
                <jsp:include page="../common/csrf-input.jsp"/>
                <button class="btn btn--primary" type="submit">상신</button>
            </form>
        </c:if>
    </main>
</div>
<jsp:include page="../common/footer.jsp"/>
</body>
</html>
```

`sidebar.jsp` 에 전자결재 그룹을 추가한다 (기존 조직 그룹 뒤, 주석 자리에):

```jsp
    <ul class="lnb__group">
        <li class="lnb__group-title">전자결재</li>
        <li><a class="lnb__link" href="${pageContext.request.contextPath}/approval/write">기안 작성</a></li>
        <li><a class="lnb__link" href="${pageContext.request.contextPath}/approval/box">내 결재함</a></li>
    </ul>
```

- [ ] **Step 9: `style.css` 클래스 목록에 새 클래스를 추가한다**

`Phase 2` 절에 적는다. **규칙은 여전히 CSS 를 쓰지 않고 목록만 갱신하는 것**이다.

```
 * ── 클래스 목록 (Phase 2) ────────────────────────────────────────
 * 문서폼     .doc-form
 * 결재선     .approval-line  .approval-line__item  .approval-line__step
 *            .approval-line__name  .approval-line__position
 * 상태       .status  .status--draft  .status--pending  .status--approved
 *            .status--rejected  .status--canceled
 *            .status--waiting  .status--current  .status--skipped
```

> 최소선 CSS 를 이미 넣었으므로 이 Phase 종료 시점(Task 11)에 `.status--*` 색만 얹는다.
> 목록을 지금 적어 두는 것이 그때 화면을 뒤지지 않게 하는 조건이다.

- [ ] **Step 10: 화면을 HTTP 로 확인한다**

서버를 띄우고 쿠키 병으로 `2020003` / `flowmate1!` 로그인 후:

1. `GET /approval/write` → 200, 유형 선택 상자 5개, CSRF hidden input 존재
2. `POST /approval/draft` (docType=EXPENSE, title, amount=1000000) → 302 `/approval/write?approvalId=N`
3. 그 화면에 문서번호 `EXP-2026-####` 와 결재선 2건(신동혁·박현주)이 보인다
4. amount=5000000 으로 같은 문서를 다시 저장 → 결재선이 3건(이사 추가)으로 바뀐다
5. 한글이 깨지지 않는다

서버를 끄고 8080 이 해제됐는지 확인한다.

- [ ] **Step 11: 커밋한다**

```powershell
.\mvnw.cmd clean verify
git add src/main/java src/main/webapp src/main/resources src/test/java
git status -s
```

메시지:

```
feat: 기안 작성과 임시저장, 결재선 자동 생성

임시저장 시 결재선을 만들고, 수정 시 지우고 다시 만든다. 금액이나 유형이 바뀌면
정책 결과가 달라지는데 기존 결재선을 남겨두면 화면과 실제 규칙이 어긋난다.

문서번호는 MAX+1 이라 동시 기안 시 충돌할 수 있다. doc_no 의 UNIQUE 제약이
최후 방어선이고 충돌하면 번호를 다시 계산해 재시도한다.

common/csrf-input.jsp 조각을 만든다. Spring Security 6 은 일반 form 에 토큰을
자동 주입하지 않고, 빠뜨린 폼은 제출 시점에만 403 이 되어 신호가 늦다.
파일마다 복붙하면 언젠가 한 곳을 빠뜨린다.

문서 조회 권한을 Service 에서 검사한다. 기안자이거나 결재선에 있는 사람만 볼 수 있고,
이것은 문서마다 다르므로 URL 인가로는 막을 수 없다.
```

---

## Task 7: 상신 · 승인 · 반려 (TDD) ★

> 설계서 §9 Day 7 의 완료 기준: *"승인·반려가 되고 `approval_reject_history` 에 유형이 저장된다"*.
> 반려 유형 수집이 Phase 5 AI 사전점검의 학습 원천이므로 **유형 없는 반려를 허용하지 않는다.**

**Files:**
- Modify: `src/test/java/com/flowmate/approval/service/ApprovalServiceIT.java` (테스트 추가)
- Modify: `src/main/java/com/flowmate/approval/service/ApprovalService.java`
- Create: `src/main/java/com/flowmate/approval/controller/ApprovalActionController.java`

- [ ] **Step 1: 테스트를 추가한다 (기존 6건 유지)**

`ApprovalServiceIT` 에 이어 붙인다. `import` 추가: `ApprovalHistory`, `HistoryAction`, `RejectReason`,
`com.flowmate.approval.mapper.ApprovalHistoryMapper`, `org.springframework.jdbc.core.JdbcTemplate`.
`@Autowired` 필드로 `ApprovalHistoryMapper historyMapper` 와 `JdbcTemplate jdbcTemplate` 를 추가한다.

```java
    @Test
    @DisplayName("상신하면 진행 중이 되고 1단계가 현재 차례가 된다")
    void submitActivatesFirstStep() {
        Long id = approvalService.saveDraft(newForm("상신 대상", "1000000"), KWAK);

        approvalService.submit(id, KWAK);

        ApprovalDoc doc = queryService.findDoc(id, KWAK);
        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(doc.getCurrentStep()).isEqualTo(1);

        List<ApprovalLine> lines = lineMapper.findByApprovalId(id);
        assertThat(lines.get(0).getStatus()).isEqualTo(LineStatus.CURRENT);
        assertThat(lines.get(1).getStatus()).isEqualTo(LineStatus.WAITING);
    }

    @Test
    @DisplayName("결재자가 없으면 상신 즉시 완료된다")
    void submitWithoutApproverCompletesImmediately() {
        Long id = approvalService.saveDraft(newForm("이사 기안", "1000000"), JEONG);

        approvalService.submit(id, JEONG);

        ApprovalDoc doc = queryService.findDoc(id, JEONG);
        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(doc.getCompletedAt()).isNotNull();
        // 승인한 사람이 없으므로 APPROVE 이력을 지어내지 않는다
        assertThat(queryService.findHistories(id))
                .extracting(ApprovalHistory::getAction)
                .containsExactly(HistoryAction.DRAFT, HistoryAction.SUBMIT);
    }

    @Test
    @DisplayName("기안자가 아니면 상신할 수 없다")
    void onlyDrafterCanSubmit() {
        Long id = approvalService.saveDraft(newForm("남의 문서", "1000000"), KWAK);

        assertThatThrownBy(() -> approvalService.submit(id, SHIN))
                .isInstanceOf(ApprovalAccessDeniedException.class);
    }

    @Test
    @DisplayName("★ 사원 기안 → 팀장 승인 → 부장 승인 → 완료 전 과정이 이어진다")
    void fullApprovalFlowCompletes() {
        Long id = approvalService.saveDraft(newForm("전 과정", "1000000"), KWAK);
        approvalService.submit(id, KWAK);

        approvalService.approve(id, SHIN, "확인했습니다");

        ApprovalDoc mid = queryService.findDoc(id, KWAK);
        assertThat(mid.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(mid.getCurrentStep()).isEqualTo(2);
        assertThat(lineMapper.findStep(id, 1).getStatus()).isEqualTo(LineStatus.APPROVED);
        assertThat(lineMapper.findStep(id, 2).getStatus()).isEqualTo(LineStatus.CURRENT);

        approvalService.approve(id, PARK, null);

        ApprovalDoc done = queryService.findDoc(id, KWAK);
        assertThat(done.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(done.getCompletedAt()).isNotNull();
        assertThat(lineMapper.findStep(id, 2).getStatus()).isEqualTo(LineStatus.APPROVED);

        assertThat(queryService.findHistories(id))
                .extracting(ApprovalHistory::getAction)
                .containsExactly(HistoryAction.DRAFT, HistoryAction.SUBMIT,
                                 HistoryAction.APPROVE, HistoryAction.APPROVE);
    }

    @Test
    @DisplayName("자기 차례가 아니면 승인할 수 없다")
    void cannotApproveOutOfTurn() {
        Long id = approvalService.saveDraft(newForm("순서 확인", "1000000"), KWAK);
        approvalService.submit(id, KWAK);

        // 2단계 결재자가 1단계를 건너뛰고 승인하려 함
        assertThatThrownBy(() -> approvalService.approve(id, PARK, null))
                .isInstanceOf(ApprovalAccessDeniedException.class);
    }

    @Test
    @DisplayName("★ 반려하면 뒤 단계가 건너뛰기 처리되고 반려 이력이 유형과 함께 쌓인다")
    void rejectSkipsRemainingAndRecordsReason() {
        Long id = approvalService.saveDraft(newForm("반려 대상", "1000000"), KWAK);
        approvalService.submit(id, KWAK);

        approvalService.reject(id, SHIN, RejectReason.MISSING_EVIDENCE, "견적서를 첨부해 주세요");

        ApprovalDoc doc = queryService.findDoc(id, KWAK);
        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(lineMapper.findStep(id, 1).getStatus()).isEqualTo(LineStatus.REJECTED);
        assertThat(lineMapper.findStep(id, 2).getStatus()).isEqualTo(LineStatus.SKIPPED);

        // ★ Phase 5 사전점검이 읽는 표
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_reject_history WHERE approval_id = ? AND reason_category = ?",
                Integer.class, id, RejectReason.MISSING_EVIDENCE);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("반려 유형이 없거나 잘못되면 반려 자체를 거부한다")
    void rejectRequiresValidReasonCategory() {
        Long id = approvalService.saveDraft(newForm("유형 검증", "1000000"), KWAK);
        approvalService.submit(id, KWAK);

        assertThatThrownBy(() -> approvalService.reject(id, SHIN, null, "이유"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> approvalService.reject(id, SHIN, "NOT_A_REASON", "이유"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("첫 승인 전이면 기안자가 회수할 수 있다")
    void drafterCanCancelBeforeFirstApproval() {
        Long id = approvalService.saveDraft(newForm("회수 대상", "1000000"), KWAK);
        approvalService.submit(id, KWAK);

        approvalService.cancel(id, KWAK);

        assertThat(queryService.findDoc(id, KWAK).getStatus()).isEqualTo(ApprovalStatus.CANCELED);
    }

    @Test
    @DisplayName("한 단계라도 승인되면 회수할 수 없다")
    void cannotCancelAfterFirstApproval() {
        Long id = approvalService.saveDraft(newForm("회수 불가", "1000000"), KWAK);
        approvalService.submit(id, KWAK);
        approvalService.approve(id, SHIN, null);

        assertThatThrownBy(() -> approvalService.cancel(id, KWAK))
                .isInstanceOf(IllegalStateException.class);
    }
```

- [ ] **Step 2: 테스트가 실패하는지 확인한다**

```powershell
.\mvnw.cmd verify "-Dit.test=ApprovalServiceIT"
```

기대: 컴파일 실패 — `cannot find symbol: method submit/approve/reject/cancel`.

- [ ] **Step 3: `ApprovalService` 에 전이 메서드 4종을 추가한다**

```java
    /**
     * 상신한다. 결재선 길이를 도메인 객체에 넘겨 결재자가 0명인 경우까지 한 곳에서 판정하게 한다.
     */
    @Transactional
    public void submit(Long approvalId, Long actorId) {
        ApprovalDoc doc = requireDocForUpdate(approvalId);
        if (!Objects.equals(doc.getDrafterId(), actorId)) {
            throw new ApprovalAccessDeniedException("기안자만 상신할 수 있습니다");
        }
        int approverCount = lineMapper.countByApprovalId(approvalId);

        doc.submit(approverCount);
        docMapper.updateStatus(doc);

        if (approverCount > 0) {
            lineMapper.activateStep(approvalId, 1);
        }
        historyMapper.insert(HistoryFactory.of(approvalId, actorId, HistoryAction.SUBMIT, null));
    }

    /**
     * 현재 단계를 승인한다.
     *
     * 행 잠금(FOR UPDATE)으로 조회하는 이유: 두 결재자가 동시에 누르면 잠금이 없으면
     * 둘 다 같은 current_step 을 읽어 각자 +1 해서 단계가 하나 건너뛰어진다.
     */
    @Transactional
    public void approve(Long approvalId, Long actorId, String comment) {
        ApprovalDoc doc = requireDocForUpdate(approvalId);
        ApprovalLine current = requireMyCurrentLine(doc, approvalId, actorId);
        int totalStep = lineMapper.countByApprovalId(approvalId);

        current.setStatus(LineStatus.APPROVED);
        current.setComment(comment);
        current.setProcessedAt(LocalDateTime.now());
        lineMapper.updateStep(current);

        doc.approve(totalStep);
        docMapper.updateStatus(doc);

        if (!doc.isCompleted()) {
            lineMapper.activateStep(approvalId, doc.getCurrentStep());
        }
        historyMapper.insert(HistoryFactory.of(approvalId, actorId, HistoryAction.APPROVE, comment));

        // Phase 4 훅 자리 — 연차 신청서가 최종 승인되면 근태에 반영한다.
        // Spring 이벤트가 아니라 직접 호출로 붙인다. 같은 트랜잭션에서 어느 한쪽이
        // 실패하면 전부 롤백되어야 하기 때문이다 (설계서 §6.3).
    }

    /**
     * 반려한다. 반려 유형이 없으면 거부한다 — 이 값이 Phase 5 사전점검의 학습 원천이다.
     */
    @Transactional
    public void reject(Long approvalId, Long actorId, String reasonCategory, String reasonText) {
        if (!RejectReason.isValid(reasonCategory)) {
            throw new IllegalArgumentException("반려 유형을 선택해야 합니다: " + reasonCategory);
        }
        ApprovalDoc doc = requireDocForUpdate(approvalId);
        ApprovalLine current = requireMyCurrentLine(doc, approvalId, actorId);

        current.setStatus(LineStatus.REJECTED);
        current.setComment(reasonText);
        current.setProcessedAt(LocalDateTime.now());
        lineMapper.updateStep(current);

        // 뒤 단계는 차례가 오지 않는다
        lineMapper.skipRemaining(approvalId, doc.getCurrentStep() + 1);

        doc.reject();
        docMapper.updateStatus(doc);
        historyMapper.insert(HistoryFactory.of(approvalId, actorId, HistoryAction.REJECT, reasonText));

        // ★ Phase 5 AI 사전점검이 읽는 표. 비정규화된 doc_type/dept_id 를 여기서 채운다.
        RejectHistory reject = new RejectHistory();
        reject.setApprovalId(approvalId);
        reject.setDocType(doc.getDocType());
        reject.setDeptId(doc.getDeptId());
        reject.setRejectorId(actorId);
        reject.setReasonCategory(reasonCategory);
        reject.setReasonText(reasonText);
        reject.setRejectedAt(LocalDateTime.now());
        rejectHistoryMapper.insert(reject);
    }

    /** 기안자가 회수한다. 가능 여부 판정은 도메인 객체가 한다 */
    @Transactional
    public void cancel(Long approvalId, Long actorId) {
        ApprovalDoc doc = requireDocForUpdate(approvalId);

        doc.cancel(actorId);
        docMapper.updateStatus(doc);

        lineMapper.skipRemaining(approvalId, 1);
        historyMapper.insert(HistoryFactory.of(approvalId, actorId, HistoryAction.CANCEL, null));
    }

    private ApprovalDoc requireDocForUpdate(Long approvalId) {
        ApprovalDoc doc = docMapper.findByIdForUpdate(approvalId);
        if (doc == null) {
            throw new ApprovalNotFoundException(approvalId);
        }
        return doc;
    }

    /**
     * 지금 이 사람이 처리할 차례인 결재선 단계를 돌려준다.
     *
     * 세 가지를 함께 검사한다 — 문서가 진행 중인지, 그 단계가 존재하는지,
     * 그 단계의 결재자가 요청자인지. 하나라도 어긋나면 권한 예외다.
     */
    private ApprovalLine requireMyCurrentLine(ApprovalDoc doc, Long approvalId, Long actorId) {
        ApprovalLine line = lineMapper.findStep(approvalId, doc.getCurrentStep());
        if (line == null || !LineStatus.CURRENT.equals(line.getStatus())) {
            throw new ApprovalAccessDeniedException("지금 처리할 수 있는 단계가 아닙니다");
        }
        if (!Objects.equals(line.getApproverId(), actorId)) {
            throw new ApprovalAccessDeniedException("이 단계의 결재자가 아닙니다");
        }
        return line;
    }
```

`import` 추가: `LineStatus`, `RejectHistory`, `RejectReason`.

- [ ] **Step 4: 테스트가 통과하는지 확인한다**

```powershell
.\mvnw.cmd verify "-Dit.test=ApprovalServiceIT"
```

기대: `Tests run: 15, Failures: 0, Errors: 0, Skipped: 0` (임시저장 6 + 전이 9)

진단표:

| 증상 | 원인 |
|---|---|
| 1단계가 CURRENT 가 안 됨 | `activateStep` 호출 누락, 또는 `status = 'WAITING'` 조건에 걸려 갱신 0건 |
| 최종 승인 후에도 PENDING | `countByApprovalId` 가 아닌 값을 `totalStep` 으로 넘겼다 |
| 반려 후 2단계가 CURRENT 로 남음 | `skipRemaining` 의 시작 단계가 `currentStep + 1` 이 아니다 |
| 반려 이력이 안 쌓임 | `rejectHistoryMapper.insert` 누락, 또는 유형 검증에서 먼저 예외 |
| 회수 후 결재선이 CURRENT 로 남음 | `cancel` 의 `skipRemaining(approvalId, 1)` 누락 |

- [ ] **Step 5: 액션 컨트롤러를 만든다**

```java
package com.flowmate.approval.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.flowmate.approval.service.ApprovalService;
import com.flowmate.org.security.LoginEmployee;

/**
 * 결재 처리 액션. 모두 POST 다 — 상태를 바꾸는 요청을 GET 으로 두면
 * 브라우저 프리페치나 크롤러가 결재를 처리해 버릴 수 있다.
 */
@Controller
@RequestMapping("/approval")
public class ApprovalActionController {

    private final ApprovalService approvalService;

    public ApprovalActionController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @PostMapping("/{approvalId}/submit")
    public String submit(@PathVariable Long approvalId,
                         @AuthenticationPrincipal LoginEmployee loginEmployee) {
        approvalService.submit(approvalId, loginEmployee.getEmpId());
        return "redirect:/approval/" + approvalId;
    }

    @PostMapping("/{approvalId}/approve")
    public String approve(@PathVariable Long approvalId,
                          @RequestParam(required = false) String comment,
                          @AuthenticationPrincipal LoginEmployee loginEmployee) {
        approvalService.approve(approvalId, loginEmployee.getEmpId(), comment);
        return "redirect:/approval/" + approvalId;
    }

    @PostMapping("/{approvalId}/reject")
    public String reject(@PathVariable Long approvalId,
                         @RequestParam String reasonCategory,
                         @RequestParam(required = false) String reasonText,
                         @AuthenticationPrincipal LoginEmployee loginEmployee) {
        approvalService.reject(approvalId, loginEmployee.getEmpId(), reasonCategory, reasonText);
        return "redirect:/approval/" + approvalId;
    }

    @PostMapping("/{approvalId}/cancel")
    public String cancel(@PathVariable Long approvalId,
                         @AuthenticationPrincipal LoginEmployee loginEmployee) {
        approvalService.cancel(approvalId, loginEmployee.getEmpId());
        return "redirect:/approval/" + approvalId;
    }
}
```

- [ ] **Step 6: 전체 빌드 후 커밋한다**

```powershell
.\mvnw.cmd clean verify
```

기대: Surefire `Tests run: 52`, Failsafe `Tests run: 40` (기존 21 + Mapper 10 + Service 15 - 6 중복 없음 → 46).
**실제 숫자는 실행 결과를 기록한다** — 위 계산은 추정이므로 다르면 실제 값을 이 계획서에 반영한다.

```powershell
git add src/main/java src/test/java
git status -s
```

메시지:

```
feat: 상신 · 승인 · 반려 · 회수 처리

승인 조회에 FOR UPDATE 를 쓴다. 두 결재자가 동시에 누르면 잠금이 없으면 둘 다
같은 current_step 을 읽어 각자 +1 해서 단계가 하나 건너뛰어진다.

반려 유형이 없거나 목록에 없는 값이면 반려 자체를 거부한다. 이 값이 Phase 5
사전점검의 학습 원천이므로, 자유 텍스트만 받으면 유형별 빈도를 집계할 수 없고
집계가 없으면 과거 반려 건수를 근거로 제시할 수 없다.

결재자가 0명인 문서는 상신 즉시 완료되며 APPROVE 이력을 지어내지 않는다.
승인한 사람이 없기 때문이다.

액션은 전부 POST 다. 상태를 바꾸는 요청을 GET 으로 두면 브라우저 프리페치가
결재를 처리해 버릴 수 있다.

approve() 에 Phase 4 훅 자리를 주석으로 남겼다. Spring 이벤트가 아니라 직접 호출로
붙일 예정이다 - 같은 트랜잭션에서 한쪽이 실패하면 전부 롤백되어야 한다.
```

---

## Task 8: 내 결재함 (탭 4종 · 페이징)

**Files:**
- Test: `src/test/java/com/flowmate/approval/service/ApprovalQueryServiceIT.java`
- Modify: `src/main/java/com/flowmate/approval/service/ApprovalQueryService.java`
- Create: `src/main/java/com/flowmate/approval/controller/ApprovalBoxController.java`
- Create: `src/main/webapp/WEB-INF/views/approval/box.jsp`
- Modify: `src/main/java/com/flowmate/config/SecurityConfig.java` (로드맵 C5 해소)

- [ ] **Step 1: 실패하는 통합 테스트를 쓴다**

시드(문서 6건)를 그대로 쓴다. 기안자는 전부 곽수빈(18), 결재선은 신동혁(14) → 박현주(3).

```java
package com.flowmate.approval.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalSearchCond;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.DocType;
import com.flowmate.common.web.Page;

/**
 * 내 결재함의 탭 4종. Task 1 의 시드 6건을 전제로 한다.
 *
 * 시드: DRAFT 1건(id 1), PENDING 2건(id 2 는 1단계 CURRENT=신동혁, id 3 은 2단계 CURRENT=박현주),
 *       APPROVED 1건(id 4), REJECTED 1건(id 5), CANCELED 1건(id 6)
 */
@SpringBootTest
@Transactional
class ApprovalQueryServiceIT {

    private static final Long KWAK = 18L;
    private static final Long SHIN = 14L;
    private static final Long PARK = 3L;

    @Autowired
    private ApprovalQueryService queryService;

    @Test
    @DisplayName("기안 탭은 내가 기안한 문서 전부를 보여준다")
    void draftedTabShowsAllMyDocs() {
        Page<ApprovalDoc> page = queryService.searchBox(cond("drafted", KWAK));

        assertThat(page.getTotalCount()).isEqualTo(6);
        assertThat(page.getContent()).allSatisfy(d -> assertThat(d.getDrafterId()).isEqualTo(KWAK));
    }

    @Test
    @DisplayName("대기 탭은 지금 내 차례인 문서만 보여준다")
    void pendingTabShowsOnlyMyTurn() {
        // 신동혁은 id 2 의 1단계가 CURRENT
        Page<ApprovalDoc> shinBox = queryService.searchBox(cond("pending", SHIN));
        assertThat(shinBox.getContent()).extracting(ApprovalDoc::getApprovalId).containsExactly(2L);

        // 박현주는 id 3 의 2단계가 CURRENT
        Page<ApprovalDoc> parkBox = queryService.searchBox(cond("pending", PARK));
        assertThat(parkBox.getContent()).extracting(ApprovalDoc::getApprovalId).containsExactly(3L);

        // 기안자는 대기 탭에 아무것도 없다
        assertThat(queryService.searchBox(cond("pending", KWAK)).getTotalCount()).isZero();
    }

    @Test
    @DisplayName("완료 탭은 내가 처리를 끝낸 문서를 보여준다")
    void doneTabShowsWhatIProcessed() {
        // 신동혁: id 3 승인, id 4 승인, id 5 반려
        Page<ApprovalDoc> page = queryService.searchBox(cond("done", SHIN));

        assertThat(page.getContent()).extracting(ApprovalDoc::getApprovalId)
                .containsExactlyInAnyOrder(3L, 4L, 5L);
    }

    @Test
    @DisplayName("반려 탭은 내가 기안했고 반려된 문서만 보여준다")
    void rejectedTabShowsMyRejectedDocs() {
        Page<ApprovalDoc> page = queryService.searchBox(cond("rejected", KWAK));

        assertThat(page.getContent()).extracting(ApprovalDoc::getApprovalId).containsExactly(5L);
        assertThat(page.getContent()).allSatisfy(
                d -> assertThat(d.getStatus()).isEqualTo(ApprovalStatus.REJECTED));
    }

    @Test
    @DisplayName("문서 유형과 검색어로 좁힐 수 있다")
    void narrowsByDocTypeAndKeyword() {
        ApprovalSearchCond byType = cond("drafted", KWAK);
        byType.setDocType(DocType.PURCHASE);
        assertThat(byType).isNotNull();
        assertThat(queryService.searchBox(byType).getTotalCount()).isEqualTo(2);

        ApprovalSearchCond byKeyword = cond("drafted", KWAK);
        byKeyword.setKeyword("출장비");
        assertThat(queryService.searchBox(byKeyword).getTotalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("전체 페이지를 넘는 페이지를 요청하면 마지막 페이지로 보정한다")
    void clampsPageBeyondLast() {
        ApprovalSearchCond cond = cond("drafted", KWAK);
        cond.setSize(2);
        cond.setPage(99);

        Page<ApprovalDoc> page = queryService.searchBox(cond);

        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getPage()).isEqualTo(3);
        assertThat(page.getStartPage()).isLessThanOrEqualTo(page.getEndPage());
        assertThat(page.getContent()).isNotEmpty();
    }

    @Test
    @DisplayName("LIKE 와일드카드를 입력해도 리터럴로 검색된다")
    void treatsWildcardAsLiteral() {
        ApprovalSearchCond cond = cond("drafted", KWAK);
        cond.setKeyword("%");

        assertThat(queryService.searchBox(cond).getTotalCount()).isZero();
    }

    private ApprovalSearchCond cond(String tab, Long empId) {
        ApprovalSearchCond c = new ApprovalSearchCond();
        c.setTab(tab);
        c.setEmpId(empId);
        return c;
    }
}
```

- [ ] **Step 2: `ApprovalQueryService.searchBox` 를 추가한다**

Phase 1 의 `EmployeeService.search` 와 **같은 페이지 보정 패턴**을 쓴다.

```java
    /**
     * 내 결재함. 건수를 먼저 읽고 요청 페이지를 실제 마지막 페이지로 보정한 뒤 목록을 읽는다.
     *
     * 보정하지 않으면 startPage 가 endPage 보다 커져 pagination.jsp 의
     * c:forEach 가 예외 없이 링크를 0개 그린다 — 페이징이 조용히 죽는다.
     * Phase 1 의 EmployeeService 와 같은 이유, 같은 순서다.
     */
    @Transactional(readOnly = true)
    public Page<ApprovalDoc> searchBox(ApprovalSearchCond cond) {
        long totalCount = docMapper.countBox(cond);

        int totalPages = Page.totalPagesOf(totalCount, cond.getSize());
        if (cond.getPage() > totalPages) {
            cond.setPage(totalPages);
        }

        List<ApprovalDoc> content = docMapper.searchBox(cond);
        return new Page<>(content, cond.getPage(), cond.getSize(), totalCount);
    }
```

`import com.flowmate.approval.domain.ApprovalSearchCond;` 와 `import com.flowmate.common.web.Page;` 를 추가한다.

- [ ] **Step 3: 컨트롤러와 화면을 만든다**

```java
package com.flowmate.approval.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.flowmate.approval.domain.ApprovalSearchCond;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.RejectReason;
import com.flowmate.approval.service.ApprovalQueryService;
import com.flowmate.org.security.LoginEmployee;

@Controller
@RequestMapping("/approval")
public class ApprovalBoxController {

    private final ApprovalQueryService queryService;

    public ApprovalBoxController(ApprovalQueryService queryService) {
        this.queryService = queryService;
    }

    /**
     * 내 결재함. empId 는 화면이 보내는 값을 쓰지 않고 로그인 주체에서 강제로 덮어쓴다 —
     * 요청 파라미터로 남의 결재함을 볼 수 있으면 안 된다.
     */
    @GetMapping("/box")
    public String box(@ModelAttribute("cond") ApprovalSearchCond cond,
                      @AuthenticationPrincipal LoginEmployee loginEmployee,
                      Model model) {
        cond.setEmpId(loginEmployee.getEmpId());
        model.addAttribute("paging", queryService.searchBox(cond));
        model.addAttribute("docTypes", DocType.ALL);
        return "approval/box";
    }

    @GetMapping("/{approvalId}")
    public String detail(@PathVariable Long approvalId,
                         @AuthenticationPrincipal LoginEmployee loginEmployee,
                         Model model) {
        Long viewerId = loginEmployee.getEmpId();
        ApprovalDoc doc = queryService.findDoc(approvalId, viewerId);
        List<ApprovalLine> lines = queryService.findLines(approvalId);

        model.addAttribute("doc", doc);
        model.addAttribute("lines", lines);
        model.addAttribute("histories", queryService.findHistories(approvalId));
        model.addAttribute("rejectReasons", RejectReason.ALL);
        model.addAttribute("myTurn", queryService.isMyTurn(doc, lines, viewerId));
        model.addAttribute("canCancel", queryService.canCancel(doc, viewerId));
        return "approval/detail";
    }
}
```

`ApprovalQueryService` 에 화면 판정 두 개를 추가한다. **화면이 조건을 직접 계산하지 않게** 하는 것이 목적이다 —
JSP 에 `${doc.status == 'PENDING' and ...}` 같은 식이 흩어지면 규칙이 화면마다 어긋난다.

```java
    /** 지금 이 사람이 처리할 차례인가 */
    public boolean isMyTurn(ApprovalDoc doc, List<ApprovalLine> lines, Long viewerId) {
        if (!ApprovalStatus.PENDING.equals(doc.getStatus())) {
            return false;
        }
        for (ApprovalLine line : lines) {
            if (line.isCurrent() && Objects.equals(line.getApproverId(), viewerId)) {
                return true;
            }
        }
        return false;
    }

    /** 회수 버튼을 보여줄지. 판정 규칙은 도메인 객체와 같아야 하므로 그대로 옮긴다 */
    public boolean canCancel(ApprovalDoc doc, Long viewerId) {
        if (!Objects.equals(doc.getDrafterId(), viewerId)) {
            return false;
        }
        if (ApprovalStatus.DRAFT.equals(doc.getStatus())) {
            return true;
        }
        return ApprovalStatus.PENDING.equals(doc.getStatus()) && doc.getCurrentStep() <= 1;
    }
```

`src/main/webapp/WEB-INF/views/approval/box.jsp`:

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="../common/head.jsp">
    <jsp:param name="pageTitle" value="내 결재함"/>
</jsp:include>
<body>
<jsp:include page="../common/header.jsp"/>
<div class="layout">
    <jsp:include page="../common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title">내 결재함</h2>

        <%--
          탭. 링크가 아니라 hidden tab 을 바꿔 #searchForm 을 다시 보낸다.
          링크로 만들면 현재 검색어·유형이 URL 조립 대상이 되어 pagination.jsp 와
          같은 문제를 반복하게 된다.
        --%>
        <ul class="box-tabs">
            <c:forEach items="${['drafted','pending','done','rejected']}" var="t">
                <li class="box-tabs__item">
                    <a class="box-tabs__link ${t eq cond.tab ? 'box-tabs__link--active' : ''}"
                       href="#" data-tab="${t}">
                        <c:choose>
                            <c:when test="${t eq 'drafted'}">기안</c:when>
                            <c:when test="${t eq 'pending'}">대기</c:when>
                            <c:when test="${t eq 'done'}">완료</c:when>
                            <c:otherwise>반려</c:otherwise>
                        </c:choose>
                    </a>
                </li>
            </c:forEach>
        </ul>

        <form id="searchForm" class="search-form" method="get"
              action="${pageContext.request.contextPath}/approval/box">
            <input type="hidden" name="page" value="${paging.page}">
            <input type="hidden" name="tab" id="tab" value="${fn:escapeXml(cond.tab)}">
            <div class="form-row">
                <label class="form-label" for="docType">유형</label>
                <select class="form-input" id="docType" name="docType">
                    <option value="">전체</option>
                    <c:forEach items="${docTypes}" var="t">
                        <option value="${t}" ${t eq cond.docType ? 'selected' : ''}><c:out value="${t}"/></option>
                    </c:forEach>
                </select>
                <label class="form-label" for="keyword">검색</label>
                <input class="form-input" type="text" id="keyword" name="keyword"
                       value="${fn:escapeXml(cond.keyword)}" placeholder="제목 또는 문서번호">
                <button class="btn btn--primary" type="submit">검색</button>
            </div>
        </form>

        <p class="result-count">전체 <strong>${paging.totalCount}</strong>건</p>

        <table class="doc-list">
            <thead>
            <tr>
                <th>문서번호</th><th>유형</th><th>제목</th><th>금액</th>
                <th>기안자</th><th>기안일</th><th>상태</th>
            </tr>
            </thead>
            <tbody>
            <c:choose>
                <c:when test="${paging.totalCount == 0}">
                    <tr><td class="doc-list__empty" colspan="7">조회 결과가 없습니다.</td></tr>
                </c:when>
                <c:otherwise>
                    <c:forEach items="${paging.content}" var="d">
                        <tr>
                            <td>
                                <a class="doc-list__link"
                                   href="${pageContext.request.contextPath}/approval/${d.approvalId}">
                                    <c:out value="${d.docNo}"/>
                                </a>
                            </td>
                            <td><c:out value="${d.docTypeLabel}"/></td>
                            <td><c:out value="${d.title}"/></td>
                            <td class="doc-list__amount">${d.amount}</td>
                            <td><c:out value="${d.drafterName}"/></td>
                            <td>${d.draftedAt}</td>
                            <td>
                                <span class="status status--${fn:toLowerCase(d.status)}">
                                    <c:out value="${d.status}"/>
                                </span>
                            </td>
                        </tr>
                    </c:forEach>
                </c:otherwise>
            </c:choose>
            </tbody>
        </table>

        <jsp:include page="../common/pagination.jsp"/>
    </main>
</div>
<jsp:include page="../common/footer.jsp"/>
</body>
</html>
```

`static/js/common.js` 에 탭 위임을 추가한다 (기존 페이징 위임 아래):

```javascript
    /*
     * 내 결재함 탭. 페이징과 같은 이유로 링크가 아니라 폼 재전송이다.
     * 탭을 바꾸면 페이지는 1로 되돌린다 — 3페이지에서 탭을 옮기면
     * 그 탭에 3페이지가 없어 빈 화면이 된다.
     */
    $(document).on('click', '.box-tabs__link[data-tab]', function (event) {
        event.preventDefault();
        var $form = $('#searchForm');
        if ($form.length === 0) {
            return;
        }
        $form.find('input[name="tab"]').val($(this).data('tab'));
        $form.find('input[name="page"]').val(1);
        $form.trigger('submit');
    });
```

- [ ] **Step 4: 로드맵 C5 를 해소한다 — 딥링크 보존**

`SecurityConfig` 의 `defaultSuccessUrl("/", true)` 를 바꾼다. 내 결재함과 문서 상세가
알림·링크 공유의 대상이 되므로, 로그인 후 원래 가려던 화면으로 돌아가야 한다.

```java
                // 저장된 요청이 있으면 그곳으로 돌아간다 (로드맵 C5).
                // 문서 상세가 딥링크 대상이 되었으므로 항상 홈으로 보내면
                // 링크를 받은 사용자가 로그인 후 다시 링크를 눌러야 한다.
                //
                // 오픈 리다이렉트 위험은 없다 — 저장된 요청은 실제로 거부된 요청에서
                // 서버가 만든 값이고 URL 파라미터로 조작할 수 없다.
                .defaultSuccessUrl("/", false)
```

- [ ] **Step 5: 테스트와 화면을 확인한다**

```powershell
.\mvnw.cmd verify "-Dit.test=ApprovalQueryServiceIT"
```

기대: `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`

HTTP 확인 (쿠키 병 사용):

| 계정 | 확인 |
|---|---|
| `2020003` 곽수빈 | `/approval/box` → 기안 탭 6건 / 대기 탭 0건 / 반려 탭 1건 |
| `2016004` 신동혁 | 대기 탭 1건(EXP-2026-0002) / 완료 탭 3건 |
| `2016002` 박현주 | 대기 탭 1건(PUR-2026-0001) |

`?tab=drafted&size=2&page=99` 로 요청해 hidden `page` 가 `3` 으로 보정되고 페이징 링크가 그려지는지 확인한다.

- [ ] **Step 6: `style.css` 목록에 추가하고 커밋한다**

```
 * 결재함     .box-tabs  .box-tabs__item  .box-tabs__link  .box-tabs__link--active
 * 문서목록   .doc-list  .doc-list__empty  .doc-list__link  .doc-list__amount
```

커밋 메시지:

```
feat: 내 결재함 - 탭 4종과 페이징

대기 탭은 결재선의 CURRENT 와 내 사원번호를 EXISTS 로 맞춰 판정한다.
idx_line_approver 가 이 조회를 받친다.

empId 는 요청 파라미터를 쓰지 않고 로그인 주체에서 덮어쓴다. 파라미터로 남의
결재함을 볼 수 있으면 안 된다.

탭도 페이징과 같은 폼 재전송 방식이다. 링크로 만들면 현재 검색어와 유형이
URL 조립 대상이 되어 같은 문제를 반복한다. 탭을 바꿀 때 페이지를 1로 되돌리는 이유는
3페이지에서 탭을 옮기면 그 탭에 3페이지가 없어 빈 화면이 되기 때문이다.

로그인 후 저장된 요청으로 돌아가게 바꿨다(로드맵 C5). 문서 상세가 딥링크 대상이
되었으므로 항상 홈으로 보내면 링크를 받은 사용자가 다시 눌러야 한다.
```

---

## Task 9: 문서 상세 · 결재선 · 이력 · 반려 유형 선택

> 설계서 §5.2: *"이 테이블을 위해 반려 화면을 재설계한다"*.
> 반려 유형 선택을 **`required` 로 만드는 것**이 이 Task 의 핵심이다.

**Files:**
- Create: `src/main/webapp/WEB-INF/views/approval/detail.jsp`
- Create: `src/main/webapp/WEB-INF/views/approval/reject-modal.jsp`
- Create: `src/main/java/com/flowmate/common/web/GlobalExceptionHandler.java`

- [ ] **Step 1: 전역 예외 처리를 만든다**

권한 예외가 500 으로 보이면 데모에서 사고처럼 보인다. 설계서 §4.2 의 `@ControllerAdvice` 자리다.

```java
package com.flowmate.common.web;

import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.flowmate.common.exception.ApprovalAccessDeniedException;
import com.flowmate.common.exception.ApprovalNotFoundException;

/**
 * 업무 예외를 화면에 맞는 형태로 바꾼다.
 *
 * 권한 예외가 500 으로 보이면 데모에서 사고처럼 보인다.
 * IllegalStateException 도 처리하는 이유: 도메인 객체의 전이 거부가 그 타입이다.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApprovalNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(ApprovalNotFoundException e, Model model) {
        model.addAttribute("errorTitle", "문서를 찾을 수 없습니다");
        model.addAttribute("errorMessage", e.getMessage());
        return "error/business";
    }

    @ExceptionHandler(ApprovalAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(ApprovalAccessDeniedException e, Model model) {
        model.addAttribute("errorTitle", "권한이 없습니다");
        model.addAttribute("errorMessage", e.getMessage());
        return "error/business";
    }

    /** 도메인 객체가 거부한 상태 전이 */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public String handleIllegalState(IllegalStateException e, Model model) {
        model.addAttribute("errorTitle", "처리할 수 없는 상태입니다");
        model.addAttribute("errorMessage", e.getMessage());
        return "error/business";
    }

    /** 잘못된 입력 (반려 유형 누락 등) */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIllegalArgument(IllegalArgumentException e, Model model) {
        model.addAttribute("errorTitle", "입력을 확인해 주세요");
        model.addAttribute("errorMessage", e.getMessage());
        return "error/business";
    }
}
```

`src/main/webapp/WEB-INF/views/error/business.jsp`:

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="../common/head.jsp">
    <jsp:param name="pageTitle" value="오류"/>
</jsp:include>
<body>
<jsp:include page="../common/header.jsp"/>
<div class="layout">
    <jsp:include page="../common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title"><c:out value="${errorTitle}"/></h2>
        <p class="alert alert--error"><c:out value="${errorMessage}"/></p>
        <a class="btn btn--plain" href="${pageContext.request.contextPath}/approval/box">내 결재함으로</a>
    </main>
</div>
<jsp:include page="../common/footer.jsp"/>
</body>
</html>
```

- [ ] **Step 2: 반려 모달 조각을 만든다**

`src/main/webapp/WEB-INF/views/approval/reject-modal.jsp`:

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  반려 유형 선택.

  ★ select 에 required 를 붙이고 빈 option 을 기본값으로 둔다.
    유형 없는 반려를 허용하면 Phase 5 사전점검이 유형별 빈도를 집계할 수 없고,
    집계가 없으면 "과거 반려 3건에 근거함" 같은 숫자를 제시할 수 없다.
    Service 도 같은 검증을 하지만(화면을 우회할 수 있으므로) 화면에서도 막는다.

  필요 모델: doc, rejectReasons
--%>
<div class="reject-modal" id="rejectModal" hidden>
    <div class="reject-modal__panel">
        <h3 class="reject-modal__title">반려</h3>
        <form method="post" action="${pageContext.request.contextPath}/approval/${doc.approvalId}/reject">
            <jsp:include page="../common/csrf-input.jsp"/>

            <div class="form-row">
                <label class="form-label" for="reasonCategory">반려 유형</label>
                <select class="form-input" id="reasonCategory" name="reasonCategory" required>
                    <option value="">선택하세요</option>
                    <c:forEach items="${rejectReasons}" var="r">
                        <option value="${r}"><c:out value="${r}"/></option>
                    </c:forEach>
                </select>
            </div>

            <div class="form-row">
                <label class="form-label" for="reasonText">반려 사유</label>
                <textarea class="form-input" id="reasonText" name="reasonText" rows="4"
                          maxlength="500" placeholder="기안자가 무엇을 고쳐야 하는지 적어 주세요"></textarea>
            </div>

            <div class="form-row">
                <button class="btn btn--danger" type="submit">반려</button>
                <button class="btn btn--plain" type="button" id="rejectCancel">취소</button>
            </div>
        </form>
    </div>
</div>
```

> `<option>` 의 표시값이 코드 그대로다. 한글 이름을 쓰려면 `RejectReason.labelOf` 를 EL 에서 불러야 하는데
> 정적 메서드 호출은 JSP EL 에서 번거로우므로, **Controller 가 `Map<String,String>` 을 만들어 넘기는 편이 낫다.**
> Task 11 의 여유 작업으로 남긴다 — 지금은 코드가 보여도 기능은 완전하다.

`static/js/common.js` 에 모달 열기/닫기를 추가한다:

```javascript
    /* 반려 모달. hidden 속성만 토글한다 — CSS 는 마지막 Phase 에서 얹는다 */
    $(document).on('click', '#rejectOpen', function () {
        $('#rejectModal').prop('hidden', false);
    });
    $(document).on('click', '#rejectCancel', function () {
        $('#rejectModal').prop('hidden', true);
    });
```

- [ ] **Step 3: 상세 화면을 만든다**

`src/main/webapp/WEB-INF/views/approval/detail.jsp`:

```jsp
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
<!DOCTYPE html>
<html lang="ko">
<jsp:include page="../common/head.jsp">
    <jsp:param name="pageTitle" value="문서 상세"/>
</jsp:include>
<body>
<jsp:include page="../common/header.jsp"/>
<div class="layout">
    <jsp:include page="../common/sidebar.jsp"/>
    <main class="content">
        <h2 class="page-title"><c:out value="${doc.title}"/></h2>

        <table class="doc-detail">
            <tr>
                <th>문서번호</th><td><c:out value="${doc.docNo}"/></td>
                <th>유형</th><td><c:out value="${doc.docTypeLabel}"/></td>
            </tr>
            <tr>
                <th>기안자</th>
                <td><c:out value="${doc.drafterName}"/> · <c:out value="${doc.drafterPositionName}"/>
                    (<c:out value="${doc.deptName}"/>)</td>
                <th>금액</th><td class="doc-detail__amount">${doc.amount}</td>
            </tr>
            <tr>
                <th>상태</th>
                <td><span class="status status--${fn:toLowerCase(doc.status)}"><c:out value="${doc.status}"/></span></td>
                <th>기안일</th><td>${doc.draftedAt}</td>
            </tr>
        </table>

        <section class="doc-content">
            <h3 class="page-title">본문</h3>
            <pre class="doc-content__body"><c:out value="${doc.content}"/></pre>
        </section>

        <section class="approval-line-box">
            <h3 class="page-title">결재선</h3>
            <c:choose>
                <c:when test="${empty lines}">
                    <p class="alert alert--info">결재할 상위자가 없는 문서입니다.</p>
                </c:when>
                <c:otherwise>
                    <ul class="approval-line">
                        <c:forEach items="${lines}" var="line">
                            <li class="approval-line__item">
                                <span class="approval-line__step">${line.stepNo}</span>
                                <span class="approval-line__name"><c:out value="${line.approverName}"/></span>
                                <span class="approval-line__position"><c:out value="${line.approverPositionName}"/></span>
                                <span class="status status--${fn:toLowerCase(line.status)}">
                                    <c:out value="${line.status}"/>
                                </span>
                                <c:if test="${not empty line.comment}">
                                    <span class="approval-line__comment"><c:out value="${line.comment}"/></span>
                                </c:if>
                                <c:if test="${line.processedAt != null}">
                                    <span class="approval-line__at">${line.processedAt}</span>
                                </c:if>
                            </li>
                        </c:forEach>
                    </ul>
                </c:otherwise>
            </c:choose>
        </section>

        <section class="doc-actions">
            <%-- 버튼 표시 조건은 Service 가 계산해 넘긴 값만 쓴다. JSP 에 규칙을 흩지 않는다 --%>
            <c:if test="${myTurn}">
                <form class="doc-actions__approve" method="post"
                      action="${pageContext.request.contextPath}/approval/${doc.approvalId}/approve">
                    <jsp:include page="../common/csrf-input.jsp"/>
                    <div class="form-row">
                        <label class="form-label" for="comment">의견</label>
                        <input class="form-input" type="text" id="comment" name="comment" maxlength="500">
                        <button class="btn btn--primary" type="submit">승인</button>
                    </div>
                </form>
                <button class="btn btn--danger" type="button" id="rejectOpen">반려</button>
            </c:if>

            <c:if test="${doc.editable}">
                <a class="btn btn--plain"
                   href="${pageContext.request.contextPath}/approval/write?approvalId=${doc.approvalId}">수정</a>
            </c:if>

            <c:if test="${canCancel}">
                <form method="post"
                      action="${pageContext.request.contextPath}/approval/${doc.approvalId}/cancel">
                    <jsp:include page="../common/csrf-input.jsp"/>
                    <button class="btn btn--plain" type="submit">회수</button>
                </form>
            </c:if>

            <a class="btn btn--plain" href="${pageContext.request.contextPath}/approval/box">목록</a>
        </section>

        <section class="doc-history">
            <h3 class="page-title">이력</h3>
            <ul class="history-list">
                <c:forEach items="${histories}" var="h">
                    <li class="history-list__item">
                        <span class="history-list__at">${h.createdAt}</span>
                        <span class="history-list__actor"><c:out value="${h.actorName}"/></span>
                        <span class="history-list__action"><c:out value="${h.actionLabel}"/></span>
                        <c:if test="${not empty h.comment}">
                            <span class="history-list__comment"><c:out value="${h.comment}"/></span>
                        </c:if>
                    </li>
                </c:forEach>
            </ul>
        </section>

        <c:if test="${myTurn}">
            <jsp:include page="reject-modal.jsp"/>
        </c:if>
    </main>
</div>
<jsp:include page="../common/footer.jsp"/>
</body>
</html>
```

- [ ] **Step 4: ★ 전 과정을 브라우저에서 확인한다 — 설계서 §9 Day 8 완료 기준**

세 계정으로 쿠키 병을 따로 만들어 순서대로 진행한다.

1. **곽수빈(`2020003`)** — `/approval/write` 에서 지출결의 100만원 임시저장 → 결재선 2건 확인 → **상신**
2. **신동혁(`2016004`)** — `/approval/box` 대기 탭에 그 문서가 보임 → 상세 → **승인**
3. **박현주(`2016002`)** — 대기 탭에 보임 → 상세 → **승인** → 상태가 `APPROVED`
4. **곽수빈** — 기안 탭에서 상태가 완료로 보이고, 이력에 기안·상신·승인·승인 4건

반려 경로도 확인한다:

5. 곽수빈이 새 문서를 상신 → 신동혁이 **반려 유형을 선택하지 않고** 제출 시도 → 브라우저가 막는다(`required`)
6. 유형을 선택해 반려 → 문서가 `REJECTED`, 2단계가 `SKIPPED`
7. DB 확인: `SELECT reason_category FROM approval_reject_history ORDER BY id DESC LIMIT 1`

권한도 확인한다:

8. 곽수빈이 다른 사람 문서 URL 을 직접 열면 403 화면 (`/approval/2` 를 조직 무관 계정으로)
9. 신동혁이 자기 차례가 아닌 문서에서 승인 POST 를 보내면 403

- [ ] **Step 5: `style.css` 목록에 추가하고 커밋한다**

```
 * 문서상세   .doc-detail  .doc-detail__amount  .doc-content  .doc-content__body
 * 결재선박스 .approval-line-box  .approval-line__comment  .approval-line__at
 * 액션       .doc-actions  .doc-actions__approve
 * 이력       .doc-history  .history-list  .history-list__item  .history-list__at
 *            .history-list__actor  .history-list__action  .history-list__comment
 * 반려모달   .reject-modal  .reject-modal__panel  .reject-modal__title
 * 버튼추가   .btn--danger
 * 오류       (error/business.jsp 는 기존 .alert--error 재사용)
```

커밋 메시지:

```
feat: 문서 상세와 반려 유형 선택 화면

반려 유형 select 에 required 를 붙이고 기본값을 빈 값으로 둔다. 유형 없는 반려를
허용하면 Phase 5 사전점검이 유형별 빈도를 집계할 수 없고, 집계가 없으면
과거 반려 건수를 근거로 제시할 수 없다. Service 도 같은 검증을 한다 - 화면은 우회 가능하다.

버튼 표시 조건을 Service 가 계산해 넘긴다. JSP 에 상태 판정식을 흩으면
화면마다 규칙이 어긋나고, 도메인 객체의 cancel 조건과 이중으로 관리된다.

권한 예외를 ControllerAdvice 로 받아 403 화면으로 바꾼다. 500 으로 보이면
데모에서 사고처럼 보인다. 도메인 객체의 전이 거부는 IllegalStateException 이라
409 로 매핑했다.
```

---

## Task 10: 첨부파일 업로드 (잘라내기 1순위)

> 설계서 §9 는 Phase 2 초과 시 **가장 먼저 잘라낼 항목**으로 이것을 지목했다.
> 앞의 9개 Task 가 끝나고 시간이 남을 때만 한다. **잘라내도 Phase 2 의 완료 기준은 충족된다.**

**Files:**
- Modify: `.gitignore`
- Modify: `src/main/resources/application.yml`
- Create: `src/main/java/com/flowmate/approval/domain/ApprovalAttachment.java`
- Create: `src/main/java/com/flowmate/approval/mapper/ApprovalAttachmentMapper.java` + XML
- Create: `src/main/java/com/flowmate/approval/service/AttachmentStorage.java`
- Create: `src/main/java/com/flowmate/approval/controller/AttachmentController.java`
- Modify: `src/main/webapp/WEB-INF/views/approval/write.jsp`, `detail.jsp`

- [ ] **Step 1: ★ 먼저 `.gitignore` 에 업로드 경로를 넣는다**

**이 Step 을 뒤로 미루지 않는다.** 업로드된 파일이 한 번 커밋되면 지워도 이력에 남고,
저장소는 Phase 6 이후 public 이 된다 (로드맵 Q6).

`.gitignore` 의 `# 로그` 절 앞에 추가한다:

```gitignore
# 업로드 파일 (flowmate.upload.base-dir 기본값)
/upload/
```

- [ ] **Step 2: 설정을 추가한다**

`application.yml` 의 `flowmate:` 블록에 추가한다:

```yaml
flowmate:
  approval:
    line-policy: default
  upload:
    # 첨부파일 저장 위치. 저장 경로는 {base-dir}/approval/{yyyy}/{MM}/{UUID}.{ext}
    # .gitignore 의 /upload/ 와 짝이다 — 기본값을 바꾸면 그쪽도 함께 바꾼다.
    base-dir: ./upload
    max-file-size: 10MB
```

`spring:` 블록에 멀티파트 상한을 추가한다. 설정하지 않으면 기본 1MB 에서 조용히 막힌다.

```yaml
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 20MB
```

- [ ] **Step 3: 저장 서비스를 만든다**

```java
package com.flowmate.approval.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.flowmate.common.exception.FlowMateException;

/**
 * 첨부파일 디스크 저장.
 *
 * 원본 파일명을 디스크에 쓰지 않고 UUID 로 저장하는 이유 세 가지:
 *   1. 경로 조작 — 원본명에 ../ 가 들어오면 임의 위치에 쓸 수 있다
 *   2. 중복 — 같은 이름을 올리면 덮어써진다
 *   3. 한글·특수문자 파일명이 OS·파일시스템마다 다르게 처리된다
 *
 * 원본명은 approval_attachment.file_name 에만 두고 다운로드 시 헤더로 되돌린다.
 */
@Service
public class AttachmentStorage {

    private final Path baseDir;

    public AttachmentStorage(@Value("${flowmate.upload.base-dir}") String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    }

    /**
     * 파일을 저장하고 baseDir 기준 상대 경로를 돌려준다.
     * DB 에는 상대 경로를 넣는다 — 배포 환경마다 baseDir 이 달라지기 때문이다.
     */
    public String store(MultipartFile file) {
        String original = file.getOriginalFilename();
        String ext = extensionOf(original);
        LocalDate today = LocalDate.now();
        Path dir = baseDir.resolve(String.format("approval/%d/%02d",
                today.getYear(), today.getMonthValue()));
        String stored = UUID.randomUUID() + ext;
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dir.resolve(stored));
            }
        } catch (IOException e) {
            throw new FlowMateException("파일을 저장할 수 없습니다: " + original);
        }
        return baseDir.relativize(dir.resolve(stored)).toString().replace('\\', '/');
    }

    /**
     * 상대 경로로 실제 파일 경로를 돌려준다.
     *
     * normalize 후 baseDir 밖을 가리키면 거부한다 — DB 값이 오염되었을 때의 방어다.
     */
    public Path resolve(String relativePath) {
        Path target = baseDir.resolve(relativePath).normalize();
        if (!target.startsWith(baseDir)) {
            throw new FlowMateException("허용되지 않은 경로입니다");
        }
        return target;
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot).toLowerCase();
    }
}
```

- [ ] **Step 4: 도메인·매퍼·컨트롤러를 만든다**

`ApprovalAttachment` — 필드 `attachId`, `approvalId`, `fileName`, `filePath`, `fileSize`, `uploadedAt`.
`implements Serializable` + `serialVersionUID`. getter/setter.

매퍼 — `void insert(ApprovalAttachment a);`, `List<ApprovalAttachment> findByApprovalId(@Param("approvalId") Long id);`,
`ApprovalAttachment findById(@Param("attachId") Long attachId);`, `void delete(@Param("attachId") Long attachId);`

컨트롤러 — `POST /approval/{approvalId}/attach` (업로드, 임시저장 상태 + 기안자만),
`GET /approval/attach/{attachId}` (다운로드, 문서 조회 권한 재사용),
`POST /approval/attach/{attachId}/delete` (삭제, 임시저장 상태 + 기안자만).

다운로드 시 한글 파일명 처리:

```java
        String encoded = URLEncoder.encode(attachment.getFileName(), StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encoded)
                .contentLength(attachment.getFileSize())
                .body(new FileSystemResource(storage.resolve(attachment.getFilePath())));
```

> `filename*=UTF-8''` 형식을 쓰는 이유: 구형 `filename="..."` 는 한글이 브라우저마다 깨진다.
> `+` 를 `%20` 으로 바꾸는 것은 `URLEncoder` 가 공백을 `+` 로 만들지만
> HTTP 헤더에서는 `%20` 이어야 하기 때문이다.

- [ ] **Step 5: 화면에 첨부 영역을 붙이고 확인한다**

`write.jsp` 에 임시저장 상태일 때만 보이는 업로드 폼(`enctype="multipart/form-data"`),
`detail.jsp` 에 첨부 목록과 다운로드 링크를 추가한다. `style.css` 목록에
`.attach-list`, `.attach-list__item`, `.attach-list__link`, `.attach-list__size` 를 적는다.

확인 사항: 한글 파일명 업로드 → 다운로드 시 원본 이름이 그대로 나오는지,
디스크에는 UUID 로 저장되었는지, `/upload/` 가 `git status` 에 나타나지 않는지.

- [ ] **Step 6: 커밋한다**

```
feat: 첨부파일 업로드와 다운로드

원본 파일명을 디스크에 쓰지 않고 UUID 로 저장한다. 원본명에 ../ 가 들어오면
임의 위치에 쓸 수 있고, 같은 이름을 올리면 덮어써지고, 한글 파일명은 OS 마다
다르게 처리된다. 원본명은 DB 에만 두고 다운로드 헤더로 되돌린다.

DB 에는 baseDir 기준 상대 경로를 넣는다. 배포 환경마다 절대 경로가 달라진다.
resolve 시 normalize 후 baseDir 밖을 가리키면 거부해 DB 값 오염에 대비한다.

.gitignore 에 /upload/ 를 먼저 넣었다. 업로드 파일이 한 번 커밋되면 지워도
이력에 남고 저장소는 Phase 6 이후 public 이 된다.

멀티파트 상한을 명시했다. 설정하지 않으면 기본 1MB 에서 조용히 막힌다.
```

---

## Task 11: Phase 2 마감

**Files:**
- Modify: `src/main/webapp/static/css/style.css` (상태 색만)
- Modify: `README.md`
- Modify: `docs/superpowers/plans/2026-08-05-flowmate-roadmap.md`

- [ ] **Step 1: 클린 검증**

```powershell
docker compose ps
.\mvnw.cmd clean verify
```

Surefire · Failsafe 실제 숫자를 기록한다. 목표는 **단위 50 이상 · 통합 40 이상**.
설계서 §10 의 "단위 40건 이상 · 통합 3건 이상"을 크게 넘긴다.

`mvnw test` 가 **DB 없이** 통과하는지도 확인한다 — 이 경계가 이 프로젝트의 규약이다.

- [ ] **Step 2: 상태 색을 CSS 에 얹는다**

Task 6·8·9 에서 목록에 적어 둔 `.status--*` 만 채운다. **JSP 는 열지 않는다.**
열어야 한다면 명명 규칙이 지켜지지 않았다는 신호이므로 그 사실을 기록한다.

```css
/* 상태 배지 — 의미별 색만 얹는다. 나머지 형태는 Phase 6 에서 정리한다 */
.status { display: inline-block; padding: 1px 6px; border-radius: 3px; font-size: 12px; }
.status--draft,   .status--waiting  { background: #eceff1; color: #455a64; }
.status--pending, .status--current  { background: #e3f2fd; color: #1565c0; }
.status--approved                   { background: #e8f5e9; color: #2e7d32; }
.status--rejected                   { background: #ffebee; color: #c62828; }
.status--canceled, .status--skipped { background: #f5f5f5; color: #9e9e9e; }
.btn--danger { background: #c62828; color: #fff; border-color: #c62828; }
```

- [ ] **Step 3: 클래스 목록을 실제와 대조한다**

Phase 1 Task 13 과 같은 감사다. JSP 에서 실제로 쓰이는 클래스를 추출해 목록과 맞춘다.

```powershell
Select-String -Path src\main\webapp\WEB-INF\views\*.jsp, src\main\webapp\WEB-INF\views\**\*.jsp -Pattern 'class="([^"]+)"' -AllMatches |
    ForEach-Object { $_.Matches } |
    ForEach-Object { $_.Groups[1].Value -split '\s+' } |
    Where-Object { $_ -notmatch '^\$' -and $_ -ne '' } |
    Sort-Object -Unique
```

목록에 없는 클래스를 추가하고, 쓰이지 않는 항목을 지운다.
**시각 기반 이름(`.blue-*`, `.big-*`, `.red`)이 하나라도 있으면 보고한다** — 이름을 바꾸려면 JSP 를 열어야 하므로
그 자체가 명명 규칙 위반의 증거다.

- [ ] **Step 4: README 를 갱신한다**

`## 구현 현황` 에서 Phase 2 를 체크하고, `## 테스트` 의 건수를 실제 값으로 바꾼다.
`## 데모 시나리오` 절을 새로 추가한다:

```markdown
## 데모 시나리오

1. `2020003` 곽수빈 로그인 → 기안 작성 → 지출결의 100만원 → 임시저장
   → **결재선이 자동 생성된다** (신동혁 과장 → 박현주 부장) → 상신
2. `2016004` 신동혁 로그인 → 내 결재함 **대기** 탭에 문서가 보임 → 승인
3. `2016002` 박현주 로그인 → 대기 탭 → 승인 → **완료**
4. 곽수빈으로 돌아가 이력 4건(기안·상신·승인·승인) 확인

**커스터마이징 시연:** 금액을 500만원으로 바꿔 저장하면 결재선에 이사가 추가된다.
`application.yml` 의 `flowmate.approval.line-policy` 를 `simple-two-step` 으로 바꾸고
재기동하면 같은 금액에서도 결재선이 팀장 1명으로 줄어든다.

**반려 시연:** 신동혁이 반려할 때 유형 선택이 필수다. 선택한 유형은
`approval_reject_history` 에 쌓이고 Phase 5 의 AI 사전점검이 이 표를 읽는다.
```

- [ ] **Step 5: 로드맵을 갱신한다**

- §6 진행 상황에서 계획서 2 를 **완료**로 바꾼다
- §2.0 이월 항목 중 이 Phase 에서 처리한 것(C2 · C5)을 해소 표시하고, C1 은 남긴다
- Q5(반려 유형 6종)·Q6(업로드 경로)를 확정 처리한다
- Phase 2 리뷰에서 나온 이월 항목을 §2.0 에 추가한다 (계획서 3·4 가 읽는다)

- [ ] **Step 6: 머지하고 태그를 붙인다**

```powershell
git switch main
git merge --no-ff feat/phase-2-approval-core -m "merge: Phase 2 전자결재 코어 완료 - 상태 기계, 결재선 정책 2종, 승인/반려"
git tag -a phase-2-approval-core -m "Phase 2: 결재 상태 기계, 결재선 정책 교체, 승인/반려/회수, 내 결재함, 문서 상세"
git push origin main --follow-tags
git branch -d feat/phase-2-approval-core
git push origin --delete feat/phase-2-approval-core
```

- [ ] **Step 7: 완료 기준을 확인한다**

설계서 §9 Phase 2 · §10 대비:

- [ ] 상태 전이 단위 테스트 통과 (목표 8건 → 실제 17건)
- [ ] 문서를 임시저장하면 결재선이 자동 생성된다
- [ ] 승인·반려가 되고 `approval_reject_history` 에 유형이 저장된다
- [ ] 사원A 기안 → 팀장 승인 → 부장 승인 → 완료가 화면에서 전부 된다
- [ ] 반려 시 반려 유형이 저장되고 이력에 표시된다
- [ ] 결재선 정책 2종이 같은 입력에 다른 결과를 낸다 (설정으로 교체 가능)
- [ ] 단위 테스트 40건 이상 (설계서 §10)
- [ ] 첨부파일 업로드·다운로드 (잘라냈으면 그 사실을 README 와 로드맵에 기록)

---

## 다음 단계

계획서 3(Phase 3 AI 게이트웨이)을 작성한다. 설계서 §9.1 은 Phase 3 을 Phase 2 와
**병행하는 것이 전제조건**이라고 했으나, Phase 2 를 먼저 끝냈으므로 이제 순서대로 진행한다.

착수 전에 확인할 것:

- 로드맵 §5 Q4 — Anthropic API 키 보관 방식. `application-local.yml` 은 이미 `.gitignore` 에 있다
- Phase 3 은 **화면이 없다.** `FakeLlmClient` 로 마스킹·캐싱·폴백을 검증하는 순수 로직 Phase 다
- 로드맵 §2.0 C3 — `fetch()` 를 쓰면 CSRF 헤더가 붙지 않는다. Phase 5 의 AJAX 설계에 영향

## 부록 — 설계서 대응 확인

| 설계서 항목 | Task |
|---|---|
| §5.2 결재 스키마 5종 | 1 |
| §5.2 `reason_category` 6종 · 비정규화 근거 | 1, 5, 7 |
| §6.2 상태 기계 + 단위 테스트 | 2 |
| §6.2 `ApprovalLinePolicy` + 구현 2종 | 3, 4 |
| §6.2 반려 화면 재설계 (유형 필수) | 9 |
| §6.2 내 결재함 (대기/진행/완료/반려) | 8 |
| §6.3 `approve()` 의 근태 반영 훅 자리 | 7 (주석만 — Phase 4 에서 구현) |
| §7 커스터마이징 지점 1 (설정 교체) | 4 |
| §4.3 계층 규칙 · 모듈 간 Service 경유 | 5, 6 |
| §8 테스트 전략 (단위/통합 분리) | 전체 |
| §9 Phase 2 완료 기준 | 11 |
| §9 잘라내기 순서 1순위 (첨부파일) | 10 |
| §10 단위 40건 이상 | 4 에서 초과 달성 |
