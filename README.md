# FlowMate

**전자결재 · 근태관리 그룹웨어 — 과거 반려 이력에 근거해 AI가 상신 전에 점검합니다.**

`Java 17` · `Spring Boot 3.5 (WAR)` · `JSP + JSTL + jQuery` · `MyBatis 3` · `PostgreSQL 16` · `Spring Security 6` · `Docker`

---

## AI 사용

이 프로젝트는 **Claude(Claude Code)와 함께 만들었습니다.**

---

## 무엇을 하려는 프로젝트인가

회사에서 매일 쓰는 그룹웨어에서 반복되는 장면이 있었습니다. 결재를 올리고 며칠을
기다렸는데 반려되고, 사유를 열어 보면 아주 사소한 것들이었습니다.

- **실수로 증빙 자료를 업로드하지 못했다** — 문서는 다 썼는데 영수증 스캔 파일 하나가 빠졌다
- **출장 목적을 "업무협의"라고만 적었다** — 어디의 누구와 무슨 안건인지가 없다
- **사전 승인이 필요한 건인 줄 몰랐다** — 절차를 건너뛰고 바로 올렸다

전부 **올리기 전에 알았다면 30초면 고칠 수 있던 것들**입니다. 그리고 하나같이 같은 문서
유형에서 이미 여러 번 나왔던 이유입니다. 반려 이력은 시스템 안에 그대로 쌓여 있는데
아무도 그걸 읽지 않습니다. 그래서 이런 생각이 들었습니다.

> **"상신 버튼을 누르기 전에, 과거에 같은 부서·같은 유형에서 무엇 때문에 반려됐는지
> 알려주면 어떨까?"**

그래서 이 프로젝트의 AI는 문서를 대신 써 주지 않습니다. `approval_reject_history` 에 쌓인
실제 반려 유형을 집계해서 **"이 지적은 과거 3건의 실제 반려에 근거한다"** 는 숫자와 함께
보여줍니다. 근거 없는 일반론 — *"더 자세히 쓰세요"* 같은 말 — 은 하지 않는 것이 이 기능의
전제입니다.

## 화면

| 내 결재함 — 탭 배지로 할 일이 한눈에 | 기안 작성 — 지출결의 |
|---|---|
| ![내 결재함](docs/images/01-box.png) | ![기안 작성](docs/images/02-write-expense.png) |

| 기안 작성 — 연차신청 | 상신 전 사전점검 |
|---|---|
| ![연차 기안](docs/images/03-write-leave.png) | ![사전점검](docs/images/04-preflight.png) |

---

## 5분 안에 돌려보기

```powershell
.\mvnw.cmd clean package     # target/flowmate.war 생성 (코드를 바꿀 때만)
docker compose up -d         # PostgreSQL + 외부 Tomcat 10.1, 빈 볼륨이면 시드까지 자동
```

→ **http://localhost:18080/flowmate** · 계정 `2020003` / 비밀번호 `flowmate1!`

**API 키는 필요 없습니다.** `ai.enabled` 기본값이 `false` 라 키 없이 그대로 뜹니다 — AI 기능
4종만 꺼지고 전자결재·근태관리는 전부 정상 동작합니다.

<details>
<summary>왜 WAR 를 먼저 빌드하나 / 로컬 개발 / 테스트 / AI 켜기</summary>

**WAR 를 먼저 빌드하는 이유** — 이 프로젝트의 산출물은 Spring Boot 실행형 jar 가 아니라
**WAR** 입니다(외부 WAS 배포가 핵심 주장 중 하나). `docker-compose.yml` 은 이미 만들어진
WAR 를 외부 Tomcat 컨테이너에 얹기만 합니다.

**로컬 개발** (내장 Tomcat, 빠른 재시작)
```powershell
docker compose up -d postgres
.\mvnw.cmd spring-boot:run          # → http://localhost:8080/
```

**테스트**
```powershell
.\mvnw.cmd test      # 단위 176건 (DB 불필요)
.\mvnw.cmd verify    # 단위 + 통합 309건 (DB 기동 필요)
```

**AI 기능 켜기** — 값은 저장소에 들어가지 않습니다. compose 를 실행하는 셸에만 넣습니다.
```powershell
$env:AI_ENABLED = 'true'
$env:GEMINI_API_KEY = [Environment]::GetEnvironmentVariable('GEMINI_API_KEY','User')
docker compose up -d --force-recreate tomcat
```
키가 없으면 **기동 자체가 실패**합니다 — 설정 실수가 "AI 일시 장애"로 위장되지 않게
일부러 그렇게 만들었습니다.

**스키마를 고쳤을 때만** 볼륨을 지우고 다시 올립니다.
```powershell
docker compose down -v
docker compose up -d
```
</details>

---

## 주요 기능

비밀번호는 전원 `flowmate1!` 입니다.

| 사원번호 | 이름 | 부서 · 직급 | 역할 |
|---|---|---|---|
| `2020003` | 곽수빈 | 개발팀 · 사원 | **기안자** |
| `2016004` | 신동혁 | 개발팀 · 과장 | 1차 결재 |
| `2016002` | 박현주 | 사업본부 · 부장 | 2차 결재 |
| `2015001` | 정도현 | 대표이사실 · 이사 | 임원 결재(300만원 초과) |

### ① 결재와 근태가 한 트랜잭션으로

1. **곽수빈**으로 로그인 → 기안 작성 → 유형 **연차신청** → 기간 입력 → **상신**
   (AI가 켜져 있으면 과거 반려 이력에 근거한 사전점검 모달이 뜹니다)
2. **신동혁** → 내 결재함 **대기** 탭 → 승인
3. **박현주** → 대기 탭 → 최종 승인
4. 곽수빈으로 돌아가 **잔여 연차가 줄고**, 내 근태에 해당 기간이 **'연차'로 자동 반영된 것**을 확인

### ② 설정값 하나로 결재 규칙 변경

1. 곽수빈으로 지출결의 **100만원** → 결재선 `신동혁 → 박현주`
2. **500만원**으로 바꾸면 → `정도현(이사)` 추가 (300만원 초과 규칙)
3. `application.yml` 의 `flowmate.approval.line-policy` 를 `simple-two-step` 으로 바꾸고 재기동
   → 같은 500만원인데 결재선이 **부서장 1명**으로 줄어듭니다. **코드는 한 줄도 안 고칩니다.**

### ③ 반려가 AI의 재료가 된다

신동혁이 반려할 때 **유형 선택이 필수**입니다. 그 유형이 `approval_reject_history` 에 쌓이고,
①의 사전점검이 이 표를 읽어 *"과거 반려 3건에 근거함"* 이라는 숫자를 만듭니다.
자유 텍스트만 받으면 이 집계가 불가능하다는 것이 유형을 필수로 둔 이유입니다.

### ④ 같은 이력이 반대 방향으로도 쓰인다 (AI 켰을 때)

기안 작성의 본문 칸 아래 **[AI 제안 받기]** 를 누르면, 같은 반려 이력을 근거로 이번에는
**쓰기 전에** 뼈대를 잡아 줍니다. 사전점검이 *"이대로 올리면 반려된다"* 라면 이건
*"이 부서에서 자주 빠뜨리는 항목을 미리 넣어 두자"* 입니다.

모르는 값은 지어내지 않고 `[금액]` 처럼 자리표시자로 남기며, 이미 쓴 내용은 지우지 않고
살려서 다듬습니다. 제안은 **[본문에 넣기]** 를 눌러야 적용됩니다.

---

## 아키텍처

```
[브라우저]  JSP/JSTL + jQuery, 세션 로그인(Spring Security 6)
    ↓
[Controller]  com.flowmate.{org,approval,attendance,ai}.controller
    ↓
[Service]  트랜잭션 경계
    │  연차 승인은 approval → attendance 를 이벤트가 아니라
    │  같은 트랜잭션에서 직접 호출한다 (인터페이스로만 결합)
    ↓
[Mapper]  MyBatis 3 (인터페이스 + XML)
    ↓
[PostgreSQL 16]  Oracle 대응은 docs/oracle-mapping.md
```

**AI 게이트웨이** — 기능 4종이 이 뒤에 선다.

```
[SummaryService] [PreflightService] [DraftHintService] [LeaveContextService(LLM 미사용)]
    ↓
LlmClient 데코레이터 체인
    Caching → Masking → Logging → Resilient → 실제 클라이언트
      │         │                                ├─ ClaudeLlmClient (Anthropic)
      │         └─ 실제 호출보다 바깥이라야       ├─ GeminiLlmClient (Google)
      │            원문이 캐시·로그에 안 남는다   └─ FakeLlmClient   (키 없을 때)
      └─ 히트하면 마스킹도 API 호출도 안 한다
```

패키지 루트는 `com.flowmate.{org, approval, attendance, ai, common, config}` 6개입니다.
`approval` 이 `attendance.service.LeaveApplyService` **인터페이스만** 알고 구현이나 매퍼를
모르므로 의존 방향은 `approval → attendance` 한쪽입니다(순환 없음).

---

## 커스터마이징 지점

그룹웨어는 회사마다 결재 규칙도 근무 시간도 다릅니다. 그런 것들을 코드가 아니라 설정으로
바꿀 수 있게 미리 갈라 뒀습니다. **다섯 지점 모두 구현체를 2개씩 만들어 설정값 하나로
교체되는 것을 테스트로 고정합니다** (같은 입력, 다른 설정 → 다른 결과).

| # | 인터페이스 | 구현체 | 설정 키 |
|---|---|---|---|
| 1 | `ApprovalLinePolicy` | Default(부서 트리 + 임원) / SimpleTwoStep(부서장 1명) | `flowmate.approval.line-policy` |
| 2 | `LeaveGrantPolicy` | Flat(전원 15일) / TenureBased(근속 비례) | `flowmate.attendance.leave-grant-policy` |
| 3 | `WorkTimePolicy` | Default(09-18 고정) / Flexible(코어타임) | `flowmate.attendance.work-time-policy` |
| 4 | `PromptRepository` | File(classpath) / Database(`ai_prompt`, 5분 TTL) | `ai.prompt-repository` |
| 5 | `ai.features.*` | 기능별 on/off (summary · preflight · draft-hint · leave-context) | `ai.features.summary` 등 |

---

## 테스트

| 구분 | 파일 규칙 | 실행 | DB |
|---|---|---|---|
| 단위 176건 | `*Test.java` | `mvnw.cmd test` | 불필요 |
| 통합 133건 | `*IT.java` | `mvnw.cmd verify` | 필요 |

**API 키 없이 `mvnw clean verify` 가 통과하는 것이 지켜야 할 계약입니다.** AI 기능 4종은
`FakeLlmClient` 로 마스킹·캐싱·폴백·기능 플래그까지 체인 수준에서 검증됩니다.

실제 LLM 응답 품질을 보는 **고정 평가셋 8건**만 키가 있어야 수동으로 돌아갑니다
(`PreflightEvalSetIT` 5건 · `DraftHintEvalSetIT` 3건, 둘 다 `@Tag("llm")` 이라 기본 빌드에서 제외).

```powershell
$env:GEMINI_API_KEY = [Environment]::GetEnvironmentVariable('GEMINI_API_KEY','User')
.\mvnw.cmd verify "-Dit.test=*EvalSetIT" "-Dgroups=llm" `
    "-Dflowmate.eval.excludedGroups=" "-Dai.enabled=true"
```

결과 기록은 [`docs/ai-eval-results.md`](docs/ai-eval-results.md) 참고 (Gemini
`gemini-3.5-flash-lite`, **5/5 통과**).

단위 테스트가 DB 없이 도는 경계를 의도적으로 유지합니다. 이 경계가 무너지면 순수 로직
테스트가 컨테이너 기동에 묶여 빠른 피드백을 잃습니다.

---

## 더 보기

| | |
|---|---|
| [`docs/design-notes.md`](docs/design-notes.md) | **설계 판단 9가지** — 각 결정에서 버린 대안과 그 이유. 알려진 제약도 함께 |
| [`docs/oracle-mapping.md`](docs/oracle-mapping.md) | PostgreSQL 전용 문법을 쓸 때마다 그 자리에서 적어 둔 Oracle 대응표 |
| [`docs/ai-eval-results.md`](docs/ai-eval-results.md) | AI 평가셋 실행 기록 — 실패 사례와 프롬프트 수정 내역까지 |
| [`docs/superpowers/`](docs/superpowers/) | 원 설계서와 Phase별 계획서 (개발 과정 기록) |
