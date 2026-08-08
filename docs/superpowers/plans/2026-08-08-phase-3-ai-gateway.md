# Phase 3 실행 계획 — AI 게이트웨이

> 이 계획서는 설계서 §6.4(AI 계층)와 §5.5(AI 스키마), §9 Phase 3을 실행 단위로 옮긴 것이다.
> 로드맵: [2026-08-05-flowmate-roadmap.md](2026-08-05-flowmate-roadmap.md) — §3 규약이 이 계획서의 전제다.
> 원본 설계서: [2026-08-05-flowmate-design.md](../specs/2026-08-05-flowmate-design.md)
> 작성일: 2026-08-08 · 분량: 1.0일 · 태그: `phase-3-ai-gateway`

---

## 시작 상태

`main` 이 `phase-2-approval-core` 태그 상태다.

- 단위 **52** · 통합 **55** · BUILD SUCCESS
- `flowmate-postgres` 컨테이너 정상, 시드 `depts=7 emps=20 docs=6 lines=8 hist=15 rejects=1 attach=0`
- 전자결재 전 과정이 화면에서 동작: 기안 → 상신 → 승인 → 완료, 반려 유형 저장, 내 결재함 4탭, 문서 상세, 첨부파일
- `com.flowmate.approval` 패키지가 완성되어 있고, `com.flowmate.attendance` 는 아직 없다

## 이 Phase가 끝나면 무엇이 동작하는가

**화면은 하나도 늘지 않는다.** 설계서 §9가 Phase 3을 "화면 없음"으로 못박은 대로다.

늘어나는 것은 이것이다.

- `LlmClient` 인터페이스와 데코레이터 5단 체인이 조립되고, `FakeLlmClient` 로 체인 전체가 통합 테스트를 통과한다
- `SensitiveDataMasker` 가 주민번호·계좌·전화·사업자번호·카드·이메일 6종을 치환하고, **마스킹된 문자열만 바깥으로 나간다는 사실이 체인 수준에서 단정된다**
- API 키 없이도 `mvnw clean verify` 가 통과한다 (D3)
- `ai.enabled` 설정 한 줄로 가짜 구현 ↔ 실제 Claude 호출이 교체된다 — **커스터마이징 지점 4번의 증명**

## 왜 이 Phase를 지금 하는가

설계서 §9.1은 Phase 3을 Phase 2와 병행할 수 있는 **피난처**로 지정했다. Phase 2가 막힘 없이 끝났으므로 피난처로 쓸 일은 없었지만, 순서 자체는 여전히 옳다.

Phase 5(AI 기능)는 4일짜리 가장 큰 덩어리이고, 그 4일은 **프롬프트를 다듬고 평가셋을 맞추는 데** 써야 한다. 그때 가서 "SDK 호출이 안 된다", "마스킹이 새는 것 같다", "캐시 키가 틀렸다"를 디버깅하면 4일이 6일이 된다. 배관을 미리 깔고 Phase 5는 내용물만 채우는 구조로 간다.

---

## 이 계획서가 전제하는 확정 사항

설계서가 정하지 않았거나, 설계서 작성 이후 API가 바뀌어 다시 정해야 하는 것들이다. 이유까지 적는다 — 나중에 이 결정을 뒤집을 때 무엇을 다시 따져야 하는지 알기 위해서다.

### D1. `LlmClient` 는 원문 텍스트를 주고받는다. 구조화 출력은 Phase 5로 미룬다

```java
Optional<LlmResponse> complete(LlmRequest request);
```

설계서 §6.4.5는 문서 요약에 `output_config.format`(구조화 출력)으로 JSON 스키마를 강제하라고 했다. 맞는 말이지만 **그건 기능의 요구사항이지 게이트웨이의 요구사항이 아니다.**

스키마를 지금 인터페이스에 넣으면:
- 데코레이터 4개가 전부 스키마를 알아야 할 이유가 없는데 시그니처를 통과시켜야 하고
- Phase 3에는 그 필드를 쓰는 기능이 하나도 없으므로 **테스트되지 않는 죽은 코드**가 된다

넣지 않으면 Phase 5에서 무엇을 고쳐야 하나? **`LlmRequest` 와 `ClaudeLlmClient` 둘뿐이다.** 데코레이터는 `LlmRequest` 를 그대로 넘기므로 필드가 늘어도 손댈 곳이 없다. YAGNI가 이기는 구조라서 미룬다.

**단, Phase 5로 넘길 부채 하나를 여기 적어둔다:** 스키마가 생기면 `cache_key` 에 스키마 해시도 들어가야 한다. 입력이 같아도 스키마가 바뀌면 결과 모양이 달라지는데, 지금 키는 `feature:promptVersion:input` 뿐이라 옛 모양을 돌려준다. 프롬프트 버전을 올리는 것으로 우회할 수 있지만 **잊으면 조용히 틀린다.**

### D2. `LlmResponse` 는 토큰 수와 모델명을 함께 나른다

```java
public class LlmResponse {
    private String text;
    private int inputTokens;
    private int outputTokens;
    private String model;
}
```

`LoggingLlmClient` 가 `ai_call_log.input_tokens` 를 기록하고 `CachingLlmClient` 가 `ai_result_cache.model` 을 저장해야 하는데, 그 값은 **체인 맨 안쪽의 `ClaudeLlmClient` 만 안다.** 응답이 문자열뿐이면 바깥 데코레이터가 알 방법이 없다.

`Optional` 로 감싸는 이유는 설계서 §6.4.3의 폴백 원칙이다 — AI 실패는 예외가 아니라 빈 값이다.

### D3. ★ API 키 없이 전체 빌드가 통과해야 한다

**`mvnw clean verify` 가 `ANTHROPIC_API_KEY` 를 요구하면 안 된다.**

이유가 셋이다.

1. **저장소가 public 으로 전환된다.** 클론한 사람이 키 없이 빌드할 수 있어야 한다. 못 하면 포트폴리오로서 죽은 저장소다.
2. **테스트가 과금된다면 테스트를 안 돌리게 된다.** 실제 API를 때리는 테스트는 느리고 비결정적이고 돈이 든다.
3. **키가 없을 때 앱이 뜨지 않으면 Phase 4·5의 다른 작업까지 막힌다.**

배선:

```yaml
ai:
  enabled: false          # 기본값 — 키 없이 뜬다
  model: claude-opus-5
  timeout-seconds: 30
```

`ai.enabled=false` → `FakeLlmClient`, `true` → `ClaudeLlmClient`. Phase 2의 `ApprovalPolicyConfig` 와 같은 `@ConditionalOnProperty` 패턴이다.

키는 **환경변수 `ANTHROPIC_API_KEY` 로만** 받는다. `application-local.yml` 은 이미 `.gitignore` 에 있지만 거기에도 넣지 않는다 — 파일에 있으면 언젠가 스크린샷이나 붙여넣기로 샌다. SDK의 `AnthropicOkHttpClient.fromEnv()` 가 환경변수를 직접 읽으므로 코드에 키 문자열이 등장할 일 자체가 없다.

### D4. 두 번째 구현은 `FakeLlmClient` 다 — 교체 증명을 겸한다

설계서 §7은 커스터마이징 지점 5개를 나열하고 각각 **두 개 이상 구현하라**고 했다. AI 계층이 4번이다.

Phase 2에서는 `DefaultApprovalLinePolicy` / `SimpleTwoStepLinePolicy` 두 개를 일부러 만들었다. Phase 3에서는 **테스트에 어차피 필요한 `FakeLlmClient` 가 그 두 번째 구현 역할을 그대로 한다.** 억지로 세 번째를 만들지 않는다 — 그건 증명이 아니라 장식이다.

교체 증명 테스트는 Phase 2와 같은 형태로 쓴다: 같은 `LlmRequest` 를 두 구현에 넣어 다른 결과가 나오는 것을 단정한다.

### D5. ★ 새 스키마는 `down -v` 없이 적용한다

**이것이 이 Phase에서 가장 중요한 운영 결정이다.**

`docker/postgres/init/*.sql` 은 **빈 볼륨에서만** 실행된다. Phase 2 Task 1은 이 때문에 `down -v` 를 한 번 썼고, 그것이 이 프로젝트에 허용된 유일한 사용이었다.

지금 `30-schema-ai.sql` 을 추가하면 같은 문제가 반복된다. 그러나 이번에는 `down -v` 를 쓰지 않는다. 이유:

- Phase 2 이후로 **손으로 만든 문서와 첨부파일이 컨테이너에 남아 있을 수 있다.** 시드 스크립트가 복구해주지 않는다.
- Phase 4·5도 각각 새 스키마를 추가한다. `down -v` 를 관례로 삼으면 **Phase마다 데이터를 잃는다.**

**확정 방식 — 파일은 두 경로 모두에서 쓰인다:**

| 경로 | 실행 시점 | 명령 |
|---|---|---|
| 새 환경 (클론 후 최초) | `docker compose up` 이 자동 실행 | init 스크립트로 자동 |
| 기존 환경 (지금 우리) | 손으로 한 번 | `docker exec -i flowmate-postgres psql -U flowmate -d flowmate -f /docker-entrypoint-initdb.d/30-schema-ai.sql` |

**따라서 모든 신규 스키마 파일은 `CREATE TABLE IF NOT EXISTS` / `CREATE INDEX IF NOT EXISTS` 로 쓴다.** 두 경로에서 모두 안전해야 하기 때문이다. Phase 2까지의 파일은 `IF NOT EXISTS` 가 없지만 고치지 않는다 — 이미 적용됐고, 새 환경에서는 어차피 한 번만 돈다.

컨테이너 안의 `/docker-entrypoint-initdb.d/` 는 `docker-compose.yml` 이 `docker/postgres/init` 를 마운트한 경로다. 파일을 만들면 컨테이너에서 즉시 보인다 — 복사할 필요가 없다.

### D6. Claude Opus 5 의 현재 API 제약 — 설계서 작성 시점과 달라진 것들

설계서는 `ai.model: claude-opus-5` 를 적었지만 그 모델의 **요청 규약**은 적지 않았다. 지금 확정한다.

| 항목 | 결정 | 이유 |
|---|---|---|
| `temperature` / `top_p` / `top_k` | **넣지 않는다** | Opus 5 에서 제거됐다. 보내면 400 이다 |
| `thinking` | **설정하지 않는다** (기본값 = adaptive) | Opus 5 는 생략하면 thinking 이 켜진다. 끄는 것은 문서화된 실패 모드 2종(도구 호출이 평문으로 새는 것, `<thinking>` 태그 누출)이 있어 권장되지 않는다 |
| `output_config.effort` | **`LOW`** | 요약·사전점검은 깊은 추론이 필요한 작업이 아니다. Opus 5 는 낮은 effort 에서도 이전 모델의 높은 effort 를 넘는 경우가 많다 |
| `max_tokens` | **4096** | ★ Opus 5 의 `max_tokens` 는 **thinking + 응답 텍스트를 합쳐서** 제한한다. 3줄 요약이라고 512 를 주면 thinking 이 다 먹고 응답이 잘린다 |
| 스트리밍 | 쓰지 않는다 | `max_tokens` 가 4096 이라 HTTP 타임아웃 위험 구간(~16000)에 한참 못 미친다 |
| `stop_reason: "refusal"` | **반드시 먼저 검사한다** | Opus 5 는 안전 분류기가 요청을 거절할 수 있다. HTTP 200 에 빈 `content` 가 온다. `content` 를 먼저 읽는 코드는 그 자리에서 깨진다 |
| 서버측 `fallbacks` | **쓰지 않는다** | 베타 헤더가 필요하고, 우리는 이미 "실패 시 `Optional.empty()`" 라는 더 강한 폴백을 갖고 있다. 한국어 그룹웨어 요약이 거절될 확률은 낮고, 거절돼도 업무는 안 멈춘다 |

**SDK 좌표:** `com.anthropic:anthropic-java:2.34.0`

**시그니처가 기억과 다를 때:** WebFetch 로 조사하지 말고 **컴파일러에게 물어본다.** 계획서에 적힌 대로 파일을 먼저 쓰고 `mvnw test-compile` 을 돌려 `cannot find symbol` 이 가리키는 곳을 고친다. 정적 타입 SDK에서는 이쪽이 훨씬 빠르다. 필요하면 `javap -classpath <jar> com.anthropic.models.messages.Message` 로 멤버를 확인한다.

### D7. `ResilientLlmClient` 는 진짜 타임아웃을 건다

설계서의 생성자 시그니처가 `new ResilientLlmClient(claude, Duration.ofSeconds(30))` 이다. Duration 을 받았으면 써야 한다.

SDK 클라이언트에 타임아웃을 설정하는 것(`AnthropicOkHttpClient.builder().timeout(...)`)만으로는 부족하다. 그건 HTTP 계층만 막는다. 데코레이터의 계약은 **"무슨 일이 있어도 30초 안에 돌아온다"** 이므로 `ExecutorService` + `Future.get(timeout)` 로 건다.

15줄이면 되고, **테스트할 수 있다** — `FakeLlmClient` 에게 잠들라고 시키면 타임아웃이 실제로 발동하는지 단정할 수 있다. SDK 설정만 하면 그 단정을 쓸 수 없다.

### D8. 마스킹은 체인 수준에서 검증한다

`SensitiveDataMasker` 단위 테스트는 **정규식이 맞다는 것만** 증명한다. 설계서가 내건 주장은 그게 아니라 **"어떤 경로로 들어와도 원문이 외부로 나가지 않는다"** 이다.

그걸 증명하려면 체인을 통째로 조립해 놓고, **맨 안쪽 `FakeLlmClient` 가 받은 문자열에 원문이 없다는 것**을 단정해야 한다. Task 6의 핵심 테스트가 이것이다.

**정정 (실측 결과) — 원래 여기에 "순서가 뒤집히면 캐시에 원문이 저장된다"고 적었는데 틀렸다.**

`ai_result_cache` 의 실제 컬럼은 `cache_key, feature, prompt_version, result_json, model, input_tokens, output_tokens, hit_count, created_at` 이다. **프롬프트를 담는 컬럼이 없다.** 키는 SHA-256 해시이고 저장되는 것은 응답(`result_json`)뿐이다. 그러므로 마스킹과 캐싱의 순서를 뒤집어도 캐시에 원문이 들어가지 않는다.

순서가 실제로 바꾸는 것은 **비용**이다. 캐싱이 바깥이면 히트 시 마스킹도 API 호출도 건너뛴다(설계서가 말한 "비용 0"). 마스킹이 바깥이면 히트해도 마스킹 CPU 를 쓴다 — 사소하다.

**그런데 "캐시에 원문이 없어야 한다"는 요구 자체는 여전히 진짜다.** 이유가 다르다: **실제 LLM 응답은 입력을 인용할 수 있다.** 요약문에 계좌번호가 그대로 들어오는 것은 정상 동작이다. 마스킹이 실제 호출보다 **안쪽**(즉 호출 뒤)에 있거나 아예 빠지면, 응답 자체가 원문을 실어오고 그 응답이 캐시와 로그에 저장된다.

그래서 검증해야 하는 것은 순서가 아니라 **"실제 호출에 도달하는 프롬프트가 이미 마스킹돼 있다"** 이고, 그 결과로 **"응답에서 파생된 어떤 저장물에도 원문이 없다"** 이다. `FakeLlmClient` 가 받은 프롬프트를 응답에 되비추게 만들면 이 성질을 기계적으로 단정할 수 있다.

---

## 파일 구조

```
src/main/java/com/flowmate/ai/
├─ domain/
│   ├─ LlmRequest.java            (feature, promptVersion, prompt, empId, approvalId)
│   ├─ LlmResponse.java           (text, inputTokens, outputTokens, model)
│   ├─ AiFeature.java             (SUMMARY / PREFLIGHT / LEAVE_CONTEXT 상수)
│   ├─ AiResultCache.java
│   └─ AiCallLog.java
├─ client/
│   ├─ LlmClient.java             ★ 인터페이스 (커스터마이징 지점 4)
│   ├─ ClaudeLlmClient.java       ★ 실제 SDK 호출
│   ├─ FakeLlmClient.java         ★ 두 번째 구현 (D4)
│   ├─ CachingLlmClient.java      데코레이터 1 (가장 바깥)
│   ├─ MaskingLlmClient.java      데코레이터 2
│   ├─ LoggingLlmClient.java      데코레이터 3
│   └─ ResilientLlmClient.java    데코레이터 4 (가장 안쪽)
├─ mask/
│   └─ SensitiveDataMasker.java   ★ 단위 14건
├─ prompt/
│   ├─ PromptRepository.java      인터페이스
│   └─ FilePromptRepository.java  classpath:prompts/ 조회
└─ mapper/
    ├─ AiResultCacheMapper.java
    └─ AiCallLogMapper.java

src/main/java/com/flowmate/config/
└─ LlmConfig.java                 ★ 체인 조립 + enabled 분기

src/main/resources/
├─ mapper/ai/AiResultCacheMapper.xml
├─ mapper/ai/AiCallLogMapper.xml
└─ prompts/
    └─ summary.v1.txt             (Phase 5가 쓸 자리 — 지금은 배선 검증용)

src/test/java/com/flowmate/ai/
├─ mask/SensitiveDataMaskerTest.java        (단위 14) ★
├─ client/ResilientLlmClientTest.java       (단위 4)
├─ client/CachingLlmClientTest.java         (단위 4)
├─ client/MaskingLlmClientTest.java         (단위 3)
├─ prompt/FilePromptRepositoryTest.java     (단위 4)
└─ client/LlmChainIT.java                   (통합 6) ★

docker/postgres/init/
└─ 30-schema-ai.sql               (IF NOT EXISTS — D5)
```

**예상 테스트 수:** 단위 52 → **81** (+29), 통합 55 → **61** (+6).
설계서 §9 Phase 3 의 "마스킹 단위 테스트 12건" 목표를 14건으로 넘긴다.

---

## Task 1: AI 스키마 2종과 무중단 적용

**목표:** `ai_result_cache` · `ai_call_log` 을 **데이터를 잃지 않고** 추가한다.

`ai_preflight_result` 는 만들지 않는다 — 사전점검 기능이 없는데 테이블만 있으면 Phase 5가 그 모양이 맞는지 검증할 수 없다. Phase 5에서 기능과 함께 만든다.

### Step 1. `docker/postgres/init/30-schema-ai.sql`

```sql
-- AI 게이트웨이 스키마 (설계서 §5.5)
--
-- ★ 이 파일은 두 경로에서 실행된다 (계획서 3 D5):
--   1) 새 환경: docker compose up 이 빈 볼륨에서 자동 실행
--   2) 기존 환경: docker exec ... psql -f 로 손으로 한 번
-- 그래서 IF NOT EXISTS 가 필수다. 없으면 2번 경로에서 에러가 난다.

CREATE TABLE IF NOT EXISTS ai_result_cache (
    cache_key      VARCHAR(64) PRIMARY KEY,
    feature        VARCHAR(30) NOT NULL,
    prompt_version VARCHAR(20) NOT NULL,
    result_json    TEXT        NOT NULL,
    model          VARCHAR(50) NOT NULL,
    input_tokens   INT,
    output_tokens  INT,
    hit_count      INT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ai_result_cache            IS 'AI 호출 결과 캐시';
COMMENT ON COLUMN ai_result_cache.cache_key  IS 'SHA-256(feature:promptVersion:input) 의 hex';
COMMENT ON COLUMN ai_result_cache.hit_count  IS '캐시가 실제로 쓰였는지 측정 — 0 이 많으면 캐싱이 무의미하다는 신호';

CREATE TABLE IF NOT EXISTS ai_call_log (
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
    called_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE  ai_call_log             IS 'AI 호출 로그 — 성공/실패 무관하게 전부 남긴다';
COMMENT ON COLUMN ai_call_log.success_yn  IS 'Y/N. 실패도 기록해야 폴백이 얼마나 발동하는지 알 수 있다';
COMMENT ON COLUMN ai_call_log.error_msg   IS '실패 사유. 500자로 자른다 — 스택트레이스를 통째로 넣지 않는다';

-- emp_id / approval_id 에 FK 를 걸지 않는 이유:
-- 로그는 원본이 지워져도 남아야 한다. FK 를 걸면 사원 삭제가 로그 삭제를 강요한다.
CREATE INDEX IF NOT EXISTS idx_ai_log_feature  ON ai_call_log (feature, called_at DESC);
CREATE INDEX IF NOT EXISTS idx_ai_log_approval ON ai_call_log (approval_id);
CREATE INDEX IF NOT EXISTS idx_ai_cache_feature ON ai_result_cache (feature, prompt_version);
```

### Step 2. 실행 중인 컨테이너에 적용

```powershell
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
docker exec -i flowmate-postgres psql -U flowmate -d flowmate -f /docker-entrypoint-initdb.d/30-schema-ai.sql
```

`docker compose down -v` 를 **쓰지 않는다.** 이 명령이 D5가 정한 방식이다.

### Step 3. 두 번 돌려도 안전한지 확인 (멱등성)

같은 명령을 한 번 더 실행한다. `NOTICE: relation "ai_result_cache" already exists, skipping` 만 나오고 에러가 없어야 한다.

**이 검증을 건너뛰지 않는다.** 멱등하지 않으면 새 환경에서 init 이 실패하고, 실패한 init 은 컨테이너를 죽이지 않으므로 **테이블 없이 healthy 한 컨테이너**가 만들어진다.

### Step 4. 기존 데이터 무사한지 확인

```powershell
docker exec -i flowmate-postgres psql -U flowmate -d flowmate -t -A -c "SELECT 'depts='||(SELECT COUNT(*) FROM department)||' emps='||(SELECT COUNT(*) FROM employee)||' docs='||(SELECT COUNT(*) FROM approval_doc)||' cache='||(SELECT COUNT(*) FROM ai_result_cache)||' log='||(SELECT COUNT(*) FROM ai_call_log);"
```

기대: `depts=7 emps=20 docs=6 cache=0 log=0`

**`docs=6` 을 반드시 확인한다.** 이것이 "데이터를 잃지 않았다"의 증거다.

### Step 5. 커밋

```
feat: AI 게이트웨이 스키마 2종

ai_result_cache, ai_call_log 을 추가한다.

down -v 를 쓰지 않고 실행 중인 컨테이너에 psql -f 로 적용했다.
새 환경에서는 init 스크립트로 자동 실행된다. 두 경로 모두에서
안전하도록 IF NOT EXISTS 로 작성했다.

ai_preflight_result 는 만들지 않았다. 사전점검 기능이 있는
Phase 5 에서 기능과 함께 만든다.
```

---

## Task 2: `SensitiveDataMasker` (TDD) ★

**목표:** 설계서 §6.4.2의 6종 패턴을 구현하고, **오탐 허용·미탐 불허**라는 비대칭을 테스트로 고정한다.

이 Phase에서 가장 중요한 클래스다. 여기가 새면 개인정보 유출이다.

### Step 1. 실패하는 테스트 먼저

`src/test/java/com/flowmate/ai/mask/SensitiveDataMaskerTest.java` — 14건:

| # | 이름 | 단정 |
|---|---|---|
| 1 | `masksResidentRegistrationNumber` | `901231-1234567` → `[[RRN_1]]`, 원문 미포함 |
| 2 | `masksAccountNumber` | `110-234-567890` → `[[ACCT_1]]` |
| 3 | `masksMobilePhone` | `010-1234-5678` → `[[PHONE_1]]` |
| 4 | `masksBusinessNumber` | `123-45-67890` → `[[BIZ_1]]` |
| 5 | `masksCardNumber` | `1234-5678-9012-3456` → `[[CARD_1]]` |
| 6 | `masksEmail` | `hong@flowmate.co.kr` → `[[EMAIL_1]]` |
| 7 | `numbersEachOccurrenceSeparately` | 전화 2개 → `[[PHONE_1]]`, `[[PHONE_2]]` |
| 8 | `sameValueGetsSameToken` | 같은 전화번호 2번 → 둘 다 `[[PHONE_1]]` |
| 9 | `masksMultipleTypesInOneText` | 한 문장에 주민번호+계좌+이메일 → 셋 다 치환 |
| 10 | `leavesOrdinaryTextUntouched` | `3월 출장비 정산 540,000원` → 그대로 |
| 11 | `handlesNullAndEmpty` | `null` → `null`, `""` → `""` |
| 12 | ★ `restoresWhenAsked` | `restore(masked, map)` 가 원문 복원 |
| 13 | ★ `docNoLooksLikeAccountAndThatIsAccepted` | `EXP-2026-0001` 같은 문서번호가 계좌로 오인돼도 **테스트가 통과한다** — 오탐 허용의 명시 |
| 14 | ★ `rrnIsMaskedEvenInsideLongerDigits` | `주민번호901231-1234567입니다` 처럼 공백 없이 붙어도 잡는다 — 미탐 불허 |

**13번과 14번이 이 클래스의 성격을 규정한다.** 13번은 "오탐이 나도 그게 설계다"를 코드로 박아 넣는 것이고, 14번은 "공백에 의존하지 않는다"를 강제한다. 둘 다 없으면 나중에 누가 "정확도를 높이려고" 패턴을 좁혔을 때 아무도 못 막는다.

### Step 2. 빨간 것을 확인한다

```powershell
$env:JAVA_HOME = [Environment]::GetEnvironmentVariable('JAVA_HOME','User')
$env:Path = [Environment]::GetEnvironmentVariable('Path','Machine') + ';' + [Environment]::GetEnvironmentVariable('Path','User')
.\mvnw.cmd test "-Dtest=SensitiveDataMaskerTest"
```

`cannot find symbol: class SensitiveDataMasker` 를 눈으로 본다.

### Step 3. 구현

`src/main/java/com/flowmate/ai/mask/SensitiveDataMasker.java`:

```java
package com.flowmate.ai.mask;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 외부 LLM 으로 나가는 텍스트에서 민감정보를 토큰으로 치환한다 (설계서 §6.4.2).
 *
 * ★ 설계 원칙 — 오탐은 허용하고 미탐은 불허한다.
 *
 *   계좌번호 패턴 \d{2,6}-\d{2,6}-\d{2,8} 은 넓어서 문서번호나 날짜 일부를
 *   계좌로 오인할 수 있다. 그래도 좁히지 않는다.
 *   오탐: 요약 품질이 조금 떨어진다.
 *   미탐: 개인정보가 외부로 나간다.
 *   이 비대칭은 의도된 선택이다. 정확도를 높인다는 이유로 패턴을 좁히지 말 것.
 *   (SensitiveDataMaskerTest 의 docNoLooksLikeAccountAndThatIsAccepted 가 이를 고정한다)
 *
 * 순서가 중요하다. 주민번호(6-7)를 계좌(2~6-2~6-2~8)보다 먼저 검사하지 않으면
 * 계좌 패턴이 주민번호의 일부를 먼저 먹는다.
 */
@Component
public class SensitiveDataMasker {

    /** 검사 순서가 곧 우선순위다. 좁은 패턴이 넓은 패턴보다 앞에 온다. */
    private static final Rule[] RULES = {
        new Rule("RRN",   Pattern.compile("\\d{6}-\\d{7}")),
        new Rule("CARD",  Pattern.compile("\\d{4}-\\d{4}-\\d{4}-\\d{4}")),
        new Rule("PHONE", Pattern.compile("01\\d-\\d{3,4}-\\d{4}")),
        new Rule("BIZ",   Pattern.compile("\\d{3}-\\d{2}-\\d{5}")),
        new Rule("ACCT",  Pattern.compile("\\d{2,6}-\\d{2,6}-\\d{2,8}")),
        new Rule("EMAIL", Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+"))
    };

    /**
     * 마스킹한다. 복원용 매핑을 함께 돌려준다.
     *
     * 복원 매핑을 돌려주되 요약·점검 기능에서는 쓰지 않는다 (설계서 §6.4.2 정책 1).
     * 요약문에 계좌번호가 필요 없고, 복원하면 마스킹의 목적이 절반 사라진다.
     */
    public MaskResult mask(String text) {
        if (text == null || text.isEmpty()) {
            return new MaskResult(text, new LinkedHashMap<>());
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        Map<String, String> valueToToken = new LinkedHashMap<>();
        String result = text;

        for (Rule rule : RULES) {
            Matcher m = rule.pattern.matcher(result);
            StringBuilder sb = new StringBuilder();
            int counter = countExisting(mapping, rule.label);
            while (m.find()) {
                String matched = m.group();
                String token = valueToToken.get(matched);
                if (token == null) {
                    token = "[[" + rule.label + "_" + (++counter) + "]]";
                    valueToToken.put(matched, token);
                    mapping.put(token, matched);
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(token));
            }
            m.appendTail(sb);
            result = sb.toString();
        }
        return new MaskResult(result, mapping);
    }

    /** 토큰을 원문으로 되돌린다. 기본적으로 쓰지 않는다 — 설계서 §6.4.2 정책 1. */
    public String restore(String masked, Map<String, String> mapping) {
        if (masked == null) {
            return null;
        }
        String result = masked;
        for (Map.Entry<String, String> e : mapping.entrySet()) {
            result = result.replace(e.getKey(), e.getValue());
        }
        return result;
    }

    private int countExisting(Map<String, String> mapping, String label) {
        int n = 0;
        for (String token : mapping.keySet()) {
            if (token.startsWith("[[" + label + "_")) {
                n++;
            }
        }
        return n;
    }

    private static final class Rule {
        private final String label;
        private final Pattern pattern;

        private Rule(String label, Pattern pattern) {
            this.label = label;
            this.pattern = pattern;
        }
    }
}
```

`MaskResult` 는 `masked` 와 `mapping` 두 필드를 갖는 작은 값 객체다 (같은 패키지).

### Step 4. 초록 확인 + 커밋

`Tests run: 14` 를 확인하고 전체 `mvnw test` 로 **66** (52+14)을 확인한다.

---

## Task 3: 인터페이스 · 가짜 구현 · 프롬프트 저장소

**목표:** 체인이 올라탈 골격을 만든다. 아직 데코레이터는 없다.

### `LlmClient`

```java
package com.flowmate.ai.client;

import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import java.util.Optional;

/**
 * ★ 커스터마이징 지점 4 — AI 제공자 (설계서 §7)
 *
 * 구현 2종:
 *   - ClaudeLlmClient : 실제 Anthropic API 호출 (ai.enabled=true)
 *   - FakeLlmClient   : 고정 응답 (ai.enabled=false, 기본값)
 *
 * 데코레이터 4종이 같은 인터페이스로 서로를 감싼다 (설계서 §6.4.1).
 *
 * ★ 반환이 Optional 인 이유 — 설계서 §6.4.3 의 폴백 원칙:
 *   "AI 실패가 업무 실패가 되어서는 안 된다."
 *   구현체는 어떤 경우에도 예외를 던지지 않고 Optional.empty() 를 돌려준다.
 *   호출자는 try/catch 가 아니라 isPresent() 로 분기한다.
 */
public interface LlmClient {
    Optional<LlmResponse> complete(LlmRequest request);
}
```

### `FakeLlmClient`

테스트와 `ai.enabled=false` 양쪽에서 쓴다. 필요한 능력:

- 고정 응답 반환 (기본)
- **받은 요청을 기록** — 마스킹 검증(D8)에 필수
- 지연 주입 — `ResilientLlmClient` 타임아웃 테스트용
- 예외 주입 — 폴백 테스트용

```java
/**
 * 고정 응답을 돌려주는 구현. 두 가지 역할을 겸한다.
 *
 *   1) 테스트 대역 — 받은 요청을 기록하므로 "무엇이 바깥으로 나갔는지" 단정할 수 있다
 *   2) ai.enabled=false 일 때의 실제 구현 — API 키 없이 앱이 뜬다 (계획서 3 D3)
 *
 * 2번이 중요하다. 이것 덕분에 저장소를 클론한 사람이 키 없이 빌드하고 실행할 수 있다.
 */
```

기록용 필드: `List<LlmRequest> received` + `lastRequest()` 접근자.

### `PromptRepository` / `FilePromptRepository`

```java
public interface PromptRepository {
    /** 없으면 IllegalArgumentException. 조용히 빈 문자열을 돌려주지 않는다 — 프롬프트 없는 호출은 버그다 */
    String load(String feature, String version);
}
```

`FilePromptRepository` 는 `classpath:prompts/{feature}.{version}.txt` 를 읽는다. **읽은 것을 캐시한다** — 파일 I/O 를 매 호출마다 하지 않는다.

`src/main/resources/prompts/summary.v1.txt` 를 만든다. 내용은 설계서 §6.4.5의 프롬프트 규칙을 담되, **Phase 5에서 다듬을 초안**임을 파일 안에 적는다:

```
당신은 한국 기업의 전자결재 문서를 요약합니다.

규칙:
- 본문에 없는 내용을 만들지 않습니다.
- 금액·날짜·거래처는 원문 그대로 옮깁니다.
- 인사말과 상투어는 제외합니다.
- 결재자가 승인 여부를 판단하는 데 필요한 정보 위주로 씁니다.
- 3줄 이내, 각 줄 60자 이내로 씁니다.

[[RRN_1]] 같은 대괄호 토큰은 개인정보가 치환된 자리입니다.
그 자리의 실제 값을 추측하지 말고 토큰을 그대로 두십시오.
```

**마지막 두 줄이 중요하다.** 마스킹된 텍스트를 모델에게 주면서 그게 뭔지 알려주지 않으면, 모델이 토큰을 오타로 보고 "복원"하려 들거나 요약에서 빼버린다.

테스트 4건: 로드 성공 / 없는 파일 예외 / 캐시 동작 / 한글 정상.

---

## Task 4: 데코레이터 4종 (TDD)

**목표:** 설계서 §6.4.1의 체인을 구현한다. **각 데코레이터는 하나의 관심사만 갖는다.**

### `ResilientLlmClient` (가장 안쪽)

```java
/**
 * 타임아웃과 예외를 흡수한다. 설계서 §6.4.3 폴백 원칙의 실행 지점이다.
 *
 * ★ SDK 클라이언트의 타임아웃 설정만으로 부족한 이유:
 *   그건 HTTP 계층만 막는다. 이 데코레이터의 계약은
 *   "무슨 일이 있어도 지정 시간 안에 돌아온다" 이므로 별도로 건다.
 *
 * 어떤 예외도 밖으로 내보내지 않는다 — Error 는 제외한다.
 * OutOfMemoryError 를 삼키면 죽어야 할 프로세스가 이상하게 살아있게 된다.
 */
```

핵심:

```java
@Override
public Optional<LlmResponse> complete(LlmRequest request) {
    Future<Optional<LlmResponse>> future = executor.submit(() -> delegate.complete(request));
    try {
        return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
        future.cancel(true);
        log.warn("AI 호출 타임아웃 {}초 — feature={}", timeout.getSeconds(), request.getFeature());
        return Optional.empty();
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();   // ★ 인터럽트 상태를 복원한다
        return Optional.empty();
    } catch (ExecutionException e) {
        log.warn("AI 호출 실패 — feature={}", request.getFeature(), e.getCause());
        return Optional.empty();
    }
}
```

**`Thread.currentThread().interrupt()` 를 빠뜨리지 않는다.** `InterruptedException` 을 잡고 상태를 복원하지 않으면 상위 스레드 풀이 종료 신호를 못 받는다.

테스트 4건: 정상 통과 / 타임아웃 → empty / 예외 → empty / **원본이 empty 를 주면 그대로 empty**.

### `LoggingLlmClient`

성공·실패 모두 `ai_call_log` 에 남긴다. `latency_ms` 를 잰다.

```java
/**
 * ★ 로그 기록이 실패해도 AI 호출 결과를 버리지 않는다.
 *   로그는 부가 기능이다. DB 가 잠깐 이상해서 요약이 안 나오면 안 된다.
 */
```

이 주석이 가리키는 코드 — `logMapper.insert(...)` 를 try/catch 로 감싸 삼킨다. **이걸 안 하면 로깅 데코레이터가 폴백 원칙을 깨뜨린다.**

### `MaskingLlmClient`

```java
/**
 * 프롬프트를 마스킹해서 위임한다.
 *
 * ★ 체인에서 이 위치(캐싱 안쪽, 로깅 바깥쪽)가 중요하다:
 *   - 캐싱보다 안쪽이라야 캐시에 원문이 저장되지 않는다
 *   - 실제 호출보다 바깥이면 어떤 경로로 들어와도 원문이 나가지 않는다
 *   순서가 뒤집혀도 컴파일은 되고 테스트도 대부분 통과한다.
 *   LlmChainIT 가 이 순서를 단정한다.
 *
 * 복원하지 않는다 (설계서 §6.4.2 정책 1). mask() 가 돌려준 매핑을 버린다.
 */
```

### `CachingLlmClient` (가장 바깥)

`cache_key = SHA256(feature + ":" + promptVersion + ":" + prompt)` 의 hex.

```java
/**
 * ★ 캐시 키에 promptVersion 을 넣는 이유 (설계서 §6.4.3):
 *   프롬프트를 고쳤는데 캐시가 옛 결과를 돌려주면, 프롬프트를 고친 사람은
 *   "왜 안 바뀌지"를 한참 디버깅하게 된다. 버전이 키에 있으면 자동으로 무효화된다.
 *
 * 사전점검(PREFLIGHT)은 캐시하지 않는다 — 수정 후 재실행이 정상 동작이다.
 */
```

테스트 4건: 미스 → 위임 후 저장 / 히트 → 위임 안 함 + `hit_count` 증가 / **promptVersion 이 다르면 미스** / PREFLIGHT 는 캐시 안 함.

**단위 테스트에서 매퍼는 손으로 만든 가짜를 쓴다.** Mockito 를 새로 도입하지 않는다 — `spring-boot-starter-test` 에 들어있지만, 이 프로젝트는 지금까지 손으로 만든 대역만 써왔고 그 일관성이 읽기에 낫다.

---

## Task 5: `ClaudeLlmClient` 와 설정 배선

**목표:** 실제 SDK 호출. **이 Task의 테스트는 컴파일과 컨텍스트 기동뿐이다** — 실제 API 를 때리는 테스트는 만들지 않는다 (D3).

### Step 1. 의존성

```xml
<dependency>
    <groupId>com.anthropic</groupId>
    <artifactId>anthropic-java</artifactId>
    <version>2.34.0</version>
</dependency>
```

### Step 2. `ClaudeLlmClient`

```java
package com.flowmate.ai.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import java.util.Optional;

/**
 * 실제 Anthropic API 호출 (설계서 §6.4.1 체인의 맨 안쪽).
 *
 * ★ Claude Opus 5 의 요청 규약 (계획서 3 D6) — 아래는 전부 의도된 것이다:
 *
 *   temperature/top_p/top_k 를 넣지 않는다 — Opus 5 에서 제거됐다. 보내면 400 이다.
 *
 *   thinking 을 설정하지 않는다 — Opus 5 는 생략하면 thinking 이 켜진다(기본값 adaptive).
 *     끄는 것은 문서화된 실패 모드가 있어 권장되지 않으므로 켜둔 채 effort 로 조절한다.
 *
 *   maxTokens 4096 — ★ thinking 과 응답 텍스트를 합쳐서 제한한다.
 *     3줄 요약이라고 작게 주면 thinking 이 다 먹고 응답이 잘린다.
 *
 *   effort LOW — 요약·사전점검은 깊은 추론이 필요한 작업이 아니다.
 *
 * ★ API 키는 환경변수 ANTHROPIC_API_KEY 로만 받는다.
 *   fromEnv() 가 직접 읽으므로 코드에도 설정 파일에도 키 문자열이 등장하지 않는다.
 */
public class ClaudeLlmClient implements LlmClient {

    private final AnthropicClient client;
    private final String model;

    public ClaudeLlmClient(String model) {
        this.client = AnthropicOkHttpClient.fromEnv();
        this.model = model;
    }

    @Override
    public Optional<LlmResponse> complete(LlmRequest request) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(4096L)
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.LOW)
                        .build())
                .system(request.getSystemPrompt())
                .addUserMessage(request.getPrompt())
                .build();

        Message response = client.messages().create(params);

        // ★ content 를 읽기 전에 거절부터 검사한다.
        //   Opus 5 의 안전 분류기가 요청을 거절하면 HTTP 200 에 빈 content 가 온다.
        //   content.get(0) 을 먼저 하는 코드는 그 자리에서 IndexOutOfBounds 로 깨진다.
        if (isRefusal(response)) {
            return Optional.empty();
        }

        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .reduce("", String::concat);

        if (text.isEmpty()) {
            return Optional.empty();
        }

        LlmResponse result = new LlmResponse();
        result.setText(text);
        result.setModel(response.model().toString());
        result.setInputTokens((int) response.usage().inputTokens());
        result.setOutputTokens((int) response.usage().outputTokens());
        return Optional.of(result);
    }

    private boolean isRefusal(Message response) {
        return String.valueOf(response.stopReason()).toLowerCase().contains("refusal");
    }
}
```

**예외를 잡지 않는 것이 의도다.** `ResilientLlmClient` 가 바로 바깥에서 전부 흡수한다. 여기서 또 잡으면 두 곳에서 같은 일을 하게 되고, 어느 쪽이 진짜 방어선인지 흐려진다.

**★ 시그니처가 안 맞으면 컴파일러에게 물어본다.** 위 코드는 SDK 2.34.0 기준으로 썼지만 `stopReason()` 의 반환형, `usage().inputTokens()` 의 타입(long/Long), `Effort` 열거 위치는 다를 수 있다. **WebFetch 로 조사하지 말고** `mvnw test-compile` 을 돌려 `cannot find symbol` 이 가리키는 곳을 고친다. 필요하면 `javap -classpath <jar> com.anthropic.models.messages.Message` 로 멤버명을 확인한다. 정적 타입 SDK에서는 이쪽이 훨씬 빠르다.

### Step 3. `LlmConfig` — 체인 조립과 교체

```java
@Configuration
public class LlmConfig {

    /**
     * ★ 데코레이터 순서는 설계서 §6.4.1 이 정한 것이고 그 근거가 있다:
     *
     *   Caching (바깥) → 히트하면 마스킹도 API 호출도 안 한다. 비용 0.
     *     Masking     → 실제 호출 직전에 치환한다. 어느 경로로 들어와도 원문이 안 나간다.
     *       Logging   → 마스킹 이후에 로그를 남긴다. 로그에도 원문이 없다.
     *         Resilient (안쪽) → 타임아웃·예외를 흡수한다.
     *           실제 구현
     *
     *   Masking 이 Caching 보다 안쪽인 것이 특히 중요하다.
     *   뒤집히면 캐시 테이블에 원문이 그대로 저장된다.
     *   LlmChainIT 가 이 순서를 단정한다.
     */
    @Bean
    public LlmClient llmClient(LlmClient baseClient,
                               SensitiveDataMasker masker,
                               AiCallLogMapper logMapper,
                               AiResultCacheMapper cacheMapper,
                               AiProperties props) {
        LlmClient chain = new ResilientLlmClient(baseClient,
                Duration.ofSeconds(props.getTimeoutSeconds()));
        chain = new LoggingLlmClient(chain, logMapper);
        chain = new MaskingLlmClient(chain, masker);
        chain = new CachingLlmClient(chain, cacheMapper);
        return chain;
    }

    /** ai.enabled=true 이고 키가 있을 때만 실제 호출을 배선한다 (계획서 3 D3) */
    @Bean
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    public LlmClient claudeLlmClient(AiProperties props) {
        return new ClaudeLlmClient(props.getModel());
    }

    /**
     * 기본값. 키 없이 앱이 뜨고 테스트가 돈다.
     * ★ 이것이 커스터마이징 지점 4의 두 번째 구현이다 (계획서 3 D4).
     */
    @Bean
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "false", matchIfMissing = true)
    public LlmClient fakeLlmClient() {
        return new FakeLlmClient();
    }
}
```

**순환 참조에 대한 정정 (실측 결과):** `llmClient` 가 `LlmClient` 를 주입받으면서 자기도 `LlmClient` 라서 순환 참조가 날 것이라고 처음에 적었는데, **실제로는 나지 않는다.** Spring 이 타입 후보를 고를 때 "지금 만들고 있는 빈 자기 자신"을 후보에서 제외하기 때문이다(자기 참조 배제). `ai.enabled` 에 따라 `claudeLlmClient`/`fakeLlmClient` 중 정확히 하나만 활성화되므로 후보가 하나로 좁혀져 모호함이 없다.

그래도 `@Qualifier("baseLlmClient")` 를 붙인다 — 그 암묵적 동작에 기대면, 조건 없는 세 번째 `LlmClient` 빈이 언젠가 추가되는 순간 후보가 둘로 늘어나 다시 모호해진다.

### Step 4. `application.yml`

```yaml
ai:
  enabled: false          # 기본값 — API 키 없이 빌드·실행된다 (계획서 3 D3)
  model: claude-opus-5
  timeout-seconds: 30
  features:
    summary: true
    preflight: true
    leave-context: true
```

### Step 5. 컨텍스트 기동 확인

```powershell
.\mvnw.cmd verify "-Dit.test=FlowmateApplicationIT"
```

`ai.enabled=false` 로 뜨는지 확인한다.

### Step 6. ★ 키 없는 `enabled=true` 를 기동 시점에 막는다 (실측으로 밝혀진 결함)

처음 이 계획서는 "`ai.enabled=true` 로 두고 키가 없으면 `fromEnv()` 가 실패하니 그게 정상"이라고 적었다. **틀렸다. 실측 결과 `fromEnv()` 는 키가 없어도 예외를 던지지 않고 클라이언트를 만들어 준다.**

그래서 실제 동작은 계획서가 예상한 것보다 **나쁘다**:

1. 키 없이 `enabled=true` → 앱이 **정상 기동**한다
2. 첫 AI 호출 → 401
3. 그 401 을 바로 바깥의 `ResilientLlmClient` 가 **설계대로** 흡수해 `Optional.empty()` 로 바꾼다
4. 화면에는 "AI 기능을 일시적으로 사용할 수 없습니다"가 뜬다
5. → **설정 실수가 일시적 장애와 구별되지 않는다.** 아무도 눈치채지 못하고 AI 기능이 영구히 죽은 채로 운영된다

폴백이 잘 작동하기 때문에 오히려 문제가 숨는다는 점이 고약하다.

**조치 — `claudeLlmClient` 빈에서 기동 시점에 검사한다:**

```java
String apiKey = System.getenv("ANTHROPIC_API_KEY");
if (apiKey == null || apiKey.isBlank()) {
    throw new IllegalStateException(
            "ai.enabled=true 인데 환경변수 ANTHROPIC_API_KEY 가 없습니다. "
            + "키를 설정하거나 ai.enabled 를 false 로 두십시오.");
}
```

배포한 사람이 즉시 알아야 하는 종류의 문제이지, 사용자가 "AI가 안 되는데요"로 알려줄 문제가 아니다.

**확인:** `ai.enabled: true` + 키 없음 → 기동 실패 + 위 메시지. 그 뒤 **`false` 로 되돌리고 Read 도구로 확인하고 커밋한다.**

---

## Task 6: 체인 통합 테스트와 마감 ★

**목표:** 설계서가 내건 주장을 테스트로 고정한다.

### `LlmChainIT` — 6건

| # | 이름 | 무엇을 증명하는가 |
|---|---|---|
| 1 | ★ `originalTextNeverReachesTheInnerClient` | 주민번호가 든 프롬프트를 체인에 넣고, `FakeLlmClient.lastRequest()` 에 **원문이 없고 토큰이 있음**을 단정 — **D8의 핵심** |
| 2 | ★ `cacheStoresMaskedTextNotOriginal` | `ai_result_cache` 를 조회해 저장된 것에 원문이 없음을 단정 — 데코레이터 순서 증명 |
| 3 | `secondCallHitsCacheWithoutDelegating` | 같은 요청 2회 → `FakeLlmClient` 호출 1회, `hit_count=1` |
| 4 | `differentPromptVersionMissesCache` | 버전만 바꾸면 다시 위임된다 |
| 5 | `callIsLoggedEvenOnFailure` | 예외 주입 → `ai_call_log.success_yn='N'` 이 남고, 체인은 `empty` 를 돌려준다 |
| 6 | ★ `twoImplementationsProduceDifferentResults` | 커스터마이징 지점 4의 교체 증명 (D4) |

**1번과 2번이 이 Phase 전체의 존재 이유다.** 나머지가 다 통과해도 이 둘이 없으면 "민감정보가 안 나간다"는 주장에 근거가 없다.

### 마감 체크리스트

- [x] `mvnw clean verify` — 단위 **81** / 통합 **61** 기대. **실제 숫자를 기록한다** → **실측 단위 81 · 통합 61, BUILD SUCCESS. 기대와 정확히 일치했다**
- [x] **`ANTHROPIC_API_KEY` 환경변수를 지운 상태에서** 다시 `clean verify` — 통과해야 한다 (D3의 실증) → 프로세스·사용자·시스템 세 범위 모두 미설정 확인 후 `clean verify` 통과
- [x] `git log -p -S "sk-ant"` — 어느 커밋에도 키가 없는지 → 두 커밋에서 문자열이 걸리지만 둘 다 계획서/로드맵 마크다운이 이 체크리스트 명령 자체를 리터럴로 담고 있는 것뿐이다. 실제 키 값은 어떤 커밋에도 없다
- [x] `application.yml` 의 `ai.enabled` 가 `false` 인지 → 확인
- [x] 시드 무결: `docs=6 cache=0 log=0` → `depts=7 emps=20 docs=6 cache=0 log=0` (트랜잭션 테스트가 전부 롤백됨을 증명)
- [x] `README.md` 의 구현 현황에 Phase 3 체크, 테스트 수 갱신
- [x] 로드맵 §6 진행 상황에 계획서 3 완료, §5 Q4(API 키 보관) 확정으로 표시
- [x] `docs/oracle-mapping.md` — AI 스키마는 표준 SQL 이라 특이사항 없음을 한 줄 기록
- [ ] merge → tag `phase-3-ai-gateway` → push — **코디네이터가 수행한다 (이 세션의 범위 밖)**

### 다음 계획서로 넘기는 것

| # | 항목 | 왜 지금 안 하는가 |
|---|---|---|
| A1 | 구조화 출력(`output_config.format`) | 기능이 없으면 스키마도 없다. Phase 5 기능 1과 함께 (D1) |
| A2 | **캐시 키에 스키마 해시** | A1 이 들어오면 필수. 안 하면 조용히 옛 모양을 돌려준다 (D1) |
| A3 | `ai_preflight_result` 테이블 | Phase 5 사전점검과 함께 |
| A4 | 프롬프트 캐싱(SDK 레벨) | Opus 5 최소 512 토큰. 우리 시스템 프롬프트가 그보다 짧을 수 있어 Phase 5에서 실측 후 결정 |
| A5 | `fetch()` CSRF (로드맵 C3) | Phase 5가 AJAX 를 쓸 때. Phase 3은 화면이 없다 |
| A6 | 레이트 리밋 대응 | 실사용 부하가 없으므로 실측할 수 없다. Phase 5 평가셋 실행 때 드러난다 |

---

## 부록 — 설계서 §9 Phase 3 대응 확인

| 설계서 요구 | 이 계획서 | Task |
|---|---|---|
| `LlmClient` 인터페이스 | ✅ | 3 |
| `ClaudeLlmClient` | ✅ | 5 |
| `SensitiveDataMasker` + 단위 12건 | ✅ **14건** | 2 |
| 캐싱 데코레이터 | ✅ | 4 |
| 폴백 데코레이터 (`ResilientLlmClient`) | ✅ | 4 |
| 로깅 데코레이터 | ✅ | 4 |
| `PromptRepository`(파일) | ✅ | 3 |
| 기능 플래그 | ✅ | 5 |
| 완료 기준: 마스킹 테스트 통과 | ✅ | 2 |
| 완료 기준: `FakeLlmClient` 로 체인 통합 테스트 | ✅ **+ 마스킹 누출 단정** | 6 |
