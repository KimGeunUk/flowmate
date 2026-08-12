# Phase 7 — 공개 배포 하드닝 및 배포 테스트 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** FlowMate를 집 PC + Cloudflare Tunnel로 외부에 공개하되, 공개된 데모 계정으로 인해 발생하는 서버 장악·요금 폭탄·데이터 훼손 위험을 코드와 설정으로 차단한다.

**Architecture:** 위험 제거는 두 축이다. **(1) 노출면 축소** — Docker 포트 발행을 루프백으로 묶어 DB와 Tomcat이 인터넷에서 직접 보이지 않게 하고, 외부 접근은 Cloudflare Tunnel 한 경로로만 열어 준다. **(2) 남용 방어** — LLM 호출량 상한을 데코레이터 체인에 새 고리(`QuotaLlmClient`)로 끼워 넣는다. 캐시 바로 안쪽에 두어 캐시 히트는 상한을 소모하지 않고, 실제 API를 때리는 호출만 계수한다. 계수 근거는 이미 있는 `ai_call_log` 테이블이므로 새 상태 저장소를 만들지 않고 재기동에도 살아남는다.

**Tech Stack:** Spring Boot 3.5.16 (WAR) · Tomcat 10.1 · PostgreSQL 16 · MyBatis 3 · Docker Compose · Cloudflare Tunnel (cloudflared) · JUnit 5 + Failsafe

**전제와 금지사항:**
- `docker compose down -v` 는 이 계획 어디에서도 쓰지 않는다. 데모 초기화는 `DROP DATABASE ... WITH (FORCE)` 로 한다.
- API 키는 환경변수로만 전달한다. `.env`·`application-*.yml`·compose 파일 어디에도 값을 적지 않는다.
- 저장소 public 전환과 `v1.0.0` 태그는 이 계획의 범위 밖이다(사용자가 직접 수행).

---

## File Structure

| 파일 | 책임 | 신규/수정 |
|---|---|---|
| `docker-compose.yml` | 포트 발행을 루프백으로 제한, 상한·로그레벨 환경변수 전달 | 수정 |
| `src/main/java/com/flowmate/ai/client/QuotaLlmClient.java` | 일일 호출 상한 판정. 초과 시 위임하지 않고 `Optional.empty()` | **신규** |
| `src/main/java/com/flowmate/ai/mapper/AiCallLogMapper.java` | `countSince` 추가 | 수정 |
| `src/main/resources/mapper/ai/AiCallLogMapper.xml` | `countSince` 쿼리 | 수정 |
| `src/main/java/com/flowmate/config/AiProperties.java` | `dailyCallLimit` 추가 | 수정 |
| `src/main/java/com/flowmate/config/LlmConfig.java` | 체인에 Quota 배선 | 수정 |
| `src/main/resources/application.yml` | 상한 기본값·로그레벨 외부화 | 수정 |
| `src/main/webapp/WEB-INF/views/approval/write.jsp` | 개인정보 입력 금지 안내 | 수정 |
| `src/main/webapp/WEB-INF/views/login.jsp` | 공개 데모 안내 | 수정 |
| `src/main/webapp/static/css/style.css` | 안내 배너 스타일 | 수정 |
| `scripts/reset-demo.ps1` | 데모 데이터 일일 초기화 | **신규** |
| `docs/deploy.md` | 배포 절차 기록 | **신규** |
| `src/test/java/com/flowmate/ai/client/QuotaLlmClientTest.java` | 상한 판정 단위 테스트 | **신규** |
| `src/test/java/com/flowmate/ai/mapper/AiCallLogMapperIT.java` | 계수 경계 통합 테스트 | **신규** |
| `src/test/java/com/flowmate/ai/client/QuotaChainIT.java` | 체인 배선 위치 검증 | **신규** |

---

## Task 0: 작업 브랜치 생성

**Files:** 없음

- [ ] **Step 1: main이 깨끗한지 확인**

```bash
git status --porcelain
```

Expected: 출력 없음. 출력이 있으면 먼저 정리하거나 stash 한다.

- [ ] **Step 2: 브랜치 생성**

```bash
git checkout -b feature/phase-7-public-deploy
git branch --show-current
```

Expected: `feature/phase-7-public-deploy`

---

## Task 1: 포트 발행을 루프백으로 제한 (위험 1 제거)

DB가 인터넷에서 직접 보이는 것이 가장 심각한 위험이다. `postgres` 이미지는 `POSTGRES_USER` 로 준 이름을 `initdb --username` 에 넘기므로 `flowmate` 는 **부트스트랩 슈퍼유저**다. 슈퍼유저 접속이 뚫리면 `COPY ... FROM PROGRAM` 으로 컨테이너 안 임의 명령 실행이 된다.

Docker는 기본적으로 `0.0.0.0` 에 발행한다. 주소를 명시하면 호스트 내부에서만 접근 가능해진다. **로컬 `mvnw verify` 는 `localhost` 로 붙으므로 그대로 동작한다.** Cloudflare Tunnel의 `cloudflared` 도 같은 호스트에서 `localhost:18080` 으로 붙으므로 영향이 없다.

**Files:**
- Modify: `docker-compose.yml:14-15`, `docker-compose.yml:74-76`

- [ ] **Step 1: postgres 포트 바인딩 변경**

`docker-compose.yml` 의 postgres 서비스에서:

```yaml
    ports:
      - "5432:5432"
```

를 아래로 바꾼다.

```yaml
    ports:
      # ★ 0.0.0.0 이 아니라 127.0.0.1 에 발행한다. Docker 의 기본값은 모든 인터페이스라,
      #   이 호스트를 인터넷에 노출하는 순간 PostgreSQL 이 그대로 공개된다.
      #   flowmate 계정은 initdb --username 으로 만들어진 부트스트랩 슈퍼유저이므로
      #   접속이 뚫리면 COPY ... FROM PROGRAM 으로 컨테이너 안 명령 실행까지 이어진다.
      #   호스트에서 도는 mvnw verify 와 psql 은 localhost 로 붙으므로 영향이 없다.
      - "127.0.0.1:5432:5432"
```

- [ ] **Step 2: tomcat 포트 바인딩 변경**

`docker-compose.yml` 의 tomcat 서비스에서:

```yaml
    ports:
      # 로컬 spring-boot:run(내장 Tomcat, 8080)과 충돌하지 않도록 다른 포트로 발행한다.
      - "18080:8080"
```

를 아래로 바꾼다.

```yaml
    ports:
      # 로컬 spring-boot:run(내장 Tomcat, 8080)과 충돌하지 않도록 다른 포트로 발행한다.
      # ★ 127.0.0.1 에 묶는 이유: 외부 접근은 Cloudflare Tunnel 한 경로로만 받는다.
      #   cloudflared 는 같은 호스트에서 localhost:18080 으로 붙으므로 터널은 그대로 동작하고,
      #   공유기 포트포워딩이나 방화벽 실수로 8080 이 열리는 사고가 원천 차단된다.
      - "127.0.0.1:18080:8080"
```

- [ ] **Step 3: 재기동 후 실제 바인딩 주소 확인**

```bash
docker compose up -d --force-recreate
docker compose ps --format "{{.Service}}  {{.Ports}}"
```

Expected: 두 줄 모두 `127.0.0.1:` 접두사가 붙어 있다.
```
postgres  127.0.0.1:5432->5432/tcp
tomcat    127.0.0.1:18080->8080/tcp
```

- [ ] **Step 4: 호스트 리스닝 주소를 OS 수준에서 재확인**

PowerShell:

```powershell
Get-NetTCPConnection -LocalPort 5432,18080 -State Listen | Select-Object LocalAddress, LocalPort
```

Expected: `LocalAddress` 가 전부 `127.0.0.1`. `0.0.0.0` 이나 `::` 가 하나라도 보이면 Step 1~2가 반영되지 않은 것이다.

- [ ] **Step 5: 기존 접근 경로가 안 깨졌는지 확인**

```bash
./mvnw -q verify -DskipITs=false 2>&1 | tail -20
```

Expected: 단위·통합 테스트 전부 통과 (직전 기준 단위 180 / 통합 154). DB 접속 실패가 나면 루프백 바인딩이 아니라 다른 원인이다.

- [ ] **Step 6: 커밋**

```bash
git add docker-compose.yml
git commit -m "security: DB·Tomcat 포트를 루프백에만 발행한다

공개 호스트에 올리는 순간 5432 가 인터넷에 노출된다. flowmate 계정은
initdb --username 으로 만들어진 부트스트랩 슈퍼유저라, 접속이 뚫리면
COPY ... FROM PROGRAM 으로 컨테이너 안 명령 실행까지 이어진다.
외부 접근은 Cloudflare Tunnel 한 경로로만 받는다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 2: 호출 건수 계수 쿼리 (TDD)

상한을 판정하려면 "오늘 몇 번 불렀나"를 알아야 한다. 새 테이블을 만들지 않고 `ai_call_log` 를 쓴다 — 이미 성공·실패를 모두 남기고 있고(`success_yn`), 재기동해도 값이 남는다. 인메모리 카운터였다면 재기동 한 번으로 상한이 리셋되어 방어가 무의미해진다.

**실패한 호출도 센다.** 실패해도 API는 이미 때린 것이므로 비용 방어 관점에서 성공과 다르지 않다.

**Files:**
- Modify: `src/main/java/com/flowmate/ai/mapper/AiCallLogMapper.java`
- Modify: `src/main/resources/mapper/ai/AiCallLogMapper.xml`
- Test: `src/test/java/com/flowmate/ai/mapper/AiCallLogMapperIT.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/flowmate/ai/mapper/AiCallLogMapperIT.java` 생성:

```java
package com.flowmate.ai.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.ai.domain.AiCallLog;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 호출 건수 계수 검증.
 *
 * ★ 절대 건수를 단정하지 않고 **증분**을 단정한다. ai_call_log 는 다른 테스트가
 *   남긴 행이 이미 있을 수 있는 공용 테이블이라, "3건이다"는 실행 순서에 따라
 *   깨진다. 이 테스트가 스스로 만든 행만 세는 것이 조건을 통제하는 방법이다.
 *
 * ★ 경계값을 LocalDateTime.now() 로 잡지 않는다. Java 의 now() 는 나노초,
 *   PostgreSQL 의 timestamp 는 마이크로초 정밀도라 방금 넣은 행이 경계보다
 *   과거로 판정될 수 있다 - 아주 가끔 깨지는 테스트가 된다. 그래서 경계도
 *   행의 called_at 도 이 테스트가 직접 지정한다.
 */
@SpringBootTest
@Transactional
class AiCallLogMapperIT {

    @Autowired
    private AiCallLogMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("★ 경계 이후 행만 센다 - 경계와 같은 시각은 포함한다")
    void countsOnlyRowsAtOrAfterTheBoundary() {
        LocalDateTime boundary = LocalDateTime.of(2030, 1, 15, 0, 0);
        long before = mapper.countSince(boundary);

        insertAt(boundary.minusSeconds(1));  // 경계 직전 - 세지 않는다
        insertAt(boundary);                  // 경계와 같은 시각 - 센다
        insertAt(boundary.plusHours(5));     // 경계 이후 - 센다

        assertThat(mapper.countSince(boundary) - before).isEqualTo(2);
    }

    @Test
    @DisplayName("실패한 호출도 센다 - API 를 때린 것은 성공과 같다")
    void failedCallsCountToo() {
        LocalDateTime boundary = LocalDateTime.of(2030, 2, 20, 0, 0);
        long before = mapper.countSince(boundary);

        insertAt(boundary.plusMinutes(1), "N");
        insertAt(boundary.plusMinutes(2), "N");

        assertThat(mapper.countSince(boundary) - before).isEqualTo(2);
    }

    @Test
    @DisplayName("insert 가 남긴 행도 같은 방식으로 세어진다 - 운영 경로와 같은 행이다")
    void rowsWrittenByInsertAreCounted() {
        LocalDateTime boundary = LocalDateTime.now().minusMinutes(1);
        long before = mapper.countSince(boundary);

        AiCallLog entry = new AiCallLog();
        entry.setFeature("SUMMARY");
        entry.setPromptVersion("v1");
        entry.setSuccessYn("Y");
        mapper.insert(entry);

        assertThat(mapper.countSince(boundary) - before).isEqualTo(1);
    }

    private void insertAt(LocalDateTime calledAt) {
        insertAt(calledAt, "Y");
    }

    /** called_at 을 직접 지정해야 하므로 매퍼가 아니라 JdbcTemplate 으로 넣는다 */
    private void insertAt(LocalDateTime calledAt, String successYn) {
        jdbcTemplate.update(
                "INSERT INTO ai_call_log (feature, prompt_version, success_yn, called_at) "
                        + "VALUES ('SUMMARY', 'v1', ?, ?)",
                successYn, calledAt);
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./mvnw -q verify -Dit.test=AiCallLogMapperIT
```

Expected: 컴파일 실패 — `cannot find symbol: method countSince(LocalDateTime)`

- [ ] **Step 3: 매퍼 인터페이스에 메서드 추가**

`src/main/java/com/flowmate/ai/mapper/AiCallLogMapper.java` 를 아래로 교체:

```java
package com.flowmate.ai.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.ai.domain.AiCallLog;

@Mapper
public interface AiCallLogMapper {

    /** 저장 후 생성된 PK 를 인자 객체에 채운다. 성공·실패 모두 이 메서드로 남긴다 */
    void insert(AiCallLog log);

    /**
     * {@code since} 이후(같은 시각 포함)의 호출 건수.
     *
     * ★ 성공·실패를 가리지 않고 센다. 실패한 호출도 API 를 이미 때린 것이라
     *   비용 방어 관점에서는 성공과 다르지 않다. {@code QuotaLlmClient} 가
     *   일일 상한을 판정할 때 쓴다.
     */
    long countSince(@Param("since") LocalDateTime since);
}
```

- [ ] **Step 4: 매퍼 XML에 쿼리 추가**

`src/main/resources/mapper/ai/AiCallLogMapper.xml` 의 `</mapper>` 바로 앞에 추가:

```xml
    <!--
      경계를 파라미터로 받는 이유: CURRENT_DATE 를 쿼리 안에서 쓰면 "오늘"의 정의가
      DB 서버 시각에 묶여 테스트가 스스로 조건을 만들 수 없다. 경계를 자바에서
      계산해 넘기면 QuotaLlmClient 가 Clock 을 주입받아 시각을 통제할 수 있다.
    -->
    <select id="countSince" resultType="long">
        SELECT COUNT(*)
          FROM ai_call_log
         WHERE called_at &gt;= #{since}
    </select>
```

- [ ] **Step 5: 테스트가 통과하는지 확인**

```bash
./mvnw -q verify -Dit.test=AiCallLogMapperIT
```

Expected: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/com/flowmate/ai/mapper/AiCallLogMapper.java src/main/resources/mapper/ai/AiCallLogMapper.xml src/test/java/com/flowmate/ai/mapper/AiCallLogMapperIT.java
git commit -m "feat: ai_call_log 에서 기간별 호출 건수를 센다

일일 호출 상한의 계수 근거. 새 테이블을 만들지 않고 이미 성공·실패를
모두 남기는 로그를 쓴다 - 인메모리 카운터는 재기동 한 번에 리셋되어
상한이 무의미해진다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 3: QuotaLlmClient 데코레이터 (TDD)

**설계 판단 — 상한 검사가 실패하면 호출을 막는다(fail-closed).** 이 프로젝트의 다른 곳은 "AI 실패가 업무 실패가 되어서는 안 된다"는 폴백 원칙을 따르지만, 이 데코레이터만은 반대로 간다. 목적이 비용 방어이기 때문이다 — 셀 수 없는 상태에서 계속 호출하면 상한이 아예 없는 것과 같다. 다만 예외를 던지지는 않고 `Optional.empty()` 를 돌려주므로, **사용자가 보는 화면은 평소의 폴백 문구 그대로**다. 원칙을 깨는 게 아니라 적용 층위가 다르다.

**Files:**
- Create: `src/main/java/com/flowmate/ai/client/QuotaLlmClient.java`
- Test: `src/test/java/com/flowmate/ai/client/QuotaLlmClientTest.java`

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/com/flowmate/ai/client/QuotaLlmClientTest.java` 생성:

```java
package com.flowmate.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.ai.domain.AiCallLog;
import com.flowmate.ai.domain.AiFeature;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import com.flowmate.ai.mapper.AiCallLogMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 일일 호출 상한 판정. Spring 없이 돈다 - 스텁 두 개로 조건을 전부 통제한다.
 *
 * ★ Clock 을 주입받는 설계인 이유: "오늘"의 경계를 테스트가 정할 수 있어야 한다.
 *   시스템 시각에 의존하면 자정 직전에 돌린 빌드만 깨지는 테스트가 된다.
 */
class QuotaLlmClientTest {

    /** 2026-08-12 14:30 KST 로 고정 */
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-12T05:30:00Z"), ZoneId.of("Asia/Seoul"));

    @Test
    @DisplayName("상한 미만이면 그대로 위임한다")
    void belowLimitDelegates() {
        CountingDelegate delegate = new CountingDelegate();
        QuotaLlmClient client = new QuotaLlmClient(delegate, countingMapper(9), 10, FIXED_CLOCK);

        assertThat(client.complete(request())).isPresent();
        assertThat(delegate.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("★ 상한에 도달하면 위임하지 않는다 - API 를 때리지 않는 것이 핵심이다")
    void atLimitDoesNotDelegate() {
        CountingDelegate delegate = new CountingDelegate();
        QuotaLlmClient client = new QuotaLlmClient(delegate, countingMapper(10), 10, FIXED_CLOCK);

        assertThat(client.complete(request())).isEmpty();
        assertThat(delegate.calls).isZero();
    }

    @Test
    @DisplayName("경계: 상한-1 은 통과하고 상한은 막힌다")
    void boundaryIsExclusiveOnTheLimit() {
        assertThat(new QuotaLlmClient(new CountingDelegate(), countingMapper(4), 5, FIXED_CLOCK)
                .complete(request())).isPresent();
        assertThat(new QuotaLlmClient(new CountingDelegate(), countingMapper(5), 5, FIXED_CLOCK)
                .complete(request())).isEmpty();
    }

    @Test
    @DisplayName("상한 0 은 무제한이다 - 로컬 개발의 기본값이라 아무것도 막지 않는다")
    void zeroMeansUnlimited() {
        CountingDelegate delegate = new CountingDelegate();
        QuotaLlmClient client = new QuotaLlmClient(delegate, countingMapper(9_999), 0, FIXED_CLOCK);

        assertThat(client.complete(request())).isPresent();
        assertThat(delegate.calls).isEqualTo(1);
    }

    @Test
    @DisplayName("★ 셀 수 없으면 막는다(fail-closed) - 비용 방어가 목적이라 반대로 가면 상한이 없는 것과 같다")
    void countingFailureBlocksTheCall() {
        CountingDelegate delegate = new CountingDelegate();
        AiCallLogMapper broken = new AiCallLogMapper() {
            @Override
            public void insert(AiCallLog log) { }

            @Override
            public long countSince(LocalDateTime since) {
                throw new IllegalStateException("DB 접속 불가");
            }
        };

        QuotaLlmClient client = new QuotaLlmClient(delegate, broken, 10, FIXED_CLOCK);

        assertThat(client.complete(request())).isEmpty();
        assertThat(delegate.calls).isZero();
    }

    @Test
    @DisplayName("★ 경계는 그날 00:00 이다 - 어제 호출은 오늘 상한을 잡아먹지 않는다")
    void boundaryIsStartOfTodayInTheClockZone() {
        List<LocalDateTime> asked = new ArrayList<>();
        AiCallLogMapper recording = new AiCallLogMapper() {
            @Override
            public void insert(AiCallLog log) { }

            @Override
            public long countSince(LocalDateTime since) {
                asked.add(since);
                return 0L;
            }
        };

        new QuotaLlmClient(new CountingDelegate(), recording, 10, FIXED_CLOCK).complete(request());

        assertThat(asked).containsExactly(LocalDateTime.of(2026, 8, 12, 0, 0));
    }

    private LlmRequest request() {
        LlmRequest request = new LlmRequest();
        request.setFeature(AiFeature.DRAFT_HINT);
        request.setPromptVersion("v1");
        request.setPrompt("본문을 제안해 주세요");
        return request;
    }

    /** countSince 가 항상 같은 값을 돌려주는 스텁 */
    private AiCallLogMapper countingMapper(long used) {
        return new AiCallLogMapper() {
            @Override
            public void insert(AiCallLog log) { }

            @Override
            public long countSince(LocalDateTime since) {
                return used;
            }
        };
    }

    /** 위임 횟수를 세는 스텁. 항상 성공 응답을 돌려준다 */
    private static class CountingDelegate implements LlmClient {
        private int calls;

        @Override
        public Optional<LlmResponse> complete(LlmRequest request) {
            calls++;
            return Optional.of(new LlmResponse());
        }
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./mvnw -q test -Dtest=QuotaLlmClientTest
```

Expected: 컴파일 실패 — `cannot find symbol: class QuotaLlmClient`

- [ ] **Step 3: 데코레이터 구현**

`src/main/java/com/flowmate/ai/client/QuotaLlmClient.java` 생성:

```java
package com.flowmate.ai.client;

import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import com.flowmate.ai.mapper.AiCallLogMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 하루 호출 건수 상한. 넘으면 위임하지 않고 {@code Optional.empty()} 를 돌려준다.
 *
 * ★ 이 데코레이터가 생긴 이유: 공개 배포에서는 데모 계정이 README 에 적혀 있어
 *   누구나 로그인할 수 있다. PREFLIGHT 와 DRAFT_HINT 는 설계상 캐시하지 않으므로
 *   (CachingLlmClient 의 NEVER_CACHED) 요청을 반복하면 그대로 API 호출이 되고,
 *   막는 장치가 없으면 요금이 무한히 올라간다.
 *
 * ★ 체인에서의 위치가 의미를 만든다 - Caching 바로 안쪽이다.
 *   캐시 히트는 여기까지 오지 않으므로 상한을 소모하지 않는다. 즉 상한은
 *   "요청 수"가 아니라 **"실제로 API 를 때린 수"**를 센다.
 *
 * ★ 셀 수 없으면 막는다(fail-closed). 이 프로젝트의 다른 곳은 "AI 실패가 업무
 *   실패가 되어서는 안 된다"를 따르지만 여기만 반대로 간다 - 목적이 비용 방어라,
 *   셀 수 없는 상태에서 계속 호출하면 상한이 아예 없는 것과 같기 때문이다.
 *   다만 예외를 던지지는 않으므로 사용자가 보는 화면은 평소의 폴백 그대로다.
 *
 * ★ 상한 도달을 WARN 으로 남기는 이유: 폴백 문구만 보면 상한 도달과 API 장애가
 *   구별되지 않는다. 운영자는 둘을 구별할 수 있어야 한다(LlmConfig.requireApiKey
 *   가 키 누락을 기동 실패로 만든 것과 같은 판단이다).
 */
public class QuotaLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(QuotaLlmClient.class);

    private final LlmClient delegate;
    private final AiCallLogMapper logMapper;
    private final int dailyLimit;
    private final Clock clock;

    /**
     * @param dailyLimit 하루 최대 호출 수. 0 이하면 무제한(로컬 개발의 기본값)
     * @param clock      "오늘"의 경계를 정한다. 테스트가 시각을 통제할 수 있도록 주입받는다
     */
    public QuotaLlmClient(LlmClient delegate, AiCallLogMapper logMapper,
                          int dailyLimit, Clock clock) {
        this.delegate = delegate;
        this.logMapper = logMapper;
        this.dailyLimit = dailyLimit;
        this.clock = clock;
    }

    @Override
    public Optional<LlmResponse> complete(LlmRequest request) {
        if (dailyLimit <= 0) {
            return delegate.complete(request);
        }

        long used;
        try {
            used = logMapper.countSince(LocalDate.now(clock).atStartOfDay());
        } catch (Exception e) {
            log.warn("AI 호출 건수를 셀 수 없어 호출을 막는다 - 셀 수 없는 상태의 무제한 호출이 더 위험하다", e);
            return Optional.empty();
        }

        if (used >= dailyLimit) {
            log.warn("AI 일일 호출 상한 도달 - 호출하지 않고 폴백한다 (feature={}, {}/{})",
                    request.getFeature(), used, dailyLimit);
            return Optional.empty();
        }

        return delegate.complete(request);
    }
}
```

- [ ] **Step 4: 테스트가 통과하는지 확인**

```bash
./mvnw -q test -Dtest=QuotaLlmClientTest
```

Expected: `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/com/flowmate/ai/client/QuotaLlmClient.java src/test/java/com/flowmate/ai/client/QuotaLlmClientTest.java
git commit -m "feat: AI 일일 호출 상한 데코레이터

공개 배포에서는 데모 계정이 README 에 있어 누구나 로그인할 수 있고,
PREFLIGHT·DRAFT_HINT 는 설계상 캐시하지 않아 반복 요청이 그대로 API
호출이 된다. 상한 초과 시 위임하지 않고 폴백한다.

셀 수 없으면 막는다 - 비용 방어가 목적이라 fail-open 이면 상한이
없는 것과 같다. 예외는 던지지 않으므로 화면은 평소 폴백 그대로다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 4: 체인에 배선하고 설정으로 외부화

**Files:**
- Modify: `src/main/java/com/flowmate/config/AiProperties.java`
- Modify: `src/main/java/com/flowmate/config/LlmConfig.java:52-64`, 클래스 주석 `24-30`
- Modify: `src/main/resources/application.yml:59-75`
- Modify: `docker-compose.yml` (tomcat environment)
- Test: `src/test/java/com/flowmate/ai/client/QuotaChainIT.java`

- [ ] **Step 1: 실패하는 통합 테스트 작성**

`src/test/java/com/flowmate/ai/client/QuotaChainIT.java` 생성:

```java
package com.flowmate.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.ai.domain.AiFeature;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상한이 체인의 **올바른 위치**에 들어갔는지 검증한다.
 *
 * ★ 이 테스트가 필요한 이유: QuotaLlmClientTest 는 데코레이터 자체만 본다.
 *   배선 위치가 틀리면(예: Caching 바깥) 단위 테스트는 전부 통과하면서
 *   캐시 히트가 상한을 소모하는 잘못된 동작이 된다.
 *
 * ★ ai_call_log 는 공용 테이블이라 다른 테스트가 남긴 행이 오늘 자로 있을 수
 *   있다. @BeforeEach 에서 오늘 행을 지워 조건을 스스로 만든다 - @Transactional
 *   이므로 이 삭제도 테스트가 끝나면 롤백된다.
 */
@SpringBootTest(properties = "ai.daily-call-limit=2")
@Transactional
class QuotaChainIT {

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearTodaysCalls() {
        jdbcTemplate.update("DELETE FROM ai_call_log WHERE called_at >= CURRENT_DATE");
    }

    @Test
    @DisplayName("★ 상한을 넘으면 체인이 결과를 내주지 않는다")
    void chainStopsAfterTheLimit() {
        // DRAFT_HINT 는 CachingLlmClient 의 NEVER_CACHED 라 매번 안쪽까지 내려간다.
        assertThat(call(AiFeature.DRAFT_HINT)).isPresent();
        assertThat(call(AiFeature.DRAFT_HINT)).isPresent();
        assertThat(call(AiFeature.DRAFT_HINT)).isEmpty();
    }

    @Test
    @DisplayName("★ 캐시 히트는 상한을 소모하지 않는다 - Quota 가 Caching 안쪽이라는 증거")
    void cacheHitsDoNotConsumeQuota() {
        // 프롬프트에 UUID 를 넣는 이유: ai_result_cache 에 이전 실행이 커밋한 행이
        // 남아 있을 수 있다. 첫 호출이 반드시 캐시 미스여야 이 테스트가 의미를 갖는다.
        LlmRequest cacheable = request(AiFeature.SUMMARY, "동일한 프롬프트 " + UUID.randomUUID());

        assertThat(llmClient.complete(cacheable)).isPresent();   // 1회 소모
        assertThat(llmClient.complete(cacheable)).isPresent();   // 캐시 히트 - 소모 안 함
        assertThat(llmClient.complete(cacheable)).isPresent();   // 캐시 히트 - 소모 안 함

        // 상한이 2인데 3번 불러도 살아 있다. 아직 1회만 소모했으므로 새 호출이 통과한다.
        assertThat(call(AiFeature.DRAFT_HINT)).isPresent();
    }

    private Optional<LlmResponse> call(String feature) {
        return llmClient.complete(request(feature, "프롬프트 " + UUID.randomUUID()));
    }

    private LlmRequest request(String feature, String prompt) {
        LlmRequest request = new LlmRequest();
        request.setFeature(feature);
        request.setPromptVersion("v1");
        request.setPrompt(prompt);
        request.setOutputType(String.class);
        return request;
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

```bash
./mvnw -q verify -Dit.test=QuotaChainIT
```

Expected: FAIL — 상한이 배선되지 않아 3번째 `DRAFT_HINT` 호출도 `isPresent()` 다.

- [ ] **Step 3: AiProperties 에 상한 필드 추가**

`src/main/java/com/flowmate/config/AiProperties.java` 의 `private int timeoutSeconds = 30;` 아래에 필드를 추가한다:

```java
    /**
     * 하루 최대 LLM 호출 수. 0 이면 무제한이다(기본값 - 로컬 개발과 테스트에 영향이 없다).
     * 공개 배포에서는 환경변수 AI_DAILY_CALL_LIMIT 으로 실제 값을 준다.
     */
    private int dailyCallLimit = 0;
```

그리고 `getTimeoutSeconds`/`setTimeoutSeconds` 아래에 접근자를 추가한다:

```java
    public int getDailyCallLimit() {
        return dailyCallLimit;
    }

    public void setDailyCallLimit(int dailyCallLimit) {
        this.dailyCallLimit = dailyCallLimit;
    }
```

- [ ] **Step 4: LlmConfig 체인에 배선**

`src/main/java/com/flowmate/config/LlmConfig.java` 에서 `java.time.Duration` import 아래에 추가:

```java
import java.time.Clock;
```

`llmClient` 메서드 본문을 아래로 교체:

```java
        LlmClient chain = new ResilientLlmClient(baseClient,
                Duration.ofSeconds(props.getTimeoutSeconds()));
        chain = new LoggingLlmClient(chain, logMapper);
        chain = new MaskingLlmClient(chain, masker);
        chain = new QuotaLlmClient(chain, logMapper, props.getDailyCallLimit(),
                Clock.systemDefaultZone());
        chain = new CachingLlmClient(chain, cacheMapper, modelKey(props));
        return chain;
```

`QuotaLlmClient` import 를 추가한다:

```java
import com.flowmate.ai.client.QuotaLlmClient;
```

클래스 주석의 체인 그림(현재 `24-30행`)을 아래로 교체:

```java
 * <pre>
 *   Caching (바깥)  히트하면 상한도 마스킹도 API 호출도 하지 않는다
 *     Quota         실제로 API 를 때릴 것만 계수한다 (캐시 히트는 여기 못 온다)
 *       Masking     실제 호출 직전에 치환한다
 *         Logging   마스킹 이후라 로그에도 원문이 없다
 *           Resilient 타임아웃·예외를 흡수한다
 *             Claude / Gemini / Fake 중 하나
 * </pre>
 *
 * ★ Quota 가 Caching 안쪽인 것이 상한의 의미를 정한다 - 상한은 "요청 수"가 아니라
 * "실제로 API 를 때린 수"다. 바깥에 두면 공짜인 캐시 히트가 상한을 갉아먹는다.
 * QuotaChainIT 가 이 순서를 단정한다.
```

- [ ] **Step 5: application.yml 에 기본값 명시**

`src/main/resources/application.yml` 의 `timeout-seconds: 30` 바로 아래에 추가:

```yaml
  # 하루 최대 LLM 호출 수. 0 = 무제한(로컬 기본값).
  # 공개 배포에서는 환경변수 AI_DAILY_CALL_LIMIT 으로 준다 - 데모 계정이 공개돼 있어
  # 상한이 없으면 누구나 요금을 올릴 수 있다. QuotaLlmClient 참고.
  daily-call-limit: 0
```

같은 파일 `features:` 블록에 누락된 `draft-hint` 를 추가한다(`AiProperties.Features` 에는 이미 있으나 yml 에만 빠져 있었다):

```yaml
  features:
    summary: true
    preflight: true
    leave-context: true
    draft-hint: true
```

- [ ] **Step 6: compose 에 환경변수 전달**

`docker-compose.yml` tomcat 서비스의 `AI_FEATURES_LEAVECONTEXT` 줄 아래에 추가:

```yaml
      # 하루 LLM 호출 상한. 0 = 무제한. 공개 배포에서는 반드시 실제 값을 준다.
      # Spring 의 relaxed binding 으로 ai.daily-call-limit 에 매핑된다.
      AI_DAILY_CALL_LIMIT: "${AI_DAILY_CALL_LIMIT:-0}"
```

- [ ] **Step 7: 테스트가 통과하는지 확인**

```bash
./mvnw -q verify -Dit.test=QuotaChainIT
```

Expected: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 8: 전체 테스트로 회귀 확인**

```bash
./mvnw -q verify 2>&1 | tail -20
```

Expected: 전부 통과. `ai.daily-call-limit` 기본값이 0이므로 기존 테스트는 영향받지 않는다.

- [ ] **Step 9: 커밋**

```bash
git add src/main/java/com/flowmate/config/AiProperties.java src/main/java/com/flowmate/config/LlmConfig.java src/main/resources/application.yml docker-compose.yml src/test/java/com/flowmate/ai/client/QuotaChainIT.java
git commit -m "feat: 호출 상한을 캐시 안쪽에 배선한다

Caching 바로 안쪽이라 캐시 히트는 상한을 소모하지 않는다 - 상한이
'요청 수'가 아니라 '실제로 API 를 때린 수'를 세게 된다. 기본값 0(무제한)
이라 로컬과 기존 테스트에는 영향이 없다.

application.yml 의 features 에 빠져 있던 draft-hint 도 함께 채운다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 5: 운영 로그 레벨 외부화

`logging.level.com.flowmate: DEBUG` 는 공개 배포에서 두 가지로 불리하다 — 디스크를 빠르게 채우고, 쿼리 파라미터를 포함한 상세 로그가 남는다.

**Files:**
- Modify: `src/main/resources/application.yml:77-79`
- Modify: `docker-compose.yml` (tomcat environment)

- [ ] **Step 1: application.yml 수정**

```yaml
logging:
  level:
    # 로컬 기본값은 DEBUG 로 둔다(개발 중 쿼리와 분기를 보려는 것이 목적).
    # 공개 배포에서는 FLOWMATE_LOG_LEVEL=INFO 로 낮춘다 - DEBUG 는 디스크를 빠르게
    # 채우고 파라미터를 포함한 상세 로그를 남긴다.
    com.flowmate: ${FLOWMATE_LOG_LEVEL:DEBUG}
```

- [ ] **Step 2: compose 에 환경변수 전달**

`docker-compose.yml` tomcat 서비스의 `AI_DAILY_CALL_LIMIT` 줄 아래에 추가:

```yaml
      # 컨테이너 배포는 INFO 를 기본으로 한다. 로컬 mvnw spring-boot:run 은 영향 없음.
      FLOWMATE_LOG_LEVEL: "${FLOWMATE_LOG_LEVEL:-INFO}"
```

- [ ] **Step 3: 반영 확인**

```bash
./mvnw -q clean package -DskipTests
docker compose up -d --force-recreate tomcat
docker compose logs tomcat 2>&1 | grep -c "DEBUG com.flowmate"
```

Expected: `0`

- [ ] **Step 4: 커밋**

```bash
git add src/main/resources/application.yml docker-compose.yml
git commit -m "chore: 로그 레벨을 환경변수로 낮출 수 있게 한다

로컬은 DEBUG 유지, 컨테이너 배포는 INFO. DEBUG 는 공개 배포에서
디스크를 빠르게 채우고 파라미터를 포함한 상세 로그를 남긴다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 6: 개인정보 입력 금지 안내

공개 배포한 순간 방문자가 실제 개인정보를 입력할 수 있고, 그러면 이 서버가 실제 개인정보를 보관하게 된다. 화면에 명시하는 것이 현실적인 방어다.

**Files:**
- Modify: `src/main/webapp/WEB-INF/views/login.jsp:11` 아래
- Modify: `src/main/webapp/WEB-INF/views/approval/write.jsp:31` 위
- Modify: `src/main/webapp/static/css/style.css` (파일 끝)

- [ ] **Step 1: 로그인 화면에 안내 추가**

`src/main/webapp/WEB-INF/views/login.jsp` 에서 아래 줄을 찾는다:

```jsp
    <p class="login-box__subtitle">AI 사전점검 그룹웨어</p>
```

바로 아래에 추가한다:

```jsp
    <p class="demo-notice demo-notice--login">
        포트폴리오 데모입니다. 표시되는 인물·문서·근태는 모두 가상 데이터이며,
        <strong>실제 개인정보를 입력하지 마세요.</strong>
        입력된 내용은 매일 새벽 초기화됩니다.
    </p>
```

- [ ] **Step 2: 기안 작성 화면에 안내 추가**

`src/main/webapp/WEB-INF/views/approval/write.jsp` 에서 아래 줄을 찾는다:

```jsp
        <ol class="doc-steps">
```

바로 **위**에 추가한다:

```jsp
        <p class="demo-notice">
            공개 데모입니다 — <strong>실제 개인정보(주민등록번호·계좌번호·연락처 등)를 입력하지 마세요.</strong>
        </p>
```

- [ ] **Step 3: 스타일 추가**

`src/main/webapp/static/css/style.css` 맨 끝에 추가:

```css
/* ── 공개 데모 안내 ─────────────────────────────────────────────
   경고가 아니라 안내다. alert--error 와 색을 겹치지 않게 해서
   "무언가 잘못됐다"로 읽히지 않도록 한다. */
.demo-notice {
    margin: 0 0 16px;
    padding: 10px 14px;
    border: 1px solid #d8dee9;
    border-left: 3px solid #8899aa;
    border-radius: 4px;
    background: #f6f8fa;
    color: #4a5568;
    font-size: 13px;
    line-height: 1.6;
}

.demo-notice strong {
    color: #2d3748;
}

.demo-notice--login {
    margin-top: 16px;
    text-align: left;
}
```

- [ ] **Step 4: 화면에서 확인**

```bash
./mvnw -q clean package -DskipTests
docker compose up -d --force-recreate tomcat
```

브라우저에서 `http://localhost:18080/flowmate/login` 과 기안 작성 화면을 열어 안내가 보이는지, 기존 배치가 깨지지 않았는지 확인한다.

Expected: 두 화면 모두 안내 문구가 보이고, 버튼·입력란 배치는 그대로다.

- [ ] **Step 5: 커밋**

```bash
git add src/main/webapp/WEB-INF/views/login.jsp src/main/webapp/WEB-INF/views/approval/write.jsp src/main/webapp/static/css/style.css
git commit -m "feat: 공개 데모 안내와 개인정보 입력 금지 문구

공개 배포하면 방문자가 실제 개인정보를 입력할 수 있고 그 순간
이 서버가 실제 개인정보를 보관하게 된다. 화면에 명시하는 것이
현실적인 방어다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 7: 데모 데이터 일일 초기화 스크립트

방문자가 문서를 승인·반려하면 다음 방문자(면접관)는 어질러진 화면을 본다. 시드가 스크립트이므로 DB만 다시 만들면 원상복구된다.

**★ `docker compose down -v` 를 쓰지 않는다.** 볼륨을 지우는 대신 데이터베이스만 다시 만든다 — 컨테이너와 볼륨은 그대로 두므로 다른 상태를 건드리지 않는다.

**Files:**
- Create: `scripts/reset-demo.ps1`

- [ ] **Step 1: 스크립트 작성**

`scripts/reset-demo.ps1` 생성:

```powershell
# FlowMate 데모 데이터 초기화.
#
# 공개 데모에서는 방문자가 문서를 승인·반려하므로 다음 방문자가 어질러진 화면을
# 본다. 시드가 스크립트라서 DB 만 다시 만들면 원상복구된다.
#
# ★ docker compose down -v 를 쓰지 않는다. 볼륨을 지우면 컨테이너 재생성까지
#   따라오고 다른 상태(네트워크·이미지 캐시)도 함께 흔든다. DROP DATABASE 는
#   딱 필요한 것만 지운다.
#
# 사용: powershell -ExecutionPolicy Bypass -File scripts\reset-demo.ps1

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

Write-Host "[1/5] Tomcat 중지 (DB 연결을 끊는다)"
docker compose stop tomcat | Out-Null

Write-Host "[2/5] 데이터베이스 재생성"
# WITH (FORCE) 는 PostgreSQL 13+ 기능. 남은 연결을 끊고 지운다.
docker compose exec -T postgres psql -U flowmate -d postgres -v ON_ERROR_STOP=1 `
    -c "DROP DATABASE IF EXISTS flowmate WITH (FORCE);"
docker compose exec -T postgres psql -U flowmate -d postgres -v ON_ERROR_STOP=1 `
    -c "CREATE DATABASE flowmate;"

Write-Host "[3/5] 시드 스크립트 재실행"
# 초기화 스크립트는 컨테이너에 읽기 전용으로 마운트돼 있으므로 그대로 쓴다.
# 파일명 순서가 의존 순서다(스키마 -> 시드).
$scripts = @(
    '00-extension.sql', '10-schema-org.sql', '11-seed-org.sql',
    '20-schema-approval.sql', '21-seed-approval.sql', '30-schema-ai.sql',
    '40-schema-attendance.sql', '41-seed-attendance.sql',
    '50-seed-demo.sql', '60-schema-ai-features.sql'
)
foreach ($s in $scripts) {
    Write-Host "      - $s"
    docker compose exec -T postgres psql -U flowmate -d flowmate -q -v ON_ERROR_STOP=1 `
        -f "/docker-entrypoint-initdb.d/$s"
}

Write-Host "[4/5] 업로드된 첨부 파일 삭제"
$uploadDir = Join-Path $projectRoot 'upload'
if (Test-Path $uploadDir) {
    Remove-Item -Recurse -Force -Confirm:$false "$uploadDir\*"
}

Write-Host "[5/5] Tomcat 재기동"
docker compose start tomcat | Out-Null

Write-Host "완료: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
```

- [ ] **Step 2: 스크립트를 직접 실행해 본다**

```powershell
powershell -ExecutionPolicy Bypass -File scripts\reset-demo.ps1
```

Expected: 5단계가 오류 없이 끝나고 `완료: ...` 가 출력된다.

- [ ] **Step 3: 초기화 결과 검증**

```bash
docker compose exec -T postgres psql -U flowmate -d flowmate -c "SELECT status, count(*) FROM approval_doc GROUP BY status ORDER BY 2 DESC;"
```

Expected: 시드 원본과 같다.
```
 APPROVED | 131
 REJECTED |  41
 PENDING  |  22
 DRAFT    |   6
 CANCELED |   6
```

- [ ] **Step 4: 앱이 정상인지 확인**

브라우저에서 `http://localhost:18080/flowmate` 로그인 (`2020003` / `flowmate1!`) 후 결재함이 뜨는지 본다.

Expected: 정상 로그인, 결재함에 문서가 보인다.

- [ ] **Step 5: 매일 새벽 4시 자동 실행 등록**

관리자 PowerShell에서:

```powershell
$action = New-ScheduledTaskAction -Execute 'powershell.exe' `
    -Argument "-ExecutionPolicy Bypass -WindowStyle Hidden -File D:\projects\flowmate\scripts\reset-demo.ps1"
$trigger = New-ScheduledTaskTrigger -Daily -At 4am
Register-ScheduledTask -TaskName 'FlowMate-DemoReset' -Action $action -Trigger $trigger -Description 'FlowMate 공개 데모 데이터 초기화'
```

확인:

```powershell
Get-ScheduledTask -TaskName 'FlowMate-DemoReset' | Select-Object TaskName, State
```

Expected: `State` 가 `Ready`

- [ ] **Step 6: 커밋**

```bash
git add scripts/reset-demo.ps1
git commit -m "feat: 데모 데이터 일일 초기화 스크립트

방문자가 문서를 승인·반려하면 다음 방문자가 어질러진 화면을 본다.
시드가 스크립트라 DB 만 다시 만들면 원상복구된다.

docker compose down -v 대신 DROP DATABASE WITH (FORCE) 를 쓴다 -
볼륨을 지우면 컨테이너 재생성까지 따라오고 다른 상태도 함께 흔든다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 8: 배포 리허설 — 운영 구성 그대로 로컬에서

터널을 붙이기 전에 **운영과 같은 환경변수 조합**으로 떠서 정상 동작하는지, 상한이 실제로 걸리는지 확인한다. 터널 문제와 앱 문제를 섞지 않기 위해서다.

**Files:** 없음 (검증만)

- [ ] **Step 1: 운영 환경변수로 기동**

```powershell
$env:AI_ENABLED = 'true'
$env:GEMINI_API_KEY = [Environment]::GetEnvironmentVariable('GEMINI_API_KEY','User')
$env:AI_DAILY_CALL_LIMIT = '200'
$env:FLOWMATE_LOG_LEVEL = 'INFO'
docker compose up -d --force-recreate
```

- [ ] **Step 2: 키가 전달됐는지 길이로만 확인 (값은 출력하지 않는다)**

```powershell
docker compose exec -T tomcat sh -c 'echo ${#GEMINI_API_KEY}'
```

Expected: 30 이상의 숫자. `0` 이면 셸 변수가 비어 있는 것이다.

- [ ] **Step 3: 상한이 실제로 적용됐는지 확인**

```bash
docker compose logs tomcat 2>&1 | grep -i "daily-call-limit\|dailyCallLimit" | head
docker compose exec -T tomcat sh -c 'echo $AI_DAILY_CALL_LIMIT'
```

Expected: `200`

- [ ] **Step 4: 앱 응답 확인**

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:18080/flowmate
```

Expected: `302` (로그인으로 리다이렉트)

- [ ] **Step 5: 브라우저로 AI 기능까지 확인**

`http://localhost:18080/flowmate` → `2020003` / `flowmate1!` 로그인 → 기안 작성 → 유형 선택 → **[AI 제안 받기]** 클릭

Expected: 실제 LLM 응답이 온다. 이후 호출 로그를 확인한다.

```bash
docker compose exec -T postgres psql -U flowmate -d flowmate -c "SELECT feature, success_yn, latency_ms, called_at FROM ai_call_log ORDER BY log_id DESC LIMIT 5;"
```

Expected: 방금 호출한 `DRAFT_HINT` 행이 `success_yn = Y` 로 남아 있다.

- [ ] **Step 6: 상한 동작을 낮은 값으로 실증**

```powershell
$env:AI_DAILY_CALL_LIMIT = '1'
docker compose up -d --force-recreate tomcat
```

브라우저에서 **[AI 제안 받기]** 를 두 번 누른다.

Expected: 두 번째는 폴백 문구가 나온다. 로그에 상한 경고가 남는다.

```bash
docker compose logs tomcat 2>&1 | grep "일일 호출 상한 도달"
```

Expected: `AI 일일 호출 상한 도달 - 호출하지 않고 폴백한다 (feature=DRAFT_HINT, 1/1)`

- [ ] **Step 7: 실제 운영값으로 되돌리고 데이터 초기화**

```powershell
$env:AI_DAILY_CALL_LIMIT = '200'
docker compose up -d --force-recreate tomcat
powershell -ExecutionPolicy Bypass -File scripts\reset-demo.ps1
```

---

## Task 9: Cloudflare Tunnel 연결

공유기 포트포워딩 없이 외부에 노출한다. TLS는 Cloudflare가 처리하므로 Tomcat은 평문 그대로 두고, 접근 경로는 터널 하나로 고정된다.

**Files:**
- Create: `docs/deploy.md`

- [ ] **Step 1: cloudflared 설치**

```powershell
winget install --id Cloudflare.cloudflared
cloudflared --version
```

Expected: 버전 문자열이 출력된다.

- [ ] **Step 2: Cloudflare 계정에 로그인**

```powershell
cloudflared tunnel login
```

브라우저가 열리면 Cloudflare에 등록해 둔 도메인을 선택한다. (무료 도메인을 쓴다면 먼저 Cloudflare에 사이트로 추가하고 네임서버를 Cloudflare 것으로 바꿔 두어야 한다.)

Expected: `~/.cloudflared/cert.pem` 이 생성된다.

- [ ] **Step 3: 터널 생성**

```powershell
cloudflared tunnel create flowmate
cloudflared tunnel list
```

Expected: `flowmate` 터널이 목록에 있고, `~/.cloudflared/<UUID>.json` 자격증명 파일이 생성된다. **이 UUID를 다음 단계에서 쓴다.**

- [ ] **Step 4: 터널 설정 파일 작성**

`C:\Users\<사용자>\.cloudflared\config.yml` 을 만든다. `<UUID>` 와 `<도메인>` 은 앞 단계 결과로 바꾼다.

```yaml
tunnel: <UUID>
credentials-file: C:\Users\<사용자>\.cloudflared\<UUID>.json

ingress:
  - hostname: <도메인>
    service: http://localhost:18080
  - service: http_status:404
```

**★ 이 파일은 저장소에 커밋하지 않는다.** 자격증명 파일 경로와 도메인이 들어 있다.

- [ ] **Step 5: DNS 라우트 등록**

```powershell
cloudflared tunnel route dns flowmate <도메인>
```

Expected: `Added CNAME <도메인> which will route to this tunnel`

- [ ] **Step 6: 터널을 임시로 띄워 확인**

```powershell
cloudflared tunnel run flowmate
```

다른 창에서:

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://<도메인>/flowmate
```

Expected: `302`

- [ ] **Step 7: 외부 네트워크에서 접속 확인**

휴대폰에서 **Wi-Fi를 끄고 모바일 데이터로** `https://<도메인>/flowmate` 에 접속해 로그인해 본다.

Expected: 로그인 화면이 뜨고 `2020003` / `flowmate1!` 로 들어가진다. 주소창에 자물쇠가 보인다.

- [ ] **Step 8: 노출면 재확인 — 터널 외의 경로가 열려 있지 않은지**

외부 네트워크(모바일 데이터)에서 확인한다. `<공인IP>` 는 `curl -s ifconfig.me` 로 얻는다.

```bash
curl -s --max-time 5 -o /dev/null -w "%{http_code}\n" http://<공인IP>:18080/flowmate
curl -s --max-time 5 telnet://<공인IP>:5432
```

Expected: 둘 다 **연결 실패/타임아웃**. 하나라도 응답하면 Task 1이 반영되지 않았거나 공유기에 포트포워딩이 남아 있는 것이다 — 그 경우 즉시 터널을 내리고 원인을 제거한다.

- [ ] **Step 9: 터널을 서비스로 등록해 상시 가동**

`Ctrl+C` 로 임시 실행을 끄고, 관리자 PowerShell에서:

```powershell
cloudflared service install
Get-Service cloudflared | Select-Object Name, Status, StartType
```

Expected: `Status: Running`, `StartType: Automatic`

- [ ] **Step 10: 배포 절차 문서화**

`docs/deploy.md` 생성:

```markdown
# 공개 데모 배포 메모

집 PC에서 Docker로 띄우고 Cloudflare Tunnel로 외부에 노출한다.

## 노출 구조

    인터넷 ──HTTPS──> Cloudflare ──터널──> cloudflared(집 PC) ──> localhost:18080

- Tomcat(18080)과 PostgreSQL(5432)은 `127.0.0.1` 에만 발행한다(`docker-compose.yml`).
  공유기 포트포워딩은 열지 않는다. 외부에서 도달할 수 있는 경로는 터널 하나뿐이다.
- TLS는 Cloudflare가 종단한다. Tomcat은 평문 그대로 둔다.

## 기동

    $env:AI_ENABLED = 'true'
    $env:GEMINI_API_KEY = [Environment]::GetEnvironmentVariable('GEMINI_API_KEY','User')
    $env:AI_DAILY_CALL_LIMIT = '200'
    $env:FLOWMATE_LOG_LEVEL = 'INFO'
    ./mvnw clean package -DskipTests
    docker compose up -d

## 공개 데모에서 켜 두는 것

| 환경변수 | 값 | 이유 |
|---|---|---|
| `AI_DAILY_CALL_LIMIT` | `200` | 데모 계정이 공개돼 있어 상한이 없으면 누구나 요금을 올릴 수 있다 |
| `FLOWMATE_LOG_LEVEL` | `INFO` | DEBUG는 디스크를 빠르게 채우고 파라미터를 남긴다 |

## 데이터 초기화

매일 04:00에 작업 스케줄러(`FlowMate-DemoReset`)가 `scripts/reset-demo.ps1` 을 돌린다.
수동 실행:

    powershell -ExecutionPolicy Bypass -File scripts\reset-demo.ps1

## 점검 항목

배포 후, 그리고 공유기·방화벽을 건드린 뒤에는 외부 네트워크에서 아래를 확인한다.
둘 다 연결 실패여야 한다.

    curl --max-time 5 http://<공인IP>:18080/flowmate
    curl --max-time 5 telnet://<공인IP>:5432

## 커밋하지 않는 것

- `~/.cloudflared/` 의 `cert.pem`, `<UUID>.json`, `config.yml` — 자격증명과 도메인
- API 키 — 환경변수로만 전달한다
```

- [ ] **Step 11: 커밋**

```bash
git add docs/deploy.md
git commit -m "docs: 공개 데모 배포 절차

집 PC + Cloudflare Tunnel. 노출 경로가 터널 하나뿐임을 확인하는
점검 항목을 함께 남긴다.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 10: README에 데모 링크 추가하고 병합

**Files:**
- Modify: `README.md`

- [ ] **Step 1: README 상단에 데모 링크 추가**

`README.md` 의 AI 사용 명시 블록 바로 아래에 추가한다. `<도메인>` 은 실제 값으로 바꾼다.

```markdown
> **라이브 데모** — <https://도메인/flowmate> · 계정 `2020003` / 비밀번호 `flowmate1!`
> 가상 데이터로 동작하며 매일 새벽 4시에 초기화됩니다. 실제 개인정보를 입력하지 마세요.
```

- [ ] **Step 2: 전체 테스트 최종 확인**

```bash
./mvnw -q clean verify 2>&1 | tail -25
```

Expected: 단위·통합 전부 통과. 새로 추가된 테스트로 단위 +6, 통합 +5 가 늘어난다.

- [ ] **Step 3: 비밀값이 섞여 들어가지 않았는지 확인**

```bash
git diff main...HEAD | grep -n -iE "AIza|sk-ant|BEGIN .*PRIVATE KEY|cert\.pem|\.cloudflared" || echo "OK: 비밀값 없음"
```

Expected: `OK: 비밀값 없음`

- [ ] **Step 4: 커밋**

```bash
git add README.md
git commit -m "docs: 라이브 데모 링크

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

- [ ] **Step 5: main에 병합**

```bash
git checkout main
git merge --no-ff feature/phase-7-public-deploy -m "merge: 공개 배포 하드닝 — 루프백 바인딩 + AI 호출 상한 + 데모 초기화

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
git push origin main
```

- [ ] **Step 6: 병합 후 재배포 및 최종 확인**

```powershell
./mvnw -q clean package -DskipTests
docker compose up -d --force-recreate
```

외부 네트워크에서 `https://<도메인>/flowmate` 접속 → 로그인 → 기안 작성 → AI 제안까지 한 번 돌려 본다.

Expected: 전 과정 정상. 이후 `docker compose logs tomcat | grep -i error` 에 새 오류가 없다.

---

## 이 계획이 다루지 않는 것

- **저장소 public 전환과 `v1.0.0` 태그** — 사용자가 직접 수행하는 별도 작업이다.
- **DB 계정 비밀번호 변경** — 5432가 인터넷에서 도달 불가능해지므로 이 배포 구성에서는 공격 표면이 아니다. 다만 나중에 포트를 다시 열게 된다면 반드시 함께 바꿔야 한다.
- **Cloudflare Access(접근 제한)** — 면접관에게 링크만 주면 되는 상황이라 굳이 로그인 벽을 하나 더 두지 않는다. 방문자를 통제하고 싶어지면 그때 붙인다.
- **첨부 파일 디스크 사용량 상한** — 매일 초기화가 사실상 상한 역할을 한다. 하루 만에 디스크를 채울 정도의 트래픽은 이 데모의 위협 모델이 아니다.
