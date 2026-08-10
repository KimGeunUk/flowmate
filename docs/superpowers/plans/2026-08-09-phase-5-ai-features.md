# Phase 5 실행 계획 — AI 기능

> 이 계획서는 설계서 §6.4.5~6.4.7(AI 기능 3종), §5.5(AI 스키마), §9 Phase 5를 실행 단위로 옮긴 것이다.
> 로드맵: [2026-08-05-flowmate-roadmap.md](2026-08-05-flowmate-roadmap.md) — §3 규약이 전제다.
> 원본 설계서: [2026-08-05-flowmate-design.md](../specs/2026-08-05-flowmate-design.md)
> 작성일: 2026-08-09 · 분량: 4.0일 · 태그: `phase-5-ai-features`

---

## 시작 상태

`main` 이 `phase-4-attendance` 태그 상태다.

- 단위 **136** · 통합 **100** · BUILD SUCCESS
- 시드 `docs=6 lines=8 hist=15 rejects=1 balances=20 holidays=15 att=0 usage=0 cache=0 log=0`
- 전자결재 · 근태 · 결재-근태 연동이 전부 화면에서 동작한다
- AI 게이트웨이(마스킹 · 데코레이터 5단 · 캐싱 · 폴백)가 조립돼 있고 `ai.enabled=false` 가 기본값이다
- **AI 기능은 아직 하나도 없다** — 배관만 깔려 있다

## 이 Phase가 끝나면 무엇이 동작하는가

- 결재 문서를 열면 **3줄 요약과 핵심 사실**이 보이고, 두 번째부터는 캐시에서 나온다
- 상신 버튼을 누르면 **과거 반려 패턴에 근거한 사전 점검**이 뜬다 — "과거 반려 3건에 근거함"이라는 숫자와 함께
- 연차 결재 화면에 **신청자의 근태·잔여·팀 부재 현황**이 결합돼 보인다
- **고정 평가셋 5건**이 프롬프트 품질을 지킨다

---

## ★ 먼저 정정한다 — 커스터마이징 지점을 내가 잘못 세고 있었다

Phase 3 계획서에서 `LlmClient` 를 "커스터마이징 지점 4"라고 적었고, Phase 4 마무리에서 "5개 중 4개가 증명됐다"고 말했다. **둘 다 틀렸다.**

설계서 §7의 실제 목록은 이것이다:

| # | 인터페이스 | 구현체 | 현재 상태 |
|---|---|---|---|
| 1 | `ApprovalLinePolicy` | Default / SimpleTwoStep | ✅ Phase 2 |
| 2 | `LeaveGrantPolicy` | Flat / TenureBased | ✅ Phase 4 |
| 3 | `WorkTimePolicy` | Default / Flexible | ✅ Phase 4 |
| 4 | **`PromptRepository`** | **File / (향후) Database** | ⚠️ File 뿐 |
| 5 | **`ai.features.*` 플래그** | 설정만으로 | ⚠️ 키만 있고 쓰는 기능이 없다 |

즉 **현재 증명된 것은 3개**이고, `LlmClient` 의 두 구현(Claude/Fake)은 목록에 없는 **덤**이다. 나쁜 것은 아니지만 설계서가 요구한 5개를 대신하지 못한다.

이 Phase가 나머지 둘을 채운다:

- **지점 5**는 저절로 채워진다 — 기능 3개가 생기면 `ai.features.summary/preflight/leave-context` 플래그가 실제로 무언가를 켜고 끈다 (Task 9가 검증한다)
- **지점 4**는 `DatabasePromptRepository` 를 만들어 채운다 (Task 8). 설계서가 "(향후)"라고 적었지만, 프롬프트 테이블 하나와 매퍼 하나면 되고 **설계서 §6.4.4가 말한 "나중에 DB 관리 화면으로 승격할 때 구현체만 교체하면 된다"를 말이 아니라 코드로 보이는 것**이 이 프로젝트의 요지다

---

## 이 계획서가 전제하는 확정 사항

### D1. ★★ 평가셋은 실제 API를 쓴다. 그래서 기본 빌드에서 제외한다

설계서 §6.4.6의 **고정 평가셋 5건**이 이 Phase의 품질 장치다. 그런데 그것은 **진짜 LLM 응답을 평가**하는 것이므로 `FakeLlmClient` 로는 의미가 없다.

동시에 Phase 3 D3가 정한 **"API 키 없이 `mvnw clean verify` 가 통과한다"** 는 깨면 안 된다. 저장소를 public 으로 전환할 때 클론한 사람이 키 없이 빌드할 수 있어야 한다.

**확정: 평가셋을 JUnit 태그로 분리한다.**

```java
@Tag("llm")
class PreflightEvalSetIT { ... }
```

`pom.xml` 의 Failsafe 설정에 `<excludedGroups>llm</excludedGroups>` 를 넣는다. 기본 빌드는 이것을 건너뛴다.

수동 실행:

```powershell
$env:ANTHROPIC_API_KEY = '...'
.\mvnw.cmd verify "-Dit.test=PreflightEvalSetIT" "-Dgroups=llm" "-Dai.enabled=true"
```

**평가셋 실행 결과를 파일로 남긴다** — `docs/ai-eval-results.md` 에 실행 일자·모델·통과 건수를 기록한다. "AI 기능 품질을 어떻게 검증했나"에 대한 답이 저장소 안에 있어야 한다. 실행하지 않으면 그 답이 없다.

### D2. 구조화 출력을 여기서 붙인다 (Phase 3의 부채 A1)

Phase 3 D1이 "기능이 없으면 스키마도 없다"며 미룬 것이다. 이제 기능이 생긴다.

Java SDK 의 클래스 기반 오버로드를 쓴다 — 스키마를 손으로 쓰지 않고 POJO 에서 뽑아내고, 반환도 타입으로 받는다:

```java
StructuredMessageCreateParams<SummaryResult> params = MessageCreateParams.builder()
        .model(model)
        .maxTokens(4096L)
        .outputConfig(SummaryResult.class)     // ← 스키마 자동 생성 + 타입 반환
        .addUserMessage(prompt)
        .build();
```

**`LlmRequest` 에 `Class<?> outputType` 필드를 추가한다.** 데코레이터 4종은 `LlmRequest` 를 그대로 넘기므로 손댈 곳이 없다 — Phase 3 D1이 예측한 대로다.

**★ 구조화 출력과 인용의 제약:** 구조화 출력은 citations 와 함께 쓸 수 없고, 거절(`refusal`) 시에는 스키마를 지키지 않는다. `ClaudeLlmClient` 의 거절 검사(Phase 3 D6)가 그대로 유효하다.

### D3. ★ 캐시 키에 스키마를 넣는다 (Phase 3의 부채 A2 — 잊으면 조용히 틀린다)

Phase 3 D1이 명시적으로 남긴 부채다.

현재 키는 `SHA256(feature + ":" + promptVersion + ":" + input)` 이다. 구조화 출력이 생기면 **입력이 같아도 스키마가 바뀌면 결과 모양이 달라지는데**, 키가 같으므로 옛 모양을 돌려준다. 화면은 새 필드를 기대하는데 캐시는 옛 JSON 을 주고, 아무 예외 없이 `null` 이 뜬다.

**확정: 키에 스키마 이름을 넣는다.**

```
SHA256(feature + ":" + promptVersion + ":" + outputTypeName + ":" + input)
```

전체 스키마 해시가 아니라 **타입의 정규화된 클래스명**으로 충분하다 — POJO 필드가 바뀌면 컴파일 대상이 바뀌므로 프롬프트 버전을 함께 올리는 것이 정상 흐름이고, 클래스명은 그 흐름을 방해하지 않으면서 기능 간 충돌만 막는다. **왜 전체 해시가 아닌지 주석에 적는다.**

### D4. 사전 점검은 캐시하지 않는다 — 이미 정해져 있다

설계서 §6.4.3이 정했고 Phase 3의 `CachingLlmClient` 가 이미 `PREFLIGHT` 를 건너뛴다. **수정 후 재실행이 정상 동작**이기 때문이다. 캐시하면 사용자가 문서를 고치고 다시 점검해도 옛 지적이 나온다.

요약(`SUMMARY`)은 무기한 캐시하고, 연차 맥락(`LEAVE_CONTEXT`)은 1시간 캐시한다 — 팀 부재 현황이 변하기 때문이다.

**`LEAVE_CONTEXT` 의 1시간 TTL 은 아직 구현돼 있지 않다.** Phase 3의 캐싱은 무기한뿐이다. `ai_result_cache.created_at` 을 보고 만료를 판정하는 로직을 Task 7에서 넣는다.

### D5. `fetch()` 는 CSRF 헤더를 직접 붙인다 (로드맵 C3)

로드맵이 Phase 1부터 이월해 온 항목이다. 이제 발동한다.

`common.js` 의 `$.ajaxSetup` 은 jQuery 경로에만 적용된다. 사전 점검 모달은 상신 버튼을 눌렀을 때 서버를 기다려야 하므로 `fetch` 가 자연스러운데, **그 경로는 `ajaxSetup` 을 타지 않아 헤더가 안 붙고 조용히 403 이 된다.**

**확정: `common.js` 에 `flowmateFetch(url, options)` 래퍼를 만든다.** 개별 호출부에서 헤더를 손으로 붙이지 않는다 — 그러면 다음 사람이 반드시 빠뜨린다.

```js
// CSRF 토큰은 layout 의 meta 태그에서 읽는다. $.ajaxSetup 과 같은 출처를 쓴다.
function flowmateFetch(url, options) { ... }
```

### D6. 대량 시드는 SQL 이 만든다 — 애플리케이션 코드로 만들지 않는다

설계서 §9 Phase 5-2가 요구하는 것: **문서 200건 / 반려 40건(유형별 편중) / 근태 3개월.**

`generate_series` 로 SQL 안에서 만든다. 이유:

- 애플리케이션 시드 코드는 프로덕션 코드에 섞이고 나중에 지우기 애매해진다
- 200건을 서비스 계층으로 만들면 결재선 생성·상태 전이가 다 돌아 느리고, **정책이 바뀌면 시드도 바뀐다** (Phase 2·4에서 두 번 내린 판단과 같다)

**★ 반려 사유를 유형별로 편중시키는 것이 핵심이다.** 고르게 뿌리면 사전 점검이 "이 부서·이 유형에서 자주 나는 반려"를 집계해도 아무 신호가 없다. 설계서가 "유형별 편중"이라고 못박은 이유가 이것이다.

편중 예: 개발팀 PURCHASE 는 `MISSING_EVIDENCE` 가 절반, 마케팅팀 EXPENSE 는 `INSUFFICIENT_CONTENT` 가 절반.

**이 시드는 `50-seed-demo.sql` 로 따로 둔다.** 기존 `2x`/`4x` 시드와 섞지 않는다 — 데모용 대량 데이터와 기능 검증용 최소 데이터는 수명이 다르다.

### D7. 스키마 적용은 Phase 3이 정한 방식 그대로

`down -v` 를 쓰지 않는다. `IF NOT EXISTS` 로 쓰고, 새 환경은 init 이 자동 실행하고, 기존 환경은 `psql -f` 로 한 번 적용한다. **두 번 실행해 멱등성을 확인하는 단계를 반드시 거친다.**

대량 시드(`50-seed-demo.sql`)는 `ON CONFLICT DO NOTHING` 을 붙이고, **파일 맨 위 주석에 그 사실을 적는다.**

### D8. AI 실패가 화면을 깨뜨리지 않는다 — Phase 3의 폴백을 화면까지 잇는다

`LlmClient` 는 실패 시 `Optional.empty()` 를 돌려준다. 그것이 화면에서 어떻게 보이는지는 아직 정해지지 않았다.

**확정:**

| 기능 | 실패 시 화면 |
|---|---|
| 문서 요약 | 요약 영역에 "AI 요약을 일시적으로 사용할 수 없습니다" — **문서 본문은 정상 표시** |
| 사전 점검 | **모달을 띄우지 않고 바로 상신한다.** 점검 실패가 상신을 막으면 안 된다 |
| 연차 맥락 3a | 해당 없음 — LLM 을 쓰지 않는다 |

사전 점검의 결정이 특히 중요하다. 점검은 **보조 장치**이고, 그것 때문에 기안자가 상신을 못 하면 설계서 §6.4.3의 폴백 원칙("AI 실패가 업무 실패가 되어서는 안 된다")을 정면으로 어긴다.

---

## 파일 구조

```
src/main/java/com/flowmate/ai/
├─ domain/
│   ├─ SummaryResult.java         (summary: List<String>, keyFacts: Map)
│   ├─ PreflightResult.java       (verdict, findings: List<Finding>)
│   ├─ Finding.java               (severity, category, message, suggestion, basedOnRejectCount)
│   ├─ PreflightRecord.java       (ai_preflight_result 행)
│   └─ RejectPattern.java         (reasonCategory, count — 집계 결과)
├─ feature/
│   ├─ SummaryService.java        기능 1
│   ├─ PreflightService.java      기능 2 ★
│   └─ LeaveContextService.java   기능 3a (LLM 없음)
├─ prompt/
│   └─ DatabasePromptRepository.java   ★ 커스터마이징 지점 4의 두 번째 구현
├─ mapper/
│   ├─ PreflightResultMapper.java
│   └─ PromptMapper.java
└─ controller/
    └─ AiController.java          POST /api/ai/... (JSON)

src/main/resources/prompts/
├─ summary.v1.txt                 (Phase 3에서 만든 초안을 다듬는다)
└─ preflight.v1.txt               ★ 이 Phase의 핵심 산출물

src/test/java/com/flowmate/ai/
├─ feature/SummaryServiceIT.java
├─ feature/PreflightServiceIT.java        (FakeLlmClient — 배선 검증)
├─ feature/LeaveContextServiceIT.java
├─ prompt/DatabasePromptRepositoryIT.java
└─ eval/PreflightEvalSetIT.java           ★ @Tag("llm") — 실제 API

docker/postgres/init/
├─ 60-schema-ai-features.sql      ai_preflight_result, ai_prompt
└─ 50-seed-demo.sql               문서 200 · 반려 40(편중) · 근태 3개월

docs/ai-eval-results.md           ★ 평가셋 실행 기록
```

---

## Task 1: 스키마 2종과 대량 데모 시드

`60-schema-ai-features.sql` — `ai_preflight_result`(설계서 §5.5) + `ai_prompt`(지점 4용):

```sql
CREATE TABLE IF NOT EXISTS ai_prompt (
    feature    VARCHAR(30) NOT NULL,
    version    VARCHAR(20) NOT NULL,
    body       TEXT        NOT NULL,
    updated_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    PRIMARY KEY (feature, version)
);
```

`50-seed-demo.sql` — D6대로 `generate_series` 로 만든다. **반려 유형을 부서·문서유형별로 편중**시킨다.

검증: 편중이 실제로 생겼는지 집계로 확인한다.

```sql
SELECT dept_id, doc_type, reason_category, COUNT(*)
  FROM approval_reject_history GROUP BY 1,2,3 ORDER BY 4 DESC;
```

고르게 나오면 시드가 잘못된 것이다 — Task 5의 사전 점검이 아무 신호도 못 받는다.

---

## Task 2: 구조화 출력 배선 (Phase 3 부채 A1 · A2)

- `LlmRequest` 에 `outputType` 추가
- `ClaudeLlmClient` 가 `outputType` 이 있으면 `outputConfig(Class)` 를 쓴다
- **`CachingLlmClient` 의 키에 `outputType` 이름을 넣는다 (D3)**
- `FakeLlmClient` 는 `outputType` 에 맞는 고정 JSON 을 돌려주게 확장

**회귀 테스트:** 같은 입력·같은 프롬프트 버전인데 `outputType` 만 다르면 **캐시가 미스**하는 것을 단정한다. 이것이 A2 부채를 갚았다는 증거다.

---

## Task 3: 기능 1 — 문서 요약

`POST /api/ai/approvals/{id}/summary` → `SummaryResult`.

- 문서 상세 화면에서 비동기로 부른다 (**`flowmateFetch` — D5**)
- 실패하면 요약 영역만 안내 문구, **본문은 그대로** (D8)
- 권한: 그 문서를 볼 수 있는 사람만. `ApprovalQueryService.findDoc(id, viewerId)` 를 그대로 태운다

**완료 기준(설계서 §9 5-1): 같은 문서를 2회 조회하면 2번째가 캐시에서 나온다.** `ai_result_cache.hit_count` 가 1 이 되는 것으로 확인한다.

---

## Task 4: 기능 3a — 연차 맥락 표시 (LLM 없음)

설계서 §6.4.7이 "이것만으로 두 모듈을 통합했다는 주장이 성립한다"고 적은 부분이다. **LLM 을 쓰지 않으므로 API 키 없이도 완전히 동작한다.**

연차 신청서 결재 화면에 표시:

- 신청자 · 부서 · 직급
- 신청일 · 일수
- 연차 현황 (부여 / 사용 / 잔여 / 소진율)
- **해당일 팀 부재 인원 · 팀 가동률**
- 최근 3개월 지각 · 연장 · 결근

`LeaveContextService` 가 `attendance` 모듈의 Service 인터페이스를 통해 조회한다 — Phase 4 D1의 모듈 경계를 그대로 지킨다.

3b(LLM 판단 코멘트)는 **설계서 §9.1이 지정한 잘라내기 1순위**다. 일정이 밀리면 여기를 버린다.

---

## Task 5: ★★ 기능 2 — 사전 점검 (이 Phase의 핵심, 2.0일)

### 흐름 (설계서 §6.4.6)

1. 기안자가 [상신] 클릭
2. 서버:
   - 같은 `doc_type` + `dept_id` 의 최근 반려 이력 10건 (없으면 전사로 확대)
   - `reason_category` 별 빈도 집계
   - [현재 문서 요지] + [과거 반려 패턴 + 빈도] 프롬프트 조립
   - 구조화 출력으로 `findings` 수신
3. 모달: PASS → 바로 상신 / WARN → 항목 + [수정하러 가기] / [무시하고 상신]
4. '무시하고 상신' 시 `ai_preflight_result.ignored_yn = 'Y'`

### `basedOnRejectCount` 가 설계의 핵심이다

설계서가 못박았다 — **AI 가 훈계하는 것이 아니라 "과거 반려 3건에 근거함"을 숫자로 제시한다.** 근거 없는 조언은 사용자가 두 번째부터 무시한다.

그러므로 **집계 결과를 프롬프트에 넣고, 모델이 그 숫자를 인용하게 하고, 화면에 그 숫자를 표시한다.** 셋 중 하나라도 빠지면 이 기능의 존재 이유가 사라진다.

### 프롬프트가 실패하는 4지점 (설계서 §6.4.6)

| 문제 | 프롬프트에 넣을 문장 |
|---|---|
| 뻔한 조언 | "제시된 과거 반려 사유와 직접 관련된 지점만 지적. 일반적 문서 작성 조언 금지." |
| 억지 지적 | "지적할 것이 없으면 findings 를 빈 배열로 반환하라. 찾아내는 것이 목적이 아니다." |
| 추측 | "본문에서 인용 가능한 근거가 있을 때만 지적하라." |
| 개인정보 유입 | 마스킹 계층 통과 + **반려 사유는 요약 형태로만 주입** |

마지막 항목은 코드로 강제한다 — 반려 사유 원문(`reason_text`)을 프롬프트에 넣지 않고 **`reason_category` 와 빈도만** 넣는다. 원문에는 사람 이름과 금액이 들어 있다.

### 실패해도 상신은 된다 (D8)

점검이 `Optional.empty()` 를 돌려주면 **모달 없이 바로 상신**한다. 화면 스크립트도 서버 오류·타임아웃 시 같은 경로를 탄다.

### 배선 검증 통합 테스트 (FakeLlmClient — 키 불필요)

- 반려 이력이 있는 부서·유형 → 집계가 프롬프트에 들어간다
- 이력이 없으면 전사로 확대한다
- **프롬프트에 `reason_text` 원문이 들어가지 않는다** ★
- `verdict=WARN` 이면 `ai_preflight_result` 에 기록된다
- '무시하고 상신' → `ignored_yn='Y'`
- **AI 실패 → 모달 없이 상신 성공** ★

---

## Task 6: 사전 점검 모달 화면과 `flowmateFetch`

- `common.js` 에 `flowmateFetch` 추가 (D5) — CSRF 헤더를 래퍼가 붙인다
- `preflight-modal.jsp` — findings 목록 + `basedOnRejectCount` 표시 + 두 버튼
- 상신 버튼 흐름 변경: 즉시 POST → 점검 호출 → 결과에 따라 분기

**클래스 이름만 `style.css` 목록에 추가하고 CSS 규칙은 쓰지 않는다** (Phase 6).

---

## Task 7: 캐시 TTL 과 기능 플래그 (지점 5)

- `ai_result_cache` 에 TTL 판정 추가 — `LEAVE_CONTEXT` 는 1시간 (D4)
- `ai.features.summary/preflight/leave-context` 플래그가 **실제로 기능을 끈다**
- 플래그가 꺼진 기능은 화면에서 해당 영역이 아예 안 보인다 (오류가 아니라 부재)

**통합 테스트:** 플래그를 끄면 해당 기능이 `LlmClient` 를 부르지 않는 것을 단정한다. 이것이 **커스터마이징 지점 5의 증명**이다.

---

## Task 8: `DatabasePromptRepository` (커스터마이징 지점 4 완성)

`ai_prompt` 테이블에서 프롬프트를 읽는 구현. 설정으로 교체한다:

```yaml
ai:
  prompt-repository: file      # file | database
```

**교체 증명 테스트:** 같은 `(feature, version)` 에 대해 파일과 DB 에 **다른 내용**을 넣고, 설정에 따라 다른 문구가 나오는 것을 단정한다. Phase 2·4와 같은 형태다.

이것으로 설계서 §7의 5개 지점이 전부 구현 2개씩을 갖는다.

---

## Task 9: 평가셋 5건 (D1)

`@Tag("llm")` 으로 분리하고 기본 빌드에서 제외한다.

| # | 입력 | 기대 |
|---|---|---|
| 1 | 목적이 '업무협의'뿐인 출장비 | 목적불명확 (HIGH) |
| 2 | 첨부 없는 출장비 정산 | 증빙누락 |
| 3 | 부서 평균 4배 금액의 구매 | 금액과다 |
| 4 | **잘 작성된 출장비** | **PASS** |
| 5 | **잘 작성된 구매 요청** | **PASS** |

**4·5번이 가장 중요하다** — 억지 지적을 잡는 유일한 장치이고, "AI 품질을 어떻게 검증했나"에 대한 답이다.

실행 결과를 `docs/ai-eval-results.md` 에 기록한다: 실행 일자 · 모델 · 5건 중 통과 · 실패한 항목의 실제 출력.

**통과하지 못하면 프롬프트를 고치고 다시 돌린다.** 평가셋을 통과시키려고 기대값을 낮추지 않는다 — 그러면 장치가 아니라 장식이 된다.

---

## Task 10: 마감

- [ ] `mvnw clean verify` — 실제 숫자 기록. **`ANTHROPIC_API_KEY` 미설정 상태에서 통과** (D1)
- [ ] 평가셋 수동 실행 결과가 `docs/ai-eval-results.md` 에 있는가
- [ ] 시드 무결 + 데모 시드가 의도한 편중을 갖는가
- [ ] `docs/oracle-mapping.md` — `generate_series` 대응 (Oracle `CONNECT BY LEVEL`)
- [ ] `README.md` — Phase 5 체크, 테스트 수, **커스터마이징 지점 5개 전부 구현 2개** 표 갱신
- [ ] 로드맵 §6 갱신, **C3(fetch CSRF) 해소 표시**
- [ ] merge → tag `phase-5-ai-features` → push

### Phase 6으로 넘기는 것

| # | 항목 |
|---|---|
| C1 | CSS 전면 작업 (지금까지 클래스 이름만 쌓아 왔다) |
| C2 | `docker compose up` 한 번으로 전체 기동 |
| C3 | 컨테이너 JVM `-Dfile.encoding=UTF-8` (로드맵이 경고한 항목) |
| C4 | DB 자격증명 `${DB_PASSWORD:flowmate}` 로 변경 |
| C5 | **비밀값 재검 후 public 전환** — `git log -p -S "sk-ant"` |
| C6 | 기능 3b (LLM 판단 코멘트) — 여유가 있으면 |

---

## 계획 이후 변경 — `LlmClient` 세 번째 구현으로 Gemini 추가 (계획 외)

이 계획서 어디에도 없던 작업이다. Task 10 마감 이후, `feat/phase-5-ai-features` 브랜치 위에서
별도로 진행했다. 왜 계획에 없던 일을 했는지, 계획서의 원칙과 어떻게 맞물리는지 여기 적는다.

**무엇을 왜:**

- `LlmClient` 구현을 `ClaudeLlmClient`/`FakeLlmClient` 둘에서 `GeminiLlmClient` 를 더해 셋으로
  늘렸다. `LlmConfig` 의 `ai.provider`(claude|gemini) 설정으로 고른다 — `ai.enabled=true` 일 때만
  의미가 있다는 점은 그대로다.
- **비용.** Claude(Anthropic)는 유료뿐이다. Gemini 는 무료 등급이 있어 API 키 없이도 가입만으로
  실제 호출을 시연할 수 있다 — 포트폴리오로 재현해 보려는 사람에게 진입 장벽이 사라진다.
  그래서 `application.yml` 의 기본 provider 를 `gemini` 로 뒀다(모델은 `gemini-2.0-flash`).
  `claude-opus-5` 로 되돌리는 방법은 주석으로 남겨 뒀다(`AiProperties`/`application.yml`).
- **교체 증명이 더 세진다.** ★ 이 문서 위쪽 "먼저 정정한다" 절이 이미 짚었듯, `LlmClient` 의
  두 구현(Claude/Fake)은 설계서 §7의 5개 지점에 안 들어가는 **덤**이다 — 하나가 테스트 대역이라
  "제공자를 갈아 끼울 수 있다"는 주장의 증거로는 약하다. 서로 다른 두 회사(Anthropic/Google)의
  실제 API 를 같은 인터페이스 뒤에 세우면 그 주장이 실제로 증명된다.

**설계서와의 관계 — 게이트웨이는 그대로다:**

설계서 §6.4.1 이 정한 데코레이터 체인(Caching → Masking → Logging → Resilient)은 Anthropic 을
전제로 쓰였지만, 코드 자체는 `LlmClient` 인터페이스에만 의존하고 어느 공급자인지 모른다.
Gemini 를 추가하면서 그 체인 코드는 한 줄도 바꾸지 않았다 — 늘어난 것은 `GeminiLlmClient`
구현 파일 하나와 `LlmConfig` 의 `@ConditionalOnProperty` 가지 하나뿐이다. "설계서가 전제한
제공자를 실제로 바꿔 봐도 게이트웨이가 무사하다"는 것 자체가 이 프로젝트가 원래 증명하려던
것(§6.4.1 의 데코레이터 경계)의 연장선이다.

**D3(claudeLlmClient 의 키 부재 기동 실패)를 Gemini 에도 그대로 적용한다.** `GeminiLlmClient`
를 감싸는 `Client` 도 `GEMINI_API_KEY` 가 없다고 예외를 던지지 않고 만들어지므로, 검사 없이
두면 키 없는 배포가 정상 기동하고 첫 호출의 인증 실패가 `ResilientLlmClient` 에 흡수돼
"AI 를 일시적으로 쓸 수 없습니다"로 보인다 — 설정 실수와 일시적 장애가 구별되지 않는
바로 그 문제다. `LlmConfig.geminiLlmClient()` 가 기동 시점에 크게 실패시킨다.

**D1(키 없이 `mvnw clean verify` 통과)도 그대로 지킨다.** Gemini 실제 호출 테스트는 추가하지
않았다 — Task 9 평가셋과 같은 이유로, 실제 API 를 부르는 검증은 별도의 수동 실행 대상이다.
새 테스트(`LlmConfigTest`)는 `ApplicationContextRunner` 로 조건부 배선만 확인하고, 두 공급자
모두 "키가 없으면 의도한 메시지로 기동 실패"를 실제 Spring 컨텍스트로 단정한다 - 키를
쓰지 않고도 배선이 옳다는 것을 증명한다.

---

## 부록 — 설계서 §9 Phase 5 대응 확인

| 설계서 요구 | 이 계획서 | Task |
|---|---|---|
| 5-1 기능 1 요약 + 캐시 동작 | ✅ | 3 |
| 5-2 시드 200/40/3개월 | ✅ 편중 검증 포함 | 1 |
| 5-3 기능 2 사전점검 + 평가셋 5건 + 모달 | ✅ | 5·6·9 |
| 5-4 기능 3a 근태 결합 표시 | ✅ | 4 |
| 완료 기준: 2번째가 캐시에서 | ✅ `hit_count` | 3 |
| 완료 기준: 문제 3건 지적, 정상 2건 통과 | ✅ | 9 |
| (추가) 커스터마이징 지점 4·5 완성 | ✅ | 7·8 |
