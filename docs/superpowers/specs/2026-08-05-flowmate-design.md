# FlowMate — AI 사전점검 그룹웨어 설계서

- 작성일: 2026-08-05
- 상태: 설계 확정 (구현 전)
- 한 줄 정의: **AI가 결재 반려를 미리 막아주는 사내 그룹웨어**
- 저장소 `flowmate` · 패키지 루트 `com.flowmate` · 부제 "전자결재 · 근태관리"

---

## 1. 개요

### 1.1 배경

그룹웨어 솔루션 회사의 웹솔루션 개발·커스터마이징 직무를 목표로 하는 포트폴리오다.
해당 직무의 실제 업무는 "밑바닥부터 새로 만드는 것"이 아니라 **완성된 그룹웨어 제품을 고객사별로 변형해 납품하는 것**이다.
따라서 이 프로젝트는 최신 기술 스택을 과시하는 대신, 다음 두 가지를 증명하는 데 목표를 둔다.

1. 해당 직무가 실제로 다루는 스택(JSP + jQuery + Spring MVC + MyBatis + WAR + Linux)을 그대로 다룰 수 있다.
2. 그 위에 새로운 가치(LLM)를 **상용 솔루션 수준의 안전장치와 함께** 얹을 수 있다.

### 1.2 목표

| # | 목표 | 증명 방법 |
|---|---|---|
| G1 | 그룹웨어 핵심 도메인을 이해한다 | 전자결재의 상태 기계·결재선·이력을 직접 구현 |
| G2 | 모듈을 만드는 게 아니라 이을 수 있다 | 연차 신청 → 결재 승인 → 근태 자동 반영을 하나의 트랜잭션으로 |
| G3 | 커스터마이징 가능한 구조를 설계한다 | 정책 3종을 인터페이스로 분리하고 구현체를 교체 가능하게 |
| G4 | LLM을 기업 환경에 안전하게 얹는다 | 민감정보 마스킹 · 캐싱 · 폴백 · 기능 플래그 |
| G5 | AI 기능의 품질을 검증할 수 있다 | 고정 평가셋 5건으로 사전점검 정확도 측정 |

### 1.3 비목표 (명시적으로 하지 않는 것)

- 그룹웨어 전 모듈 구현 (게시판·메일·일정·문서관리·자원예약 등)
- 상용 수준의 UI 완성도
- 실제 다중 테넌시 / 대규모 성능 최적화
- 벡터 DB · 임베딩 검색 · RAG 파이프라인
- 첨부파일(PDF/Excel) 내용 파싱

---

## 2. 범위

### 2.1 포함

```
[토대] 조직·사용자
   ├─ 부서(계층) / 직급 / 사원
   ├─ 로그인 · 세션 · 권한
   └─ 조직도 트리 조회 (재귀 쿼리)
          │
          ├──────────────────┬──────────────────┐
          ▼                  ▼                  ▼
[모듈 1] 전자결재       [모듈 2] 근태관리      [계층] AI
   ├─ 기안 · 임시저장      ├─ 출퇴근 기록        ├─ 게이트웨이
   ├─ 결재선 (정책 교체)   ├─ 근무시간 · 지각     │   (마스킹·캐싱·폴백)
   ├─ 상신 · 승인 · 반려   ├─ 연차 잔여 관리      ├─ 문서 요약
   ├─ 반려 유형 수집       └─ 부서 월간 현황      ├─ 상신 전 사전점검 ★
   ├─ 결재 이력                    ▲             └─ 연차 맥락 정보
   ├─ 내 결재함                    │
   └─ 첨부파일                     │
          │                        │
          └────────────────────────┘
        연차 신청서 승인 → 근태 자동 반영
                (프로젝트의 척추)
```

### 2.2 제외

| 제외 항목 | 이유 |
|---|---|
| 첨부파일 내용 파싱 | pdfbox/poi + 표 구조 인식 = 별도 프로젝트급 |
| 프롬프트 DB 관리 화면 | 파일 + `PromptRepository` 인터페이스로 확장 경로만 확보 |
| AI 비용 대시보드 화면 | 토큰 수는 DB에 기록, 조회 화면은 후순위 |
| 벡터 DB / 임베딩 | SQL 필터 + 프롬프트로 충분. 인프라 추가는 과잉 |
| 자연어 문서 검색 | 구조화 출력 + 동적 쿼리 조립 = 범위 초과 |
| 결재선 AI 추천 | 정책 인터페이스 구조로만 시연 |
| 스트리밍 응답 | 요약이 짧아 체감 이득 적음 |
| 유연근무 · 교대근무 · 시간외수당 | 근태 범위 폭발 방지 |
| 공휴일 자동 연동 | `holiday` 테이블 수동 관리 |

---

## 3. 기술 스택

| 영역 | 선택 | 선택 이유 |
|---|---|---|
| 언어 | Java 17 (LTS) | Spring Boot 3 요구. 단 `record`·텍스트블록 등 Java 8에 없는 문법은 최소 사용 (회사 환경이 8/11일 가능성) |
| 프레임워크 | Spring Boot 3.2.x | 개발 편의는 Boot, 산출물은 전통적 WAR |
| 화면 | **JSP + JSTL + jQuery 3.x** | 공고 요구사항. React/Vue를 쓰면 공고와 어긋남 |
| 패키징 | **WAR** | 외부 Tomcat 배포 = 회사 환경과 동일한 산출물 |
| 영속 | **MyBatis 3** | 한국 실무 표준. SQL을 직접 통제 → 튜닝·복잡 조회에 유리 |
| DB | PostgreSQL 16 (Docker) | 무료 + 표준 SQL. Oracle 문법 대응표를 별도 문서로 유지 |
| 인증 | Spring Security 6 (최소 구성) | "Spring 기반" 증명. formLogin + `UserDetailsService` + BCrypt |
| 빌드 | **Maven** | 공고 요구사항 |
| SCM | **Git** | 공고 요구사항. 기능별 브랜치 + 의미 있는 커밋 단위 |
| 배포 | Docker (Tomcat 10.1 + PostgreSQL) | Linux 환경에서 WAR 배포 경험 |
| LLM | Anthropic Java SDK (`com.anthropic:anthropic-java`) | 공식 SDK. 모델 `claude-opus-5` |
| 테스트 | JUnit 5 + AssertJ + Mockito | 순수 로직 단위 테스트 중심 |

### 3.1 Spring Boot 3 + JSP 의 알려진 마찰 (Day 1에 반드시 검증)

Spring Boot 3은 Jakarta EE 네임스페이스를 쓰므로 JSP/JSTL 설정이 Boot 2 시절과 다르다.
**입문자가 여기서 반나절~하루를 잃는 지점이므로 Day 1의 유일한 목표로 둔다.**

확인할 것:

- `packaging`을 `war`로 설정
- `tomcat-embed-jasper` 의존성 추가 (scope `provided`)
- JSTL은 **Jakarta 버전** 필요 (`jakarta.servlet.jsp.jstl-api` + 구현체)
- taglib URI가 변경됨: `http://java.sun.com/jsp/jstl/core` → **`jakarta.tags.core`**
  (기존 URI를 쓰면 태그가 그대로 문자열로 출력되거나 500 발생)
- JSP는 실행 가능 JAR에서 동작하지 않음 → WAR 필수
- JSP 위치: `src/main/webapp/WEB-INF/views/`
- `application.yml`:
  ```yaml
  spring:
    mvc:
      view:
        prefix: /WEB-INF/views/
        suffix: .jsp
  ```

**탈출 조건**: 이 배선에 4시간 이상 소모되면 Spring Boot 2.7.18 + `javax` JSTL로 하향한다.
(Boot 2.7은 OSS 지원 종료 상태이므로 README에 하향 사유를 기록한다.)

---

## 4. 아키텍처

### 4.1 요청 흐름

```
[브라우저]
   │ jQuery (AJAX / DOM / 검증)
   ▼ HTTP
[Tomcat 10.1 on Linux(Docker)]
   └─[Spring Boot 3 (WAR)]
        └─[Spring Security] 인증 · 인가 필터
             └─[Controller]        HTTP 수신 · 파라미터 검증 · 화면 지정
                  └─[Service]      업무 규칙 · 트랜잭션 경계
                       ├─[Policy]   ★ 커스터마이징 지점 (인터페이스)
                       ├─[Mapper]   MyBatis → SQL
                       │     └─[PostgreSQL]
                       └─[LlmClient] ★ AI 게이트웨이 (데코레이터 체인)
                             └─[Anthropic API]
             └─[JSP]  결과를 HTML로 렌더링
   ▼
[브라우저에 HTML]
```

### 4.2 패키지 구조

```
com.flowmate
├─ common/
│   ├─ exception/       FlowMateException, ApprovalNotFoundException 등
│   ├─ web/             ApiResponse<T>, 전역 예외 처리(@ControllerAdvice)
│   └─ util/
├─ config/
│   ├─ SecurityConfig       Spring Security
│   ├─ MyBatisConfig
│   ├─ WebMvcConfig         뷰 리졸버, 인터셉터
│   └─ LlmConfig            ★ 데코레이터 체인 조립
├─ org/                     조직 · 사용자
│   ├─ controller/ service/ mapper/ domain/
├─ approval/                전자결재
│   ├─ controller/ service/ mapper/ domain/
│   └─ policy/              ★ ApprovalLinePolicy
├─ attendance/              근태
│   ├─ controller/ service/ mapper/ domain/
│   └─ policy/              ★ LeaveGrantPolicy, WorkTimePolicy
└─ ai/
    ├─ client/              LlmClient + 데코레이터 4종
    ├─ mask/                SensitiveDataMasker
    ├─ prompt/              PromptRepository (파일 구현)
    ├─ feature/             SummaryService, PreflightService, LeaveContextService
    ├─ domain/              요약/점검 결과 객체
    └─ controller/          AI REST API
```

### 4.3 계층 규칙 (위반하면 리뷰에서 반려)

| 규칙 | 이유 |
|---|---|
| Controller는 Service만 호출. Mapper 직접 호출 금지 | 업무 규칙이 Controller로 새는 것 방지 |
| Service는 `HttpServletRequest`·`HttpSession`을 알지 않는다 | Service를 테스트 가능하게 유지 |
| 필드는 `private`, 접근은 `getXxx()` | JSP EL(`${doc.title}`)·MyBatis·Jackson이 JavaBeans 규약에 의존 |
| 모듈 간 호출은 Service 인터페이스 경유 | `approval` → `attendance` 결합도 완화 |
| `ai` 패키지는 도메인을 참조할 수 있으나 역방향은 인터페이스로만 | AI를 껐을 때 도메인이 깨지지 않게 |
| 트랜잭션은 Service에서만 시작 | 경계를 한 곳으로 |

### 4.4 화면 공통화

`WEB-INF/views/common/` 아래에 재사용 조각을 두고 `<jsp:include>`로 조립한다.

```
common/
├─ head.jsp        메타 · CSS · jQuery
├─ header.jsp      상단 메뉴 · 로그인 정보
├─ sidebar.jsp     모듈 메뉴
├─ footer.jsp
├─ pagination.jsp  페이징 공통
└─ ai-panel.jsp    AI 결과 표시 영역 (요약/점검 공용)
```

화면마다 HTML을 복사하지 않는다. `ai-panel.jsp`를 공용으로 두는 것은 AI 기능이 늘어날 때 화면 작업량을 줄이기 위한 것이다.

#### 4.4.1 원칙 — 구조는 처음에, 외양은 마지막에

CSS 스타일링은 마지막(Phase 6)에 몰아서 한다. 그러나 **화면 구조를 마지막으로 미루는 것은 금지한다.**
JSP는 다음 세 가지 성질 때문에 마크업을 나중에 교체하는 비용이 매우 크다.

1. **마크업과 데이터 바인딩이 한 파일에 있다.** `${doc.title}`·`<c:forEach>`가 HTML 안에 있으므로 마크업을 교체하면 바인딩도 다시 배선해야 한다.
2. **jQuery가 DOM 구조에 직접 의존한다.** `$('#btnSummarize')`가 깨져도 **에러가 나지 않고 조용히 동작하지 않는다.** 화면 10개에 흩어지면 원인 추적에 하루가 든다.
3. **공통 조각을 늦게 만들면 이미 만든 화면 전부를 고쳐야 한다.** `header.jsp`를 Phase 6에 만들면 그때까지의 화면 10개에 상단 메뉴 HTML이 복붙되어 있다.

| 항목 | 시점 |
|---|---|
| 공통 레이아웃 조각 (`head`/`header`/`sidebar`/`footer`) | **Phase 1** |
| `id`/`class` 이름 규칙 확정 | **Phase 1** |
| 개별 화면 마크업 | 각 기능과 함께 (JSP는 분리 불가) |
| CSS 스타일링 (색·여백·폰트·테두리) | **Phase 6** |
| 반응형 · 다크모드 · 애니메이션 | **하지 않음** (사내 그룹웨어에 불필요) |

#### 4.4.2 클래스 명명 규칙 — CSS 미루기를 안전하게 만드는 조건

**의미 기반으로 붙이고 시각 기반은 금지한다.** 이것이 Phase 6에 CSS를 채우는 동안 JSP 파일을 한 줄도 열지 않게 하는 유일한 조건이다.

```html
<!-- ✗ 시각 기반 — CSS를 바꾸면 이름이 거짓이 되고 결국 HTML도 고치게 된다 -->
<div class="blue-box big-text">
<span class="red">반려</span>

<!-- ✓ 의미 기반 — CSS만 채우면 된다 -->
<div class="ai-panel">
<span class="status status--rejected">반려</span>
<table class="doc-list">
<ul class="approval-line">
```

| 용도 | 규칙 | 예 |
|---|---|---|
| 영역 | 명사 | `.doc-list` · `.ai-panel` · `.approval-line` |
| 상태 | `--` 접미 | `.status--pending` · `.status--rejected` |
| 버튼 | `.btn` + 역할 | `.btn .btn--primary` · `.btn .btn--danger` |
| 폼 | 고정 3종 | `.form-row` · `.form-label` · `.form-input` |

CSS는 `src/main/webapp/static/css/style.css` 한 파일로 유지한다. 부트스트랩 등 UI 프레임워크는 쓰지 않는다 —
그룹웨어는 사내 UI 라이브러리 위에서 커스터마이징되는 제품이므로, 공통 조각을 직접 만들어 재사용하는 연습이 더 정확하다.

---

## 5. 데이터 모델

PostgreSQL 기준. Oracle 대응은 §5.6.

### 5.1 조직 · 사용자

```sql
CREATE TABLE department (
    dept_id        BIGSERIAL PRIMARY KEY,
    parent_dept_id BIGINT      REFERENCES department(dept_id),
    dept_name      VARCHAR(100) NOT NULL,
    dept_code      VARCHAR(20)  NOT NULL UNIQUE,
    sort_order     INT          NOT NULL DEFAULT 0,
    use_yn         CHAR(1)      NOT NULL DEFAULT 'Y'
);

CREATE TABLE position (
    position_id    BIGSERIAL PRIMARY KEY,
    position_name  VARCHAR(50) NOT NULL,   -- 사원/대리/과장/차장/부장/이사
    position_level INT         NOT NULL    -- 1~6, 결재선 정책이 참조
);

CREATE TABLE employee (
    emp_id        BIGSERIAL PRIMARY KEY,
    emp_no        VARCHAR(20)  NOT NULL UNIQUE,
    emp_name      VARCHAR(50)  NOT NULL,
    dept_id       BIGINT       NOT NULL REFERENCES department(dept_id),
    position_id   BIGINT       NOT NULL REFERENCES position(position_id),
    email         VARCHAR(100),
    hire_date     DATE         NOT NULL,
    password_hash VARCHAR(100) NOT NULL,   -- BCrypt
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',  -- USER/MANAGER/ADMIN
    use_yn        CHAR(1)      NOT NULL DEFAULT 'Y'
);
```

### 5.2 전자결재

```sql
CREATE TABLE approval_doc (
    approval_id  BIGSERIAL PRIMARY KEY,
    doc_no       VARCHAR(30)  NOT NULL UNIQUE,  -- EXP-2026-0001
    doc_type     VARCHAR(20)  NOT NULL,  -- EXPENSE/PURCHASE/LEAVE/CONTRACT/GENERAL
    title        VARCHAR(200) NOT NULL,
    content      TEXT,
    drafter_id   BIGINT       NOT NULL REFERENCES employee(emp_id),
    dept_id      BIGINT       NOT NULL REFERENCES department(dept_id),
    amount       NUMERIC(15)  DEFAULT 0,        -- 리스크 통계·정책이 참조
    status       VARCHAR(20)  NOT NULL,  -- DRAFT/PENDING/APPROVED/REJECTED/CANCELED
    current_step INT          DEFAULT 0,
    drafted_at   TIMESTAMP    NOT NULL,
    submitted_at TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE TABLE approval_line (
    line_id      BIGSERIAL PRIMARY KEY,
    approval_id  BIGINT      NOT NULL REFERENCES approval_doc(approval_id),
    step_no      INT         NOT NULL,
    approver_id  BIGINT      NOT NULL REFERENCES employee(emp_id),
    line_type    VARCHAR(20) NOT NULL,  -- APPROVAL/AGREEMENT/REFERENCE
    status       VARCHAR(20) NOT NULL,  -- WAITING/CURRENT/APPROVED/REJECTED/SKIPPED
    comment      VARCHAR(500),
    processed_at TIMESTAMP,
    UNIQUE (approval_id, step_no)
);

CREATE TABLE approval_history (
    history_id  BIGSERIAL PRIMARY KEY,
    approval_id BIGINT      NOT NULL REFERENCES approval_doc(approval_id),
    actor_id    BIGINT      NOT NULL REFERENCES employee(emp_id),
    action      VARCHAR(20) NOT NULL,  -- DRAFT/SUBMIT/APPROVE/REJECT/CANCEL
    comment     VARCHAR(500),
    created_at  TIMESTAMP   NOT NULL
);

CREATE TABLE approval_attachment (
    attach_id   BIGSERIAL PRIMARY KEY,
    approval_id BIGINT       NOT NULL REFERENCES approval_doc(approval_id),
    file_name   VARCHAR(255) NOT NULL,
    file_path   VARCHAR(500) NOT NULL,
    file_size   BIGINT       NOT NULL,
    uploaded_at TIMESTAMP    NOT NULL
);

-- ★ AI 사전점검의 학습 원천. 이 테이블을 위해 반려 화면을 재설계한다.
CREATE TABLE approval_reject_history (
    id              BIGSERIAL PRIMARY KEY,
    approval_id     BIGINT       NOT NULL REFERENCES approval_doc(approval_id),
    doc_type        VARCHAR(20)  NOT NULL,
    dept_id         BIGINT       NOT NULL,
    rejector_id     BIGINT       NOT NULL,
    reason_category VARCHAR(30)  NOT NULL,
    reason_text     VARCHAR(500),
    rejected_at     TIMESTAMP    NOT NULL
);
CREATE INDEX idx_reject_type_dept ON approval_reject_history(doc_type, dept_id, rejected_at DESC);
```

`doc_type` / `dept_id`는 `approval_doc`에도 있으므로 중복이다. **의도한 비정규화**다 —
사전점검은 상신 버튼을 누를 때마다 실행되는 조회이므로, 매번 `approval_doc`과 조인하지 않고
이 테이블 + 인덱스만으로 끝내기 위한 것이다.

`reason_category` 값: `INSUFFICIENT_CONTENT`(문서 내용 불충분) / `EXCESSIVE_AMOUNT`(금액 과다·근거 부족) / `MISSING_EVIDENCE`(증빙 누락) / `PROCEDURE_ERROR`(결재 절차 오류) / `BUDGET_EXCEEDED`(예산 초과) / `OTHER`

### 5.3 연차 신청서 (결재 문서의 유형별 확장)

```sql
CREATE TABLE leave_request (
    approval_id BIGINT      PRIMARY KEY REFERENCES approval_doc(approval_id),
    leave_type  VARCHAR(20) NOT NULL,  -- ANNUAL/HALF_AM/HALF_PM/SICK
    start_date  DATE        NOT NULL,
    end_date    DATE        NOT NULL,
    days        NUMERIC(3,1) NOT NULL, -- 0.5 단위
    reason      VARCHAR(500)
);
```

유형별 필드를 `approval_doc`에 다 넣지 않고 연차만 확장 테이블로 분리한다.
이유: 연차만 근태 모듈과 구조적으로 연동되므로 타입 안전한 컬럼이 필요하고, 나머지 유형은 `content` 텍스트로 충분하다.

### 5.4 근태

```sql
CREATE TABLE attendance (
    att_id           BIGSERIAL PRIMARY KEY,
    emp_id           BIGINT      NOT NULL REFERENCES employee(emp_id),
    work_date        DATE        NOT NULL,
    check_in         TIMESTAMP,
    check_out        TIMESTAMP,
    work_minutes     INT         DEFAULT 0,
    overtime_minutes INT         DEFAULT 0,
    status           VARCHAR(20) NOT NULL,
    note             VARCHAR(200),
    UNIQUE (emp_id, work_date)
);
-- status: NORMAL/LATE/EARLY_LEAVE/ABSENT/LEAVE/HALF_LEAVE/HOLIDAY

CREATE TABLE leave_balance (
    emp_id         BIGINT       NOT NULL REFERENCES employee(emp_id),
    year           INT          NOT NULL,
    granted_days   NUMERIC(4,1) NOT NULL,
    used_days      NUMERIC(4,1) NOT NULL DEFAULT 0,
    updated_at     TIMESTAMP    NOT NULL,
    PRIMARY KEY (emp_id, year)
);
-- 잔여일수는 컬럼으로 두지 않고 (granted - used)로 계산한다 (불일치 방지)

CREATE TABLE leave_usage (
    usage_id    BIGSERIAL PRIMARY KEY,
    emp_id      BIGINT       NOT NULL REFERENCES employee(emp_id),
    approval_id BIGINT       NOT NULL REFERENCES approval_doc(approval_id),
    leave_type  VARCHAR(20)  NOT NULL,
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    days        NUMERIC(3,1) NOT NULL,
    applied_at  TIMESTAMP    NOT NULL,
    UNIQUE (approval_id)      -- 결재 1건당 1회만 반영 (중복 반영 방지)
);

CREATE TABLE holiday (
    holiday_date DATE         PRIMARY KEY,
    holiday_name VARCHAR(50)  NOT NULL
);
```

`leave_usage`의 `UNIQUE(approval_id)`는 **중복 반영 방지 장치**다. 승인 처리가 재시도될 경우 DB 제약이 최후 방어선이 된다.

### 5.5 AI

```sql
CREATE TABLE ai_result_cache (
    cache_key      VARCHAR(64) PRIMARY KEY,   -- SHA-256(feature:promptVer:input)
    feature        VARCHAR(30) NOT NULL,      -- SUMMARY/PREFLIGHT/LEAVE_CONTEXT
    prompt_version VARCHAR(20) NOT NULL,
    result_json    TEXT        NOT NULL,
    model          VARCHAR(50) NOT NULL,
    input_tokens   INT,
    output_tokens  INT,
    hit_count      INT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMP   NOT NULL
);

CREATE TABLE ai_call_log (
    log_id         BIGSERIAL PRIMARY KEY,
    feature        VARCHAR(30) NOT NULL,
    prompt_version VARCHAR(20) NOT NULL,
    emp_id         BIGINT,
    approval_id    BIGINT,
    input_tokens   INT,
    output_tokens  INT,
    latency_ms     INT,
    success_yn     CHAR(1)     NOT NULL,
    error_msg      VARCHAR(500),
    called_at      TIMESTAMP   NOT NULL
);

-- 경고를 무시하고 상신했는지 추적 → 훗날 기능 유효성 측정 근거
CREATE TABLE ai_preflight_result (
    result_id    BIGSERIAL PRIMARY KEY,
    approval_id  BIGINT      NOT NULL,
    verdict      VARCHAR(10) NOT NULL,   -- PASS/WARN
    findings_json TEXT,
    ignored_yn   CHAR(1)     NOT NULL DEFAULT 'N',
    checked_at   TIMESTAMP   NOT NULL
);
```

### 5.6 Oracle 대응 (별도 문서 `docs/oracle-mapping.md`로 유지)

| PostgreSQL | Oracle |
|---|---|
| `BIGSERIAL` | `NUMBER` + `SEQUENCE.NEXTVAL` |
| `WITH RECURSIVE` (조직도) | `CONNECT BY PRIOR` |
| `LIMIT n OFFSET m` | `ROWNUM` 서브쿼리 또는 `OFFSET .. FETCH NEXT` |
| `COALESCE` | `NVL` |
| `CURRENT_TIMESTAMP` | `SYSDATE` / `SYSTIMESTAMP` |
| `TEXT` | `CLOB` |
| `||` 문자열 결합 | 동일 |

**이 대응표를 유지하는 것이 이 프로젝트에서 "Oracle 경험"을 설명 가능하게 만드는 장치다.**

---

## 6. 모듈 설계

### 6.1 조직 · 사용자

**조직도 트리 조회** (재귀 CTE — 공고의 SQL 항목을 정면으로 증명)

```sql
WITH RECURSIVE dept_tree AS (
    SELECT dept_id, parent_dept_id, dept_name, 1 AS depth,
           CAST(dept_id AS VARCHAR(200)) AS path
      FROM department
     WHERE parent_dept_id IS NULL AND use_yn = 'Y'
    UNION ALL
    SELECT d.dept_id, d.parent_dept_id, d.dept_name, t.depth + 1,
           t.path || '>' || d.dept_id
      FROM department d
      JOIN dept_tree t ON d.parent_dept_id = t.dept_id
     WHERE d.use_yn = 'Y'
)
SELECT * FROM dept_tree ORDER BY path;
```

**인증** — Spring Security 최소 구성. `formLogin` + `UserDetailsService` 구현 + `BCryptPasswordEncoder`.
인가는 URL 패턴 기반(`/admin/**` → `ADMIN`) + 문서 접근은 Service에서 검증(내 결재선에 있는 문서만 조회 가능).

### 6.2 전자결재

**상태 기계** — 이 프로젝트에서 가장 중요한 도메인 로직.

```
      ┌────────┐  submit()   ┌─────────┐  approve()(최종)  ┌──────────┐
      │ DRAFT  │────────────▶│ PENDING │──────────────────▶│ APPROVED │
      └────────┘             └─────────┘                   └──────────┘
           │                   │     │
           │ cancel()          │     │ reject()      ┌──────────┐
           ▼                   │     └──────────────▶│ REJECTED │
      ┌──────────┐             │                     └──────────┘
      │ CANCELED │◀────────────┘ cancel() (기안자만, 첫 승인 전)
      └──────────┘
              approve()(중간) → PENDING 유지, current_step += 1
```

허용되지 않는 전이는 `IllegalStateException`으로 즉시 차단하고, 이를 **단위 테스트로 고정**한다.

```java
public class ApprovalDoc {
    public void submit() {
        if (!"DRAFT".equals(this.status)) {
            throw new IllegalStateException("임시저장 상태만 상신할 수 있습니다: " + this.status);
        }
        this.status = "PENDING";
        this.currentStep = 1;
    }
    // approve(), reject(), cancel() 동일 패턴
}
```

**결재선 정책** — ★ 커스터마이징 지점 1

```java
public interface ApprovalLinePolicy {
    List<ApprovalLine> determineLines(ApprovalDoc doc, Employee drafter);
}
```

| 구현체 | 규칙 |
|---|---|
| `DefaultApprovalLinePolicy` | 기안자 → 부서장 → (금액 300만 초과 시) 임원 |
| `SimpleTwoStepLinePolicy` | 기안자 → 부서장 (2단계 고정, 소규모 고객사용) |

두 구현체를 실제로 만들고 설정으로 교체 가능하게 한다. **"고객사마다 결재선이 다르다"를 코드로 증명하는 부분.**

**반려 화면** — `reason_category` 선택을 필수로 만든다. AI 사전점검의 학습 데이터가 여기서 나온다.

**내 결재함** — 대기 / 진행중 / 완료 / 반려 탭. 조회 쿼리는 `approval_line`과 조인하여 "내 차례인 문서"를 판정.

### 6.3 근태관리

**근무시간 정책** — ★ 커스터마이징 지점 2

```java
public interface WorkTimePolicy {
    WorkTimeResult evaluate(LocalDateTime checkIn, LocalDateTime checkOut, LocalDate date);
}
```
`DefaultWorkTimePolicy`: 09:00 시작, 18:00 종료, 소정 8시간, 09:01 이후 = 지각, 18:00 이전 = 조퇴, 초과분 = 연장근무.

**연차 부여 정책** — ★ 커스터마이징 지점 3

```java
public interface LeaveGrantPolicy {
    BigDecimal grantDays(Employee employee, int year);
}
```

| 구현체 | 규칙 |
|---|---|
| `FlatLeaveGrantPolicy` | 전원 15일 |
| `TenureBasedLeaveGrantPolicy` | 1년 미만 월 1일, 이후 15일 + 2년마다 1일 (최대 25일) |

**★ 결재 승인 → 근태 반영 (프로젝트의 척추)**

```java
@Service
public class ApprovalService {

    private final LeaveApplyService leaveApplyService;   // attendance 모듈의 인터페이스

    @Transactional
    public void approve(Long approvalId, Long approverId, String comment) {
        ApprovalDoc doc = repository.findById(approvalId)
            .orElseThrow(() -> new ApprovalNotFoundException(approvalId));

        doc.approve(approverId);              // 상태 전이 (도메인 규칙)
        historyMapper.insert(...);            // 이력 기록

        if (doc.isCompleted() && "LEAVE".equals(doc.getDocType())) {
            leaveApplyService.applyFromApproval(approvalId);   // ★ 근태 반영
        }
    }
}
```

**설계 판단: Spring 이벤트 대신 직접 호출을 선택한다.**
`ApplicationEvent`는 결합도가 낮아 보이지만 트랜잭션 경계가 불명확해져 "승인은 됐는데 연차가 안 깎임" 같은 부분 실패를 만들기 쉽다.
동일 트랜잭션 안에서 직접 호출하면 **어느 한쪽이 실패하면 전부 롤백**된다. 결합도는 `LeaveApplyService`를 인터페이스로 두어 완화한다.
이 판단 근거를 README에 기록한다.

**부서 월간 근태 현황** — 조직도 계층을 이용해 하위 부서까지 집계.

### 6.4 AI 계층

#### 6.4.1 게이트웨이 (데코레이터 체인)

```
ApprovalService ── LlmClient (인터페이스로만 의존)
                        │
      ┌─────────────────▼─────────────────┐
      │ CachingLlmClient                  │ 캐시 히트 → 즉시 반환
      │   └─ MaskingLlmClient             │ 민감정보 치환 → 위임
      │        └─ LoggingLlmClient        │ 호출 로그 기록
      │             └─ ResilientLlmClient │ 타임아웃 · 실패 시 empty
      │                  └─ ClaudeLlmClient│ 실제 API 호출
      └───────────────────────────────────┘
```

순서의 근거: 캐싱이 가장 바깥 → 히트 시 마스킹·API 비용 0. 마스킹이 실제 호출 직전 → 어떤 경로로 들어와도 원문이 외부로 나가지 않음.

```java
@Configuration
public class LlmConfig {
    @Bean
    public LlmClient llmClient(ClaudeLlmClient claude, SensitiveDataMasker masker,
                              AiCallLogMapper logMapper, AiResultCacheMapper cacheMapper) {
        LlmClient chain = new ResilientLlmClient(claude, Duration.ofSeconds(30));
        chain = new LoggingLlmClient(chain, logMapper);
        chain = new MaskingLlmClient(chain, masker);
        chain = new CachingLlmClient(chain, cacheMapper);
        return chain;
    }
}
```

#### 6.4.2 민감정보 마스킹

| 종류 | 패턴 | 토큰 |
|---|---|---|
| 주민등록번호 | `\d{6}-\d{7}` | `[[RRN_n]]` |
| 계좌번호 | `\d{2,6}-\d{2,6}-\d{2,8}` | `[[ACCT_n]]` |
| 휴대전화 | `01\d-\d{3,4}-\d{4}` | `[[PHONE_n]]` |
| 사업자등록번호 | `\d{3}-\d{2}-\d{5}` | `[[BIZ_n]]` |
| 카드번호 | `\d{4}-\d{4}-\d{4}-\d{4}` | `[[CARD_n]]` |
| 이메일 | 표준 패턴 | `[[EMAIL_n]]` |

**정책 결정 1 — 기본적으로 복원하지 않는다.**
복원 기능은 구현하되 요약·점검 기능에서는 끈다. 요약문에 계좌번호가 필요 없고, 복원하면 마스킹의 목적이 절반 사라진다.

**정책 결정 2 — 오탐은 허용, 미탐은 불허.**
계좌번호 패턴은 넓어 문서번호를 오인할 수 있다. 오탐은 요약 품질이 약간 떨어지는 문제이지만 미탐은 개인정보 유출 사고다. 이 비대칭을 의도적으로 선택한다.

#### 6.4.3 캐싱 · 폴백 · 기능 플래그

`cache_key = SHA256(feature + ":" + promptVersion + ":" + input)`
→ `promptVersion`을 포함하는 이유: 프롬프트를 고쳤는데 캐시가 옛 결과를 돌려주는 함정 차단.

| 기능 | 캐시 |
|---|---|
| 문서 요약 | 무기한 (확정 문서는 불변) |
| 사전 점검 | 캐시 없음 (수정 후 재실행이 정상 동작) |
| 연차 맥락 | 1시간 (팀 부재 현황이 변함) |

**폴백 원칙**

> **AI 실패가 업무 실패가 되어서는 안 된다.** 로그인·기안·상신·승인·반려·근태 등록은 AI 계층이 완전히 죽어도 100% 동작해야 한다.

실패 시 예외를 던지지 않고 `Optional.empty()`를 반환하고, 화면은 "AI 기능을 일시적으로 사용할 수 없습니다"를 표시한다.

```yaml
ai:
  enabled: true
  model: claude-opus-5
  timeout-seconds: 30
  features:
    summary: true
    preflight: true
    leave-context: true
```

#### 6.4.4 프롬프트 관리

`src/main/resources/prompts/{feature}.v{n}.txt` 파일로 분리하고 `PromptRepository` 인터페이스로 조회한다.
코드에 문자열을 박지 않는 이유: git diff에 프롬프트 변경 이력이 남고, 나중에 DB 관리 화면으로 승격할 때 구현체만 교체하면 된다.

#### 6.4.5 기능 1 — 결재 문서 요약

`POST /api/ai/approvals/{id}/summary` · 구조화 출력(`output_config.format`)으로 JSON 스키마 강제.

```json
{
  "summary": ["3줄 이내, 각 줄 60자 이내"],
  "keyFacts": { "amount": "540,000원", "period": "2026-03-15 ~ 03-17", "counterparty": "○○물류" }
}
```

프롬프트 규칙: 본문에 없는 내용 생성 금지, 금액·날짜·거래처는 원문 그대로, 인사말·상투어 제외, 결재 판단에 필요한 정보 위주.

#### 6.4.6 기능 2 — 상신 전 사전 점검 ★

**흐름**

```
① 기안자 [상신] 클릭
② 서버:
   (a) 같은 doc_type + dept_id 의 최근 반려 이력 10건 조회 (없으면 전사로 확대)
   (b) reason_category 별 빈도 집계
   (c) [현재 문서 요지] + [과거 반려 패턴 + 빈도] 프롬프트 조립
   (d) 구조화 출력으로 findings 수신
③ 모달 표시
   · PASS → 바로 상신
   · WARN → 항목 목록 + [수정하러 가기] / [무시하고 상신]
④ '무시하고 상신' 시 ai_preflight_result.ignored_yn = 'Y' 기록
```

**출력 스키마**

```json
{
  "verdict": "WARN",
  "findings": [
    {
      "severity": "HIGH",
      "category": "목적불명확",
      "message": "본문에 출장 목적이 '업무협의'로만 기재되어 있습니다.",
      "suggestion": "방문 기관명과 협의 안건을 본문에 추가하세요.",
      "basedOnRejectCount": 3
    }
  ]
}
```

`basedOnRejectCount`가 설계의 핵심이다. **AI가 훈계하는 것이 아니라 "과거 반려 3건에 근거함"을 숫자로 제시**한다. 근거 없는 조언은 사용자가 두 번째부터 무시한다.

**프롬프트가 실패하는 지점과 대응**

| 문제 | 대응 |
|---|---|
| 뻔한 조언("더 자세히 쓰세요") | "제시된 과거 반려 사유와 직접 관련된 지점만 지적. 일반적 문서 작성 조언 금지." |
| 억지 지적 (정상 문서에서도 찾아냄) | "지적할 것이 없으면 findings를 빈 배열로 반환하라. 찾아내는 것이 목적이 아니다." |
| 추측 (본문에 없는 근거) | "본문에서 인용 가능한 근거가 있을 때만 지적하라." |
| 개인정보 유입 | 마스킹 계층 통과 + 반려 사유는 요약 형태로만 주입 |

**★ 고정 평가셋 5건** — 프롬프트 수정 시 매번 실행

| # | 입력 문서 | 기대 결과 |
|---|---|---|
| 1 | 목적이 '업무협의'뿐인 출장비 | 목적불명확 지적 (HIGH) |
| 2 | 첨부 없는 출장비 정산 | 증빙누락 지적 |
| 3 | 부서 평균 4배 금액의 구매 요청 | 금액과다 지적 |
| 4 | **잘 작성된 출장비** | **PASS (지적 없음)** |
| 5 | **잘 작성된 구매 요청** | **PASS (지적 없음)** |

4·5번이 가장 중요하다. 억지 지적을 잡아내는 유일한 장치이며, "AI 기능 품질을 어떻게 검증했나"에 대한 답이 된다.

#### 6.4.7 기능 3 — 연차 맥락 인식 결재 (2단계)

**3a. 데이터 결합 표시 (LLM 없음)** — 연차 신청서 결재 화면에 근태 데이터를 조합

```
┌─ 연차 신청 검토 정보 ─────────────────────────┐
│ 신청자  이00 (마케팅팀 · 대리)                 │
│ 신청일  2026-03-15(금) · 연차 1일              │
│ 연차 현황  부여 15 · 사용 8 · 잔여 7 · 53%     │
│ 해당일 팀 부재  2명 · 팀 가동률 60%            │
│ 최근 3개월  지각 2회 · 연장 18h · 결근 0회     │
└───────────────────────────────────────────────┘
```

이것만으로 "두 모듈을 통합했다"는 주장이 성립한다. LLM 불필요.

**3b. LLM 판단 코멘트**

```json
{ "riskLevel": "MEDIUM",
  "comment": "팀 가동률이 60%로 떨어지고 마감 직후입니다. 인수인계 확인을 권합니다.",
  "checkpoints": ["해당 주 마감 일정 확인", "인수인계 담당자 지정 여부"] }
```

**축소 가능 설계**: 일정이 밀리면 3a만 완성하고 3b를 포기한다. 의존 방향이 축소 순서를 강제한다.

---

## 7. 커스터마이징 지점 요약

공고의 "커스터마이징" 요구에 대한 답. **각 지점마다 구현체를 2개씩 만들어 교체를 실제로 시연한다.**

| # | 인터페이스 | 고객사별로 달라지는 것 | 구현체 |
|---|---|---|---|
| 1 | `ApprovalLinePolicy` | 결재 단계 수, 금액별 임원 결재 여부 | Default / SimpleTwoStep |
| 2 | `LeaveGrantPolicy` | 연차 부여 방식 | Flat(15일) / TenureBased(근속비례) |
| 3 | `WorkTimePolicy` | 근무 시작·종료, 지각 기준 | Default(09-18) / Flexible(코어타임) |
| 4 | `PromptRepository` | AI 프롬프트 문구 | File / (향후) Database |
| 5 | `ai.features.*` 플래그 | 어떤 AI 기능을 켤지 | 설정만으로 |

---

## 8. 테스트 전략

전체 커버리지 목표는 세우지 않는다(수치가 목적이 되면 의미 없는 테스트가 생긴다).
대신 **"핵심 업무 로직은 전부 테스트가 있다"**를 목표로 한다.

| 대상 | 방식 | 케이스 수(목표) |
|---|---|---|
| 민감정보 마스킹 | JUnit 단위 (순수 로직) | 12~15 |
| 결재 상태 전이 | JUnit 단위 (도메인 객체) | 8~10 |
| 결재선 정책 2종 | JUnit 단위 | 6 |
| 연차 부여 정책 2종 | JUnit 단위 | 6 |
| 근무시간 판정 | JUnit 단위 | 8 |
| 캐싱·폴백 동작 | `FakeLlmClient`로 단위 | 5 |
| **결재 승인 → 근태 반영** | `@SpringBootTest` 통합 (트랜잭션·롤백 검증) | 3 |
| AI 기능 배선 | `FakeLlmClient`로 통합 (API 호출 없음) | 3 |
| 사전점검 품질 | **수동 평가셋 5건** (자동화 대상 아님) | 5 |

**`FakeLlmClient`의 존재 이유**: LLM 호출 없이 캐싱·마스킹·폴백·화면 배선을 전부 검증할 수 있다.
6장의 인터페이스 설계가 테스트 가능성으로 회수되는 지점이다.

---

## 9. 단계별 계획

전체 **18 작업일** 기준 (하루 5~6시간). 각 Phase 종료 시 커밋 + 태그.

### Phase 0 — 환경 구축 (1.5일)

| Day | 작업 | 완료 기준 |
|---|---|---|
| 1 | JDK 17 / Maven / Docker 설치 확인. Spring Boot 3.2 WAR 프로젝트 생성. **JSP + Jakarta JSTL 배선** (§3.1). PostgreSQL 컨테이너 기동 | 브라우저에서 JSP 화면이 뜨고 JSTL 태그가 동작한다 |
| 1.5 | Git 저장소 · `.gitignore` · README 골격. MyBatis 배선(`SELECT 1`) | DB 값 하나를 JSP 화면에 출력한다 |

> **최대 리스크 구간.** §3.1의 탈출 조건을 지킨다.

### Phase 1 — 토대: 조직 · 사용자 (3.0일)

| Day | 작업 | 완료 기준 |
|---|---|---|
| 2 | `department` / `position` / `employee` 스키마 + 시드(부서 4, 직급 6, 사원 20) | 사원 목록이 화면에 뜬다 |
| 3 | Spring Security: `formLogin`, `UserDetailsService`, BCrypt, URL 인가 | 로그인/로그아웃이 되고 미인증 접근이 차단된다 |
| 4(0.5) | 조직도 트리 조회(재귀 CTE) + 조직도 화면 | 부서 계층이 트리로 렌더링된다 |
| 4.5(0.5) | **공통 레이아웃 조각 5종 + 클래스 명명 규칙 확정** (§4.4). 빈 `style.css` 생성. 사원 목록 화면을 이 구조로 재작성해 **템플릿으로 고정** | 이후 모든 화면이 복사할 수 있는 레이아웃 원본이 존재한다 |

### Phase 2 — 전자결재 코어 (4.5일)

| Day | 작업 | 완료 기준 |
|---|---|---|
| 4.5–5 | 결재 스키마 5종. `ApprovalDoc` 도메인 + **상태 전이 단위 테스트** | 상태 전이 테스트 8건 통과 |
| 6 | 기안 작성·임시저장 화면. `ApprovalLinePolicy` 인터페이스 + 구현 2종 + 테스트 | 문서를 임시저장하고 결재선이 자동 생성된다 |
| 7 | 상신 → 결재선 진행. 승인/반려 처리. **반려 유형 선택 화면** | 승인·반려가 되고 `approval_reject_history`에 유형이 저장된다 |
| 8 | 내 결재함(대기/진행/완료/반려), 문서 상세, 이력 표시 | 사원A 기안 → 팀장 승인 → 부장 승인 → 완료 전 과정이 화면에서 된다 |
| 9(0.5) | 첨부파일 업로드 | 파일 첨부·다운로드가 된다 |

> **가장 분량이 큰 Phase.** 초과 시 잘라내는 순서: 첨부파일 → 합의/참조 라인(결재만 유지) → 반려 탭.
>
> **Phase 종료 시 CSS에 30분을 쓴다.** 폰트·여백·테이블 줄무늬·버튼 정도만 잡아 "창피하지 않은 최소선"을 확보한다.
> 중간에 스크린샷이나 데모가 필요해질 때를 위한 보험이며, Phase 6의 본 작업이 이 위에 얹힌다.

### Phase 3 — AI 게이트웨이 (1일) · Phase 2와 병행 가능

| 작업 | 완료 기준 |
|---|---|
| `LlmClient` 인터페이스, `ClaudeLlmClient`, `SensitiveDataMasker` + **단위 테스트 12건**, 캐싱/폴백/로깅 데코레이터, `PromptRepository`(파일), 기능 플래그 | 마스킹 테스트 통과. `FakeLlmClient`로 체인 통합 테스트 통과 |

> Phase 2에서 막혔을 때 전환할 **피난처**로 쓴다. 도메인에 의존하지 않으므로 언제든 진행 가능하며, 순수 로직이라 Java·테스트 학습에 가장 좋다.

### Phase 4 — 근태 코어 + 연동 (3일)

| 단계 | 작업 | 분량 | 완료 기준 |
|---|---|---:|---|
| 4-1 | 근태 스키마 4종. 출퇴근 등록. `WorkTimePolicy` + 단위 테스트 | 1.0 | 출퇴근을 기록하면 근무시간·지각이 판정된다 |
| 4-2 | `LeaveGrantPolicy` + 테스트. 연차 잔여 관리. 개인/부서 근태 조회 화면 | 1.0 | 부서 월간 근태 현황이 조회된다 |
| 4-3 | **연차 신청서 → 결재 → 승인 시 근태 반영** (`@Transactional`) + **통합 테스트 3건** | 1.0 | 승인 시 잔여 연차가 줄고 해당일 근태가 '연차'로 바뀐다. 중간 실패 시 전부 롤백된다 |

### Phase 5 — AI 기능 (4.0일)

| 단계 | 작업 | 분량 | 완료 기준 |
|---|---|---:|---|
| 5-1 | 기능 1 문서 요약 + 캐시 동작 확인 | 0.5 | 같은 문서 2회 조회 시 2번째가 캐시에서 나온다 |
| 5-2 | **시드 데이터 생성** — 문서 200건 / 반려 40건(유형별 편중) / 근태 3개월 | 0.5 | 반려 사유가 유형별로 편중되어 쌓여 있다 |
| 5-3 | **기능 2 사전 점검** — 프롬프트 + 평가셋 5건 + 모달 화면 | 2.0 | 평가셋에서 문제 문서 3건 지적, 정상 문서 2건 통과 |
| 5-4 | 기능 3a 근태 데이터 결합 표시 | 0.5 | 연차 결재 화면에 근태 현황이 표시된다 |
| 5-5 | 기능 3b LLM 판단 코멘트 (최소 형태: `riskLevel` + 한 줄 `comment`) ← **버퍼 구간** | 0.5 | (밀리면 포기, 3a로 대체) |

> 원래 1.0일이었으나 Phase 6의 CSS 마감(+0.5)을 조달하기 위해 0.5일로 축소했다.
> `checkpoints` 배열은 제외하고 `riskLevel` + 한 줄 코멘트만 생성한다.
> 3b는 설계 시점부터 "밀리면 포기" 항목이므로 예산을 떼어내기에 가장 안전한 곳이다.

> 화려한 기능(기능 2)을 버퍼 앞에 배치한다. 반대로 하면 마감 시 B안을 선택한 이유가 사라진다.

### Phase 6 — 마감 (1.5일)

| 단계 | 작업 | 분량 | 완료 기준 |
|---|---|---:|---|
| 6-1 | **CSS 스타일링 마감** — `style.css` 한 파일만 채운다. JSP는 열지 않는다 (§4.4.2가 성립하는지의 검증도 된다) | 0.5 | 전체 화면의 톤이 통일된다 |
| 6-2 | Docker 배포: WAR → Tomcat 10.1 컨테이너 + PostgreSQL. `docker-compose up`으로 기동 | 0.5 | 컨테이너에서 전체 기능이 동작한다 |
| 6-3 | README(아키텍처 다이어그램, 실행법, **설계 판단 기록**), `docs/oracle-mapping.md`, 커밋 이력 정리, 데모 시나리오 스크립트 | 0.5 | 처음 보는 사람이 README만 읽고 실행하고 의도를 이해할 수 있다 |

> 6-1에서 **JSP 파일을 열어야 한다면 §4.4.2의 명명 규칙이 지켜지지 않았다는 신호**다.
> 그 경우 남은 화면은 손대지 말고 스타일이 적용된 화면만 데모 대상으로 삼는다.

### 9.1 일정 요약

Phase 3(AI 게이트웨이)은 도메인에 의존하지 않아 병행 가능하므로 두 경우를 함께 적는다.

| Phase | 내용 | 일수 | 누적(직렬) | 누적(3 병행) |
|---|---|---:|---:|---:|
| 0 | 환경 구축 | 1.5 | 1.5 | 1.5 |
| 1 | 조직 · 사용자 + **레이아웃 골격** | 3.0 | 4.5 | 4.5 |
| 2 | 전자결재 코어 | 4.5 | 9.0 | 9.0 |
| 3 | AI 게이트웨이 | 1.0 | 10.0 | (2와 병행) |
| 4 | 근태 + 연동 | 3.0 | 13.0 | 12.0 |
| 5 | AI 기능 | 4.0 | 17.0 | 16.0 |
| 6 | 마감 + **CSS** | 1.5 | **18.5** | 17.5 |
| — | 예비 | — | **−0.5 (초과)** | **+0.5** |

> **직렬 진행은 더 이상 성립하지 않는다.** 화면 작업(레이아웃 골격 +0.5, CSS 마감 +0.5)을 반영한 결과
> 직렬 총량이 18.5일로 0.5일 초과한다. **Phase 3(AI 게이트웨이)을 Phase 2와 병행하는 것이 선택이 아니라 전제조건이 되었다.**
>
> Phase 3은 도메인에 의존하지 않으므로 Phase 2에서 막힐 때마다 전환해 소화한다.
> 병행이 불가능해질 경우 잘라낼 순서: 기능 3b(0.5) → 첨부파일(0.5) → 합의/참조 라인.

### 9.2 Git 전략

- 브랜치: `main` + 기능별 `feat/approval-core`, `feat/ai-preflight` 등
- Phase 종료 시 `main`에 머지 + 태그 (`phase-2-approval-core`)
- 커밋 단위는 "동작하는 최소 변경". 커밋 메시지는 한국어 명령형 + 이유
- **커밋 로그 자체가 포트폴리오**임을 전제로 작성한다

---

## 10. 프로젝트 완료 기준 (Definition of Done)

기능:
- [ ] 사원A 기안 → 팀장 승인 → 부장 승인 → 완료가 화면에서 전부 동작
- [ ] 반려 시 반려 유형이 저장되고 이력에 표시
- [ ] 연차 신청서 승인 시 잔여 연차 감소 + 해당일 근태 '연차' 반영
- [ ] 승인 처리 중 실패 시 결재·근태가 함께 롤백
- [ ] 조직도 트리 렌더링
- [ ] 결재 문서 AI 요약 생성, 재조회 시 캐시 히트
- [ ] 상신 전 사전점검이 평가셋 5건에서 3 지적 / 2 통과
- [ ] 연차 결재 화면에 근태 맥락 정보 표시

안전성 (게이트웨이 계층의 존재 증명 — **데모에서 의도적으로 시연할 장면**):
- [ ] API 키를 잘못된 값으로 바꿔도 로그인·기안·상신·승인·반려·근태 등록이 전부 정상
- [ ] `ai.enabled: false`로 AI 기능이 화면에서 사라지고 나머지가 정상
- [ ] 주민번호·계좌번호·연락처가 API 요청 본문에 나타나지 않음 (로그로 검증)

품질:
- [ ] 단위 테스트 40건 이상 통과
- [ ] 통합 테스트 3건 이상 통과
- [ ] `docker-compose up` 한 번으로 전체 기동
- [ ] README에 아키텍처 다이어그램과 설계 판단 기록

---

## 11. 리스크와 대응

| # | 리스크 | 확률 | 영향 | 대응 |
|---|---|---|---|---|
| R1 | Phase 0 JSP/Jakarta 배선 지연 | 중 | 높음 | §3.1 탈출 조건 — 4시간 초과 시 Boot 2.7 하향 |
| R2 | Phase 2가 4.5일 초과 | **높음** | 높음 | 잘라내는 순서 사전 확정: 첨부파일 → 합의/참조 라인 → 반려 탭 |
| R3 | 사전점검 프롬프트 튜닝 지연 | 중 | 중 | 평가셋 5건으로 측정. 2일 초과 시 findings 항목 수를 3개로 제한해 단순화 |
| R4 | 기능 3b 미완 | 중 | 낮음 | **계획된 축소** — 3a만으로 통합 증명 |
| R5 | Java 학습 곡선 | **높음** | 중 | Phase 1~2 종료 시 코드 리뷰 요청. Phase 3을 피난처로 활용 |
| R6 | 시드 데이터 품질 저하 → 사전점검 결과가 어색 | 중 | 중 | 반려 사유를 유형별로 의도적 편중. 5건 검토 후 재생성 |
| R7 | LLM 비용 초과 | 낮음 | 낮음 | 캐싱 + 개발 중 `FakeLlmClient` 사용. 실호출은 검증 시점에만 |

---

## 12. 남은 결정 사항

| 항목 | 값 | 상태 |
|---|---|---|
| 프로젝트명 | **FlowMate** (저장소 `flowmate`, 패키지 `com.flowmate`) | **확정** (2026-08-05) |
| 반려 유형 카테고리 6종 | §5.2 정의대로 | Phase 2 Day 7 전 결정 |
| 문서 유형 5종 | EXPENSE/PURCHASE/LEAVE/CONTRACT/GENERAL | Phase 2 Day 4.5 전 결정 |
| 화면 디자인 | 부트스트랩 없음. `style.css` 한 파일. 구조는 Phase 1, CSS는 Phase 6 (§4.4) | **확정** (2026-08-05) |

### 12.1 명명 규칙 (확정)

| 위치 | 표기 |
|---|---|
| Git 저장소 | `flowmate` |
| Maven `artifactId` | `flowmate` |
| Maven `groupId` | `com.flowmate` |
| 패키지 루트 | `com.flowmate` (`com.example.*`는 사용하지 않는다 — 튜토리얼 흔적으로 읽힌다) |
| WAR 파일 | `flowmate.war` |
| README 제목 | `# FlowMate` + 부제 `### AI 사전점검 그룹웨어 — 전자결재 · 근태관리` |
| 이력서 표기 | **FlowMate** — AI 결재 사전점검 그룹웨어 |

---

## 부록 A. 개념과 학습 대응표

이 프로젝트의 각 구현이 공고 요구 기술과 어떻게 대응하는지.

| 공고 요구 | 대응 구현 |
|---|---|
| Java | 도메인 객체, 상태 기계, 정책 구현체, 마스킹 로직 |
| JSP | `WEB-INF/views/` 화면 + JSTL + 공통 조각 include |
| Javascript / jQuery | AJAX(요약·사전점검), 결재선 동적 편집, 입력 검증 |
| Maven | `pom.xml` 의존성 관리, WAR 패키징 |
| Eclipse | 표준 Maven WAR 구조 → Eclipse/STS에서 그대로 임포트 가능 |
| SCM (Git) | 기능별 브랜치, Phase 태그, 의미 있는 커밋 단위 |
| Spring framework | DI(정책·LlmClient 주입), MVC, `@Transactional`, Security |
| DBMS (Oracle) SQL | 재귀 CTE(조직도), 집계 쿼리(근태 현황), `docs/oracle-mapping.md` |
| Linux | Docker(Tomcat + PostgreSQL) WAR 배포, `catalina.out` 로그 확인 |
| 커스터마이징 | §7 커스터마이징 지점 5종 |
