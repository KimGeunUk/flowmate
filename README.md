# FlowMate

### AI 사전점검 그룹웨어 — 전자결재 · 근태관리

AI가 결재 반려를 미리 막아주는 사내 그룹웨어.

- 설계서: [docs/superpowers/specs/2026-08-05-flowmate-design.md](docs/superpowers/specs/2026-08-05-flowmate-design.md)
- 구현 로드맵: [docs/superpowers/plans/2026-08-05-flowmate-roadmap.md](docs/superpowers/plans/2026-08-05-flowmate-roadmap.md)

## 기술 스택

Java 17 · Spring Boot 3.5.16 (WAR) · JSP + JSTL + jQuery · MyBatis 3 · PostgreSQL 16 · Spring Security 6 · Anthropic Java SDK · Google Gen AI Java SDK · Maven · Docker

## 실행 방법

### 1. DB 기동

```powershell
docker compose up -d postgres
```

스키마를 추가하거나 고친 뒤에는 볼륨을 지우고 다시 올려야 init 스크립트가 재실행된다.

```powershell
docker compose down -v
docker compose up -d postgres
```

### 2. 애플리케이션 실행

```powershell
.\mvnw.cmd spring-boot:run
```

`http://localhost:8080/`

개발 중에는 이 명령을 표준으로 쓴다. `java -jar` 로 직접 실행하면 JVM 문자셋이
플랫폼 기본값(이 PC 는 MS949)이 되므로 `-Dfile.encoding=UTF-8` 이 필요하다.

### 3. 테스트

```powershell
.\mvnw.cmd test      # 단위 테스트 (DB 불필요)
.\mvnw.cmd verify    # 단위 + 통합 테스트 (DB 기동 필요)
```

### 4. WAR 빌드

```powershell
.\mvnw.cmd clean package
# target/flowmate.war
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

## 구현 현황

- [x] Phase 0 — 환경 구축 (JSP + Jakarta JSTL + MyBatis + PostgreSQL)
- [x] Phase 1 — 조직 · 사용자 (로그인, 사원 목록, 조직도, 공통 레이아웃)
- [x] Phase 2 — 전자결재 코어
- [x] Phase 3 — AI 게이트웨이 (화면 없음 - `LlmClient` 데코레이터 체인, 마스킹, 캐싱, 폴백)
- [x] Phase 4 — 근태 + 연동 (출퇴근 등록, 근태 조회, 연차 승인 → 근태 반영)
- [ ] Phase 5 — AI 기능 (Task 1~8 완료 — 문서 요약 · 사전점검 · 연차 맥락 · 캐시 TTL · 기능 플래그 ·
      `DatabasePromptRepository`. Task 9 평가셋은 실제 API 키가 필요해 기본 빌드에서 제외, 수동 실행 대상.
      Task 10 마감은 미실행. 계획 외 추가: `LlmClient` 세 번째 구현 `GeminiLlmClient` — 아래
      "설계 판단 기록" 참고)
- [ ] Phase 6 — 마감 (CSS · Docker 배포 · README)

## 테스트

| 구분 | 파일 규칙 | 실행 | DB |
|---|---|---|---|
| 단위 | `*Test.java` | `mvnw.cmd test` | 불필요 |
| 통합 | `*IT.java` | `mvnw.cmd verify` | 필요 |

Phase 4 종료 시점: 단위 136건 · 통합 100건 (Phase 3 종료 시점 단위 81건 · 통합 61건에서 증가).
Phase 5 Task 1~8 종료 시점: 단위 150건 · 통합 130건. Gemini 구현 추가(계획 외, 아래 판단
기록 참고) 이후(현재): **단위 154건 · 통합 130건.**

`ai.enabled` 는 기본값이 `false` 라 `ANTHROPIC_API_KEY`/`GEMINI_API_KEY` 둘 다 없어도
빌드·기동·테스트가 전부 통과한다 — `mvnw clean verify` 가 그 기본값 그대로 통과하는 것이
확인된 계약이다. AI 기능 3종(요약·사전점검·연차 맥락)은 화면까지 있지만 `FakeLlmClient` 로
마스킹·캐싱·폴백·기능 플래그까지 체인 수준에서 검증된다. 실제 LLM 응답 품질을 보는
평가셋(Task 9)만 API 키가 있어야 수동으로 돌릴 수 있고, 기본 빌드에는 포함되지 않는다 -
Gemini 도 같은 원칙이다: `LlmConfigTest` 는 조건부 배선과 "키 없으면 기동 실패"만
실제 Spring 컨텍스트로 검증하고, 실제 Gemini 호출은 하지 않는다.

단위 테스트가 DB 없이 도는 경계를 의도적으로 유지한다. 이 경계가 무너지면
순수 로직 테스트가 컨테이너 기동에 묶여 빠른 피드백을 잃는다.

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
(`LlmChainIT`, `LlmConfigTest`). 자세한 내용과 스위치 방법은 아래 "설계 판단 기록"의
"세 번째 LlmClient 구현으로 Gemini 를 추가했다" 절 참고.

## 설계 판단 기록

### 연차 승인 → 근태 반영: Spring 이벤트 대신 같은 트랜잭션의 직접 호출을 선택했다 (Phase 4)

연차 신청서가 최종 승인되면 잔여 연차를 줄이고 해당 일자의 근태를 '연차'로 바꿔야 한다.
`ApprovalService.approve()` 가 `attendance` 모듈의 `LeaveApplyService.apply(...)` 를
**Spring `ApplicationEvent` 가 아니라 같은 트랜잭션 안에서 직접 호출**한다.

- **이벤트의 트랜잭션 경계가 불명확한 것이 문제다.** 기본 `ApplicationEventPublisher` 는
  발행 즉시 동기 호출되므로 얼핏 안전해 보이지만, 리스너가 `@TransactionalEventListener`
  로 바뀌거나 비동기(`@Async`)로 바뀌는 순간 승인 트랜잭션과 근태 반영 트랜잭션이
  분리된다. 그 경계는 코드를 눈으로 봐서는 드러나지 않고, 리스너 쪽 애노테이션 하나로
  조용히 달라진다 — "승인은 됐는데 연차가 안 깎였다"는 실제 그룹웨어에서 흔한 사고가
  정확히 이 지점에서 생긴다.
- **같은 트랜잭션 안의 직접 호출은 실패를 한데 묶는다.** 결재선 갱신·문서 상태 갱신처럼
  이미 실행된 쓰기가 있어도, 근태 반영이 실패하면 그 트랜잭션 전체가 롤백되어 문서는
  `PENDING` 으로, 잔여 연차는 원래 값으로 되돌아간다. 부분 성공이라는 상태 자체가
  나오지 않는다. `ApprovalServiceLeaveApplyRollbackIT#attendanceFailureRollsBackTheApproval`
  이 근태 반영을 일부러 실패시켜 이 롤백을 실측으로 증명한다.
- **결합도는 인터페이스로 완화한다.** `approval` 이 아는 것은 `attendance.service.LeaveApplyService`
  인터페이스 하나뿐이고, 구현(`DefaultLeaveApplyService`)이나 매퍼는 모른다. 값도
  `approvalId` 하나가 아니라 `LeaveApplyCommand`(empId·leaveType·기간·일수)로 넘긴다 —
  `attendance` 가 `approval` 소유 테이블(`leave_request`)을 읽을 필요가 없어져 의존이
  `approval → attendance` 한 방향으로 유지된다(순환 없음). 이벤트가 주는 이점(느슨한
  결합) 중 트랜잭션 안전성과 무관한 부분은 인터페이스 분리만으로 이미 얻은 셈이다.

대안(이벤트로 비동기 반영)이 데이터 정합성보다 결합도를 우선한 선택이었다면, 이 프로젝트가
증명하려는 것("두 모듈이 하나의 트랜잭션 안에서 정확히 맞물린다")과 정면으로 어긋난다.

### 세 번째 `LlmClient` 구현으로 Gemini 를 추가했다 (계획 외, Phase 5 이후)

`docs/superpowers/plans/2026-08-09-phase-5-ai-features.md` 의 Task 10 마감 이후에 진행한,
그 계획서에는 없던 작업이다. `LlmClient` 구현이 `ClaudeLlmClient`/`FakeLlmClient` 둘에서
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
  model: gemini-2.0-flash
```

`claude-opus-5` 로 되돌리려면 `provider: claude` 와 `model: claude-opus-5` 를 함께 바꾼다
(모델명만 바꾸면 provider 가 여전히 gemini 라서 아무 효과가 없다 — `AiProperties` 주석 참고).
`ClaudeLlmClient` 가 `ANTHROPIC_API_KEY` 없이 `ai.enabled=true` 로 뜨는 것을 막듯,
`GeminiLlmClient` 도 `GEMINI_API_KEY` 없이는 기동 자체가 실패한다(`LlmConfig` 참고) — 설정
실수를 일시적 장애로 착각하게 두지 않는다는 같은 원칙을 그대로 적용했다.

**설계서와의 관계:** 설계서(§6.4.1)는 Anthropic 을 전제로 데코레이터 체인(Caching → Masking →
Logging → Resilient)을 정의했지만, 그 코드 자체는 `LlmClient` 인터페이스에만 의존하고 어느
회사의 API 인지 모른다. Gemini 를 추가하면서 그 체인 코드는 한 줄도 건드리지 않았다 — 늘어난
것은 `GeminiLlmClient` 구현 파일 하나와 `LlmConfig` 의 `@ConditionalOnProperty` 조건 하나뿐이다.
"설계서가 전제한 제공자를 실제로 바꿔 봐도 게이트웨이가 무사하다"는 것이 곧 이 추가가
증명하려는 것이다.

**테스트는 여전히 키 없이 돈다:** `LlmConfigTest`(Surefire, DB 불필요)가 `ai.enabled` ×
`ai.provider` 세 조합 모두 `ApplicationContextRunner` 로 검증한다 - `enabled=false` 는
`FakeLlmClient` 가 정상 선택되는 것을, `enabled=true`(provider=claude 또는 gemini, 또는
provider 생략 시 기본값 gemini)는 API 키가 없어 **의도한 메시지로 기동 자체가 실패하는 것**을
단정한다. 실제 Gemini 호출을 부르는 테스트는 추가하지 않았다 — Task 9 평가셋과 같은 이유로,
실제 API 호출 검증은 기본 빌드가 아니라 수동 실행 대상이다.

### 무료 등급과 민감정보 마스킹 — 왜 이 프로젝트에서는 상관이 적은가, 그래도 왜 중요한가

일반적으로 LLM 제공자의 **무료 등급은 제출한 데이터를 서비스 개선(모델 학습 등)에 쓸 수 있고,
유료 등급은 쓰지 않는 경우가 많다** — 정확한 조건은 제공자·시점마다 다르므로 실제로 쓸 때는
그 제공자의 최신 약관을 확인해야 한다. `ai.provider: gemini` 를 기본값으로 둔 이 프로젝트는
그 축에서 보면 Claude(유료뿐)보다 데이터가 나갈 여지가 이론적으로는 더 크다.

그런데 두 가지가 이미 이 문제를 크게 줄여 놓았다:

- **마스킹 계층이 이미 있다.** `MaskingLlmClient`/`SensitiveDataMasker` 가 실제 호출 직전에
  주민등록번호·계좌번호·전화번호·사업자번호·카드번호·이메일을 토큰으로 치환한다 — 어느
  제공자로 나가든 원문이 나가지 않는다(`LlmChainIT#originalTextNeverReachesTheInnerClient`).
  사전 점검(`PreflightService`)은 한 걸음 더 나아가 반려 사유 **원문 자체를 프롬프트에 아예
  넣지 않는다** — `reason_category` 와 빈도만 넣는다(계획서 Task 5 참고). 사람 이름·금액이
  들어있는 텍스트가 애초에 프롬프트에 오르지 않는다.
- **이 프로젝트의 모든 데이터가 데모 시드다.** 실제 사원·실제 결재 문서가 아니라
  `docker/postgres/init` 이 만든 가상의 이름·부서·금액이다.

즉 이 프로젝트에서는 무료 등급을 써도 잃을 실제 개인정보가 없다. 그렇다고 마스킹 계층이
덜 중요해지는 것은 아니다 — 오히려 **무료 등급에서는 마스킹이 더 중요해진다.** 이 저장소를
그대로 가져다 실제 조직의 데이터로 돌리는 사람이 있다면(포트폴리오 코드가 실제로 재사용되는
흔한 경로다), 그 순간부터 마스킹은 "혹시 모를 대비"가 아니라 "무료 등급이 그 데이터를 학습에
쓸 수도 있다"는 실제 위험을 막는 유일한 장치가 된다. 이 프로젝트가 처음부터 마스킹을
데코레이터 체인의 가장 안쪽(실제 호출 바로 바깥)에 둔 이유(`LlmConfig` 클래스 주석 참고)가
바로 이것이다 — 어떤 공급자를 선택하든, 어떤 등급을 쓰든 그 위치가 방어선이 되도록.
