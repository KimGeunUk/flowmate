# FlowMate

### AI 사전점검 그룹웨어 — 전자결재 · 근태관리

AI가 결재 반려를 미리 막아주는 사내 그룹웨어.

- 설계서: [docs/superpowers/specs/2026-08-05-flowmate-design.md](docs/superpowers/specs/2026-08-05-flowmate-design.md)
- 구현 로드맵: [docs/superpowers/plans/2026-08-05-flowmate-roadmap.md](docs/superpowers/plans/2026-08-05-flowmate-roadmap.md)
- Oracle 이식 대응표: [docs/oracle-mapping.md](docs/oracle-mapping.md)
- AI 평가셋 실행 기록: [docs/ai-eval-results.md](docs/ai-eval-results.md)

## 이건 무엇이고 누구를 위한 것인가

**실제 서비스가 아니라 그룹웨어 커스터마이징 능력을 보여주기 위한 포트폴리오다.** "전자결재·근태관리"라는
흔한 그룹웨어 도메인 위에, 채용 공고가 요구하는 것들을 코드로 증명하는 데 초점을 맞췄다.

- **커스터마이징**: 인터페이스 5개를 구현체 2개씩으로 만들고, 같은 입력에 설정값 하나만 바꿔 다른
  결과가 나오는 것을 통합 테스트로 증명한다. "고객사마다 업무 규칙이 다르다"를 코드로 보여주는 것이 목적이다.
- **DB 이식성**: PostgreSQL 16으로 개발하되, 목표 납품 환경(Oracle)에서 무엇이 달라지는지 그
  자리에서 즉시 기록했다(`docs/oracle-mapping.md`). 사후에 몰아 쓴 문서가 아니다.
  재귀 CTE, 행 잠금, `ON CONFLICT`, 자문 잠금 같은 PostgreSQL 전용 기능을 실제로 썼고 전부 대응이 있다.
- **트랜잭션 정합성**: 연차 승인이 잔여 연차 차감과 근태 반영을 하나의 트랜잭션으로 묶는다는 것을,
  일부러 실패를 주입해 전체가 롤백되는 것을 실측으로 증명하는 테스트로 고정했다.
- **AI를 다루는 태도**: LLM을 게이트웨이 뒤에 감춰 마스킹·캐싱·폴백까지 갖췄고, AI가 실패해도
  업무(상신)는 절대 막히지 않는다. 품질은 감으로 판단하지 않고 고정 평가셋으로 확인한다.

## 기술 스택

Java 17 · Spring Boot 3.5.16 (WAR) · JSP + JSTL + jQuery · MyBatis 3 · PostgreSQL 16 · Spring Security 6 · Anthropic Java SDK · Google Gen AI Java SDK · Maven · Docker

## 실행 방법

### 1. 컨테이너로 전체 기동 (권장)

```powershell
.\mvnw.cmd clean package        # target/flowmate.war 를 만든다 - 코드를 바꿀 때만 다시 실행
docker compose up -d            # PostgreSQL + 외부 Tomcat 10.1 컨테이너, 빈 볼륨이면 시드까지 자동
```

→ **http://localhost:18080/flowmate**

이 프로젝트의 산출물은 Spring Boot 실행형 jar 가 아니라 **WAR**다(외부 WAS 배포가 핵심 주장 중
하나다). `docker-compose.yml` 은 그 WAR 를 이미 만들어진 상태로 가져다 외부 Tomcat 컨테이너에
얹기만 하므로, 컨테이너를 올리기 전에 WAR 를 한 번 빌드해 둔다.

**★ API 키는 필요 없다.** `ai.enabled` 의 기본값이 `false` 라서 `ANTHROPIC_API_KEY`/`GEMINI_API_KEY`
둘 다 없어도 그대로 뜬다 — AI 기능 세 개(문서 요약·상신 전 사전점검·연차 맥락 표시)만 꺼진 채로,
전자결재·근태관리를 포함한 나머지 전부는 정상 동작한다.

스키마를 고쳤을 때만 볼륨을 지우고 다시 올린다(데모용으로 한 번만 띄운다면 필요 없다):

```powershell
docker compose down -v
docker compose up -d
```

### 2. 로컬 개발

```powershell
docker compose up -d postgres
.\mvnw.cmd spring-boot:run
```

→ **http://localhost:8080/**

개발 중에는 이 명령을 표준으로 쓴다(내장 Tomcat, 빠른 재시작). `java -jar` 로 직접 실행하면 JVM
문자셋이 플랫폼 기본값(이 PC 는 MS949)이 되므로 `-Dfile.encoding=UTF-8` 이 필요하다 — 컨테이너
배포에서는 `docker-compose.yml` 이 `CATALINA_OPTS=-Dfile.encoding=UTF-8` 로 이미 고정해 둔다
(슬림 리눅스 이미지는 로케일이 `POSIX`/`C` 인 경우가 많아, 지정하지 않으면 **컨테이너에서만**
한글이 깨진다 — 로컬이 정상인 것은 증거가 안 된다).

### 3. 테스트

```powershell
.\mvnw.cmd test      # 단위 테스트 154건 (DB 불필요)
.\mvnw.cmd verify    # 단위 + 통합 테스트 284건 (DB 기동 필요)
```

## 데모 계정

비밀번호는 전원 `flowmate1!` 이다.

| 사원번호 | 이름 | 부서 | 직급 | 권한 | 용도 |
|---|---|---|---|---|---|
| `2020003` | 곽수빈 | 개발팀 | 사원 | USER | 기안자 |
| `2016004` | 신동혁 | 개발팀 | 과장 | MANAGER | 개발팀 부서장 (1차 결재) |
| `2016002` | 박현주 | 사업본부 | 부장 | MANAGER | 상위 결재 |
| `2015001` | 정도현 | 대표이사실 | 이사 | ADMIN | 임원 결재 · 관리자 |
| `2017001` | 최민석 | 인사팀 | 차장 | ADMIN | 인사 담당 |

## 조직 구조 (시드)

```
대표이사실 (정도현 · 이사)
├─ 경영지원본부 (김성일 · 부장)
│   ├─ 인사팀   (최민석 · 차장) 3명
│   └─ 재무팀   (오세훈 · 과장) 3명
└─ 사업본부   (박현주 · 부장)
    ├─ 마케팅팀 (윤서영 · 차장) 4명
    └─ 개발팀   (신동혁 · 과장) 7명
```

부서마다 최고 직급이 1명씩만 배치되어 있다. 이후 결재선 정책이
"같은 부서 최고 직급"으로 부서장을 판정하므로, 동급이 둘이면 결재선이 비결정적으로 바뀐다.

## 아키텍처 스케치

```
[브라우저]  jQuery + JSP/JSTL, 세션 기반 로그인(Spring Security 6)
    │
    ▼
[Controller]  com.flowmate.{org,approval,attendance,ai}.controller
    │  예외 매핑은 모듈별로 범위를 좁힌다 (@ControllerAdvice(basePackages=...))
    ▼
[Service]  트랜잭션 경계.
    │  연차 승인은 approval → attendance 를 이벤트가 아니라 같은 트랜잭션에서
    │  직접 호출한다(아래 "설계 판단 기록" 참고) - 인터페이스로만 결합
    ▼
[Mapper]  MyBatis 3 (인터페이스 + XML)
    ▼
[PostgreSQL 16]  개발 DB · 목표 납품 환경(Oracle) 대응은 docs/oracle-mapping.md


AI 게이트웨이 (화면 기능 3종이 이 뒤에 선다. 화면 자체는 Phase 5, 게이트웨이는 Phase 3):

[AiController] → [SummaryService / PreflightService / LeaveContextService]
    → LlmClient(인터페이스) 데코레이터 체인
      Caching → Masking → Logging → Resilient → 실제 클라이언트
                                                    ├─ ClaudeLlmClient (Anthropic, 실호출)
                                                    ├─ GeminiLlmClient (Google, 실호출)
                                                    └─ FakeLlmClient   (테스트/키 없을 때)
    교체 지점: ai.enabled(켜고 끄기) + ai.provider(claude|gemini)
```

패키지 루트는 `com.flowmate.{org, approval, attendance, ai, common, config}` 6개다.
`approval` 이 `attendance.service.LeaveApplyService` 인터페이스만 알고 구현이나 매퍼를 모르므로,
의존 방향은 `approval → attendance` 한쪽으로만 유지된다(순환 없음).

## 커스터마이징 지점

공고의 "커스터마이징" 요구에 대한 답. **다섯 지점 모두 구현체 2개를 만들어 설정값
하나로 교체되는 것을 통합 테스트로 증명한다** (같은 입력, 다른 설정 → 다른 결과).

| # | 인터페이스 | 구현체 | 설정 키 | 교체 증명 |
|---|---|---|---|---|
| 1 | `ApprovalLinePolicy` | Default(부서 트리 + 임원) / SimpleTwoStep(부서장 1명) | `flowmate.approval.line-policy` | `DefaultApprovalLinePolicyTest` / `SimpleTwoStepLinePolicyTest` |
| 2 | `LeaveGrantPolicy` | Flat(전원 15일) / TenureBased(근속 비례) | `flowmate.attendance.leave-grant-policy` | `FlatLeaveGrantPolicyTest` / `TenureBasedLeaveGrantPolicyTest` |
| 3 | `WorkTimePolicy` | Default(09-18 고정) / Flexible(코어타임) | `flowmate.attendance.work-time-policy` | `DefaultWorkTimePolicyTest` / `FlexWorkTimePolicyTest` |
| 4 | `PromptRepository` | File(classpath) / Database(`ai_prompt` 테이블, 5분 TTL 캐시) | `ai.prompt-repository` | `DatabasePromptRepositoryIT` — 같은 `(feature, version)` 에 File·DB 가 다른 문구를 갖게 하고 설정에 따라 다른 문구가 나오는 것을 단정 |
| 5 | `ai.features.*` 플래그 | 기능별 on/off (summary/preflight/leave-context) | `ai.features.summary` 등 | `AiFeatureFlagsDisabledIT` — 플래그를 끄면 해당 기능이 `LlmClient` 를 전혀 부르지 않는 것을 단정 |

**덤(다섯 지점에 안 들어간다):** `LlmClient` 의 세 구현(Claude 실호출 / Gemini 실호출 / Fake)이
`ai.enabled` + `ai.provider` 로 교체되지만, 이건 "AI 제공자를 바꾸는 것"이지 "고객사별 업무
규칙을 바꾸는 것"이 아니라서 위 표의 다섯 지점과 성격이 다르다 — 별도로 취급한다
(`LlmChainIT`, `LlmConfigTest`). 자세한 내용은 아래 "설계 판단 기록"의
"세 번째 `LlmClient` 구현으로 Gemini 를 추가했다" 절 참고.

## 설계 판단 기록

포트폴리오에서 가장 값이 있는 부분이라고 생각한다 — 무엇을 만들었는지보다, **왜 그렇게
만들었는지**가 코드를 읽는 사람에게 남는다.

### 1. 연차 승인 → 근태 반영: Spring 이벤트 대신 같은 트랜잭션의 직접 호출

연차 신청서가 최종 승인되면 잔여 연차를 줄이고 해당 일자의 근태를 '연차'로 바꿔야 한다.
`ApprovalService.approve()` 가 `attendance` 모듈의 `LeaveApplyService.apply(...)` 를
**Spring `ApplicationEvent` 가 아니라 같은 트랜잭션 안에서 직접 호출**한다.

- **이벤트의 트랜잭션 경계가 불명확한 것이 문제다.** 기본 `ApplicationEventPublisher` 는
  발행 즉시 동기 호출되므로 얼핏 안전해 보이지만, 리스너가 `@TransactionalEventListener`
  로 바뀌거나 비동기(`@Async`)로 바뀌는 순간 승인 트랜잭션과 근태 반영 트랜잭션이
  분리된다. 그 경계는 코드를 눈으로 봐서는 드러나지 않고, 리스너 쪽 애노테이션 하나로
  조용히 달라진다 — "승인은 됐는데 연차가 안 깎였다"는 실제 그룹웨어에서 흔한 사고가
  정확히 이 지점에서 생긴다.
- **같은 트랜잭션 안의 직접 호출은 실패를 한데 묶는다.** 이미 실행된 쓰기(결재선 갱신·문서
  상태 갱신)가 있어도, 근태 반영이 실패하면 그 트랜잭션 전체가 롤백되어 문서는 `PENDING`
  으로, 잔여 연차는 원래 값으로 되돌아간다. 부분 성공이라는 상태 자체가 나오지 않는다.
  `ApprovalServiceLeaveApplyRollbackIT#attendanceFailureRollsBackTheApproval` 이 근태 반영을
  일부러 실패시켜 이 롤백을 실측으로 증명한다.
- **결합도는 인터페이스로 완화한다.** `approval` 이 아는 것은 `LeaveApplyService` 인터페이스
  하나뿐이고, 구현이나 매퍼는 모른다. 값도 `approvalId` 하나가 아니라
  `LeaveApplyCommand`(empId·leaveType·기간·일수)로 넘겨 `attendance` 가 `approval` 소유
  테이블을 읽을 필요를 없앴다 — 이벤트가 주는 이점(느슨한 결합) 중 트랜잭션 안전성과
  무관한 부분은 인터페이스 분리만으로 이미 얻은 셈이다.

### 2. PostgreSQL 트랜잭션 중단 — 제약 위반을 잡아 재시도하지 않는다

문서번호를 `MAX+1` 로 계산해 넣고 `doc_no` UNIQUE 충돌 시 `DuplicateKeyException` 을 잡아
재계산·재시도하는 코드를 처음에 짰다가, **코드 리뷰에서 지적되어 실제 DB에 재현했다.**

PostgreSQL 은 제약 위반이 나면 트랜잭션 전체를 중단(abort) 상태로 만든다. 예외를 잡아도
같은 트랜잭션의 다음 쿼리가 전부 `25P02 (current transaction is aborted)` 로 죽으므로,
재시도할 "다음 쿼리" 자체가 실행되지 않는다 — 이 재시도 루프는 실전에서 한 번도 동작하지
않는 죽은 코드였다. `pg_try_advisory_xact_lock` 으로 직접 검증(같은 키는 `false`, 다른 키는
`true`, 트랜잭션 종료 시 자동 해제)한 뒤 `(접두사, 연도)` 단위 `pg_advisory_xact_lock` 을
채번 전에 걸어 충돌 자체를 없애는 방식으로 교체했다.

**이 패턴은 이식 가능하지 않다는 것도 중요하다.** Oracle 은 문장 단위(statement-level)
롤백이라 제약 위반 문장만 되돌리고 트랜잭션은 계속 살아 있으므로, "제약 위반 → catch →
재시도"가 Oracle 에서는 실제로 동작한다. 즉 PostgreSQL 에서 죽은 코드였던 패턴이 Oracle
에서는 살아있는 코드가 된다 — DB마다 트랜잭션 중단 범위가 다르다는 것을 코드 형태로 보여준
사례다. 자문 잠금은 Oracle 에 없으므로 Oracle 이식은 `SELECT ... FOR UPDATE` 나 전용 채번
테이블을 쓴다(`docs/oracle-mapping.md` §2.6). 근태 UPSERT(`ON CONFLICT ... DO UPDATE` →
`MERGE INTO`, §2.8)에도 같은 원칙이 적용된다 — 두 DB 모두 충돌을 예외로 만들지 않고
DB 안에서 해결한다.

### 3. 마스킹은 오탐을 허용하고 미탐을 불허한다

`SensitiveDataMasker` 가 외부 LLM 으로 나가는 텍스트에서 주민등록번호·계좌번호·전화번호·
사업자번호·카드번호·이메일을 토큰으로 치환한다. 계좌번호 패턴(`\d{2,6}-\d{2,6}-\d{2,8}`)은
넓어서 문서번호(`EXP-2026-0001`)나 날짜 일부를 계좌로 오인할 수 있는데, **그래도 패턴을
좁히지 않는다.**

- 오탐(과다 마스킹)의 대가: 요약 품질이 조금 떨어진다.
- 미탐(과소 마스킹)의 대가: 개인정보가 외부로 나간다.

이 비대칭은 의도된 선택이다. `SensitiveDataMaskerTest` 의 `docNoLooksLikeAccountAndThatIsAccepted`
는 문서번호가 계좌로 오인돼도 **테스트가 통과하는 것**으로 오탐 허용을 코드에 박아 두고,
`rrnIsMaskedEvenInsideLongerDigits` 는 공백 없이 붙은 주민번호도 잡는 것으로 미탐 불허를
강제한다. 둘 다 없으면 나중에 "정확도를 높이려고" 패턴을 좁혔을 때 아무도 못 막는다.

### 4. 사전점검은 상신을 절대 막지 않는다 — AI 실패가 업무 실패가 되면 안 된다

상신 버튼을 누르면 과거 반려 이력에 근거한 사전점검이 뜨지만, 점검이 `Optional.empty()`
(타임아웃·API 오류·폴백)를 돌려주면 **모달 없이 바로 상신된다.** 화면 스크립트도 서버 오류·
타임아웃 시 같은 경로를 탄다. AI 게이트웨이가 얼마나 튼튼하든(캐싱·로깅·재시도/폴백)
바깥에 있는 외부 API 라는 사실은 바뀌지 않으므로, "AI가 느리거나 죽었다"가 "결재를 못
올린다"로 번지면 안 된다는 것을 화면·서버 양쪽에서 같은 규칙으로 못박았다. 같은 이유로
프롬프트에는 반려 사유 **원문**을 아예 넣지 않고 `reason_category` 와 빈도만 넣는다 —
`RejectHistoryMapper` 의 SELECT 목록 자체에 `reason_text` 가 없다.

### 5. CSS를 마지막 Phase까지 미룬 이유와 그 결과

설계서 §4.4.2가 "의미 기반 클래스 이름만 쓰고 CSS 규칙은 마지막까지 안 쓴다"는 명명 규칙을
Phase 1부터 강제했다. 이건 스타일 작업 지침이 아니라 **아키텍처 검증**이다 — 명명 규칙이
지켜졌다면 마지막 Phase에서 `style.css` 한 파일만 고쳐도 전체 톤이 통일돼야 하고, 만약
JSP를 열어야 하는 상황이 생긴다면 그 자체가 규칙이 깨졌다는 신호다.

세 번 확인했고 세 번 다 성립했다.

1. Phase 1 최소 CSS 66개 규칙 — JSP diff 0
2. Phase 2 상태 배지 CSS — JSP를 열지 않음, 클래스 목록이 실제 사용과 정확히 일치
3. **Phase 6 최종 마감 — `git diff --stat` 이 `style.css` 한 줄뿐** (253 insertions, 3 deletions).
   JSP 120개 클래스 전부에 규칙을 붙이면서 마크업은 한 줄도 고치지 않았다. 시각 기반 이름
   (`.blue-*`, `.mt-10` 같은)은 사전 대조에서도 하나도 나오지 않았다.

세 번째가 가장 크고, 가장 늦게 왔고, 되돌릴 여지가 가장 적은 확인이었다 — 그리고 통과했다.

### 6. 평가셋이 존재하는 이유

"AI 기능 품질을 어떻게 검증했나"는 AI 기능을 넣는 순간 반드시 나오는 질문이고, 그 답이
저장소 안에 없으면 검증한 적이 없는 것과 같다. `PreflightEvalSetIT`(`@Tag("llm")`, 기본
빌드에서 제외)가 고정된 5건(문제 있는 문서 3건 → `WARN` 기대, 잘 쓴 문서 2건 → `PASS` 기대)을
**실제 Gemini API** 로 돌려 검증한다. 잘 쓴 문서 2건에도 반려 신호를 일부러 심어 둔 뒤 그래도
지적하지 않는지를 확인한다 — 신호가 없어서 조용한 것과 신호가 있어도 정확히 걸러내는 것은
다른 증명이기 때문이다.

처음부터 5/5는 아니었다. 3회의 프롬프트 수정(목적이 뭉뚱그려진 문서를 놓침 → 규칙 추가,
`basedOnRejectCount` 가 `"6건"` 문자열로 나와 파싱이 간헐적으로 실패 → 숫자만 쓰라는 규칙
추가)을 거쳐 5/5에 도달했다. 통과시키려고 기대값을 낮춘 적은 없다 — 실행 일자·모델·실패
사례·수정 내용까지 전부 `docs/ai-eval-results.md` 에 남아 있다.

### 7. 세 번째 `LlmClient` 구현으로 Gemini 를 추가했다 (계획 외, Phase 5 이후)

계획서에 없던 작업이다. `LlmClient` 구현이 `ClaudeLlmClient`/`FakeLlmClient` 둘에서
`GeminiLlmClient` 를 더해 셋이 됐다.

**왜:** Claude(Anthropic)는 유료뿐이라 이 저장소를 그대로 클론해서 실제 AI 호출을 만져 보려면
결제 수단이 있어야 한다. Gemini 는 무료 등급이 있다. 그리고 이 프로젝트의 핵심 주장 —
"`LlmClient` 뒤에서는 제공자를 갈아 끼워도 게이트웨이가 무사하다" — 은 원래 Claude 실호출과
Fake 두 구현으로 지탱하고 있었는데, 그중 하나가 테스트 대역이라 증거로는 약했다. 서로 다른
두 회사(Anthropic/Google)의 실제 API 를 같은 인터페이스 뒤에 세우고 나서야 그 주장이 실제로
증명됐다고 본다.

**어떻게 켜는가 — `application.yml`:**

```yaml
ai:
  enabled: false        # false → FakeLlmClient(키 없이 뜬다, 기본값). 이 계약은 안 바뀌었다.
  provider: gemini      # claude | gemini — enabled=true 일 때만 의미가 있다. 기본값 gemini.
  model: gemini-3.5-flash-lite
```

`claude-opus-5` 로 되돌리려면 `provider: claude` 와 `model: claude-opus-5` 를 함께 바꾼다
(모델명만 바꾸면 provider 가 여전히 gemini 라서 아무 효과가 없다). `ClaudeLlmClient` 가
`ANTHROPIC_API_KEY` 없이 `ai.enabled=true` 로 뜨는 것을 막듯, `GeminiLlmClient` 도
`GEMINI_API_KEY` 없이는 기동 자체가 실패한다(`LlmConfig` 참고) — 설정 실수를 일시적 장애로
착각하게 두지 않는다는 같은 원칙을 그대로 적용했다. 이 키 검사는 `System.getenv` 가 아니라
Spring `Environment` 를 통해 읽는다 — 실행하는 사람이 실제로 `GEMINI_API_KEY` 를 로컬에
설정해 두면 "키 없음" 조건을 검증하는 테스트가 그 환경에 의존해 깨지던 결함을 Phase 6에서
고쳤다(`LlmConfigTest` 가 이제 프로퍼티로 빈 값을 주입해 조건을 스스로 만든다).

**테스트는 여전히 키 없이 돈다.** `LlmConfigTest`(Surefire, DB 불필요)가 `ai.enabled` ×
`ai.provider` 조합을 `ApplicationContextRunner` 로 검증한다 — `enabled=false` 는
`FakeLlmClient` 가 선택되는 것을, `enabled=true`(claude/gemini)는 키가 없어 **의도한
메시지로 기동 자체가 실패하는 것**을 단정한다. 실제 API 호출 검증은 평가셋(6번 항목)과
같은 이유로 기본 빌드가 아니라 수동 실행 대상이다.

### 8. 무료 등급과 마스킹 — 왜 상관이 적은가, 그래도 왜 중요한가

일반적으로 LLM 제공자의 무료 등급은 제출한 데이터를 서비스 개선(모델 학습 등)에 쓸 수 있고
유료 등급은 쓰지 않는 경우가 많다(정확한 조건은 제공자·시점마다 다르다). `provider: gemini`
를 기본값으로 둔 이 프로젝트는 그 축에서 Claude(유료뿐)보다 이론적으로 노출 여지가 크다.

그런데 두 가지가 이미 이 문제를 크게 줄여 놓았다 — **마스킹 계층이 원문이 나가기 전에
막는다**(3번 항목), 그리고 **이 프로젝트의 모든 데이터가 데모 시드**라 실제 개인정보가
애초에 없다. 그렇다고 마스킹이 덜 중요해지는 것은 아니다. 이 저장소를 그대로 가져다 실제
조직의 데이터로 돌리는 사람이 있다면(포트폴리오 코드가 실제로 재사용되는 흔한 경로다),
그 순간부터 마스킹은 "혹시 모를 대비"가 아니라 "무료 등급이 그 데이터를 학습에 쓸 수도
있다"는 실제 위험을 막는 유일한 장치가 된다.

## 알려진 제약

포트폴리오에서 "남은 과제를 안다"는 것은 약점이 아니라 강점이라고 본다. 숨기지 않는다.

- **기능 3b(LLM 판단 코멘트)는 만들지 않았다.** 설계서 §9.1이 일정이 밀릴 때 잘라낼 1순위로
  이 기능을 직접 지정했고, 실제로 그 계획대로 잘라냈다. **하지 못한 것이 아니라 결정한 것이다.**
- **`LoginEmployee.eraseCredentials()`** 가 감싼 `Employee` 인스턴스를 직접 `null` 처리한다.
  `EmployeeMapper.findByEmpNo` 앞에 캐시가 없는 지금은 매 호출이 새 객체를 돌려주므로 안전하다.
  **이후 이 매퍼에 `@Cacheable` 이나 공유 조회 캐시를 붙이는 순간**, 캐시된 인스턴스의 내부
  값이 로그아웃 시 지워져 그 사원의 이후 모든 로그인이 조용히 실패한다 — 캐시를 붙이지 않는
  한 안전하고, 지금은 붙일 이유가 없어 그대로 뒀다.
- **`SecurityConfig` 의 `permitAll` 목록에 `/WEB-INF/views/login.jsp` 가 들어 있다.** 뷰
  경로가 외부 라우트로 오해될 수 있어 보기엔 이상하지만, 뺐을 때 실측으로 확인한 결과는
  `GET /login` 이 무한 자기 리다이렉트가 되는 것이었다 — 그래서 필요한 것으로 확인하고
  그대로 둔다.

## 데모 시나리오

### 핵심 시나리오 — 결재와 근태가 한 트랜잭션으로 맞물리는 것

1. `2020003` 곽수빈으로 로그인 → 기안 작성 → 문서 유형 **연차신청** 선택 → 잔여 연차와
   기간을 입력 → **상신** 클릭 → 상신 전 사전점검 모달이 뜬다(과거 반려 이력에 근거한
   AI 점검 — API 키가 없으면 이 모달 자체가 건너뛰어지고 바로 상신된다, 위 "설계 판단
   기록" 4번 참고).
2. `2016004` 신동혁으로 로그인 → 내 결재함 **대기** 탭 → 문서 승인.
3. `2016002` 박현주로 로그인 → 대기 탭 → 최종 승인.
4. 곽수빈으로 돌아가 **잔여 연차가 줄어든 것**과, 근태 조회 화면에 해당 기간이 **'연차'로
   자동 반영된 것**을 확인한다 — 승인과 근태 반영이 한 트랜잭션이라는 이 프로젝트의
   중심 주장이 화면에서 보이는 지점이다.

### 커스터마이징 시연 — 결재선 정책 교체

1. `2020003` 곽수빈으로 지출결의 100만원을 기안하면 결재선이 **신동혁(과장) → 박현주(부장)**
   자동 생성된다.
2. 금액을 500만원으로 바꿔 저장하면 결재선에 **정도현(이사)** 가 추가된다(300만원 초과 규칙).
3. `application.yml` 의 `flowmate.approval.line-policy` 를 `simple-two-step` 으로 바꾸고
   재기동하면, 같은 500만원에서도 결재선이 **팀장 1명(신동혁)** 으로 줄어든다 — 코드를
   고치지 않고 설정값 하나로 다른 결재 규칙을 얻는다.

### 반려 시연

신동혁이 반려할 때 유형 선택이 필수다. 선택한 유형은 `approval_reject_history` 에 쌓이고,
AI 사전점검(핵심 시나리오 1번)이 이 표를 읽어 "과거 반려 N건에 근거함" 같은 근거를 만든다.

## 구현 현황

- [x] Phase 0 — 환경 구축 (JSP + Jakarta JSTL + MyBatis + PostgreSQL)
- [x] Phase 1 — 조직 · 사용자 (로그인, 사원 목록, 조직도, 공통 레이아웃)
- [x] Phase 2 — 전자결재 코어
- [x] Phase 3 — AI 게이트웨이 (화면 없음 - `LlmClient` 데코레이터 체인, 마스킹, 캐싱, 폴백)
- [x] Phase 4 — 근태 + 연동 (출퇴근 등록, 근태 조회, 연차 승인 → 근태 반영)
- [x] Phase 5 — AI 기능 (문서 요약 · 사전점검 · 연차 맥락 · 평가셋 5/5 완료. 계획 외 추가:
      `LlmClient` 세 번째 구현 `GeminiLlmClient` — 위 "설계 판단 기록" 7번 참고)
- [x] Phase 6 — 마감 (CSS 통일, 컨테이너 배포 검증, README·문서 최종화)

## 테스트

| 구분 | 파일 규칙 | 실행 | DB |
|---|---|---|---|
| 단위 | `*Test.java` | `mvnw.cmd test` | 불필요 |
| 통합 | `*IT.java` | `mvnw.cmd verify` | 필요 |

**현재: 단위 154건 · 통합 130건 · `mvnw clean verify` BUILD SUCCESS — API 키 없이 통과한다.**
`ai.enabled` 기본값이 `false` 라 `ANTHROPIC_API_KEY`/`GEMINI_API_KEY` 둘 다 없어도 빌드·기동·
테스트가 전부 통과하는 것이 확인된 계약이다. AI 기능 3종은 `FakeLlmClient` 로 마스킹·캐싱·
폴백·기능 플래그까지 체인 수준에서 검증된다.

실제 LLM 응답 품질을 보는 **고정 평가셋 5건**만 API 키가 있어야 수동으로 돌릴 수 있고
(`PreflightEvalSetIT`, `@Tag("llm")`), 기본 빌드에는 포함되지 않는다 — 실행 방법과 결과는
[docs/ai-eval-results.md](docs/ai-eval-results.md) 참고(2026-08-10 실행, Gemini
`gemini-3.5-flash-lite`, **5/5 통과**).

단위 테스트가 DB 없이 도는 경계를 의도적으로 유지한다. 이 경계가 무너지면
순수 로직 테스트가 컨테이너 기동에 묶여 빠른 피드백을 잃는다.
