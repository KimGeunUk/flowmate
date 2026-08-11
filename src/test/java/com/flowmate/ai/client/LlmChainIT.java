package com.flowmate.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.ai.domain.AiFeature;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code LlmConfig} 가 실제로 조립한 체인(Caching → Masking → Logging → Resilient → 실구현)을
 * 통째로 검증한다 (D8).
 *
 * {@code ai.enabled=false}(기본값)로 테스트가 돌기 때문에 체인의 맨 안쪽은
 * {@link FakeLlmClient} 다. 이 클래스가 "받은 요청을 기록"하는 능력 덕분에, 체인 밖에서
 * 무엇이 실제로 맨 안쪽까지 도달했는지를 단정할 수 있다.
 *
 * ★ 1번({@code originalTextNeverReachesTheInnerClient})과
 *   2번({@code cacheStoresMaskedTextNotOriginal})이 이 Phase 전체의 존재 이유다.
 *   "원문이 외부로 나가지 않는다"는 주장를 단위 테스트가 아니라
 *   조립된 체인 수준에서 고정하는 것이 이 둘의 역할이다.
 */
@SpringBootTest
@Transactional
class LlmChainIT {

    private static final String RRN = "901231-1234567";

    @Autowired
    private LlmClient llmClient;

    @Autowired
    private FakeLlmClient fakeLlmClient;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * {@code fakeLlmClient} 는 싱글턴이라 {@code @Transactional} 의 DB 롤백과 무관하게
     * 테스트 사이에 상태(받은 요청 목록·지연·예외·에코 설정)가 그대로 남는다.
     * 매 테스트 전에 초기화해서 실행 순서에 관계없이 결과가 같도록 만든다.
     */
    @BeforeEach
    void resetFake() {
        fakeLlmClient.getReceived().clear();
        fakeLlmClient.setDelayMillis(0);
        fakeLlmClient.setExceptionToThrow(null);
        fakeLlmClient.setEchoPrompt(false);

        LlmResponse baseline = new LlmResponse();
        baseline.setText("[TEST] 체인 통합 테스트 기본 응답");
        baseline.setModel("fake-model");
        baseline.setInputTokens(5);
        baseline.setOutputTokens(5);
        fakeLlmClient.setFixedResponse(baseline);
    }

    @Test
    @DisplayName("★ 주민번호가 든 프롬프트도 원문이 FakeLlmClient 까지 도달하지 않는다 - D8 핵심")
    void originalTextNeverReachesTheInnerClient() {
        LlmRequest request = newRequest(AiFeature.SUMMARY, "v-mask-1",
                "직원 정보: 주민번호 " + RRN + " 확인 요청");

        llmClient.complete(request);

        LlmRequest received = fakeLlmClient.lastRequest();
        assertThat(received).isNotNull();
        assertThat(received.getPrompt()).doesNotContain(RRN);
        assertThat(received.getPrompt()).contains("[[RRN_1]]");
    }

    @Test
    @DisplayName("★ 캐시에 저장된 결과에도 원문이 없다 - 캐싱이 바깥·마스킹이 안쪽인 순서의 증명")
    void cacheStoresMaskedTextNotOriginal() {
        // FakeLlmClient 가 받은 프롬프트를 그대로 응답으로 돌려주게 한다 - 실제 Claude 가
        // 요약 중 입력 내용을 일부 그대로 옮기는 상황을 흉내낸다. 고정 문자열을 그냥
        // 저장하면 무엇을 넣어도 이 단정이 항상 참이 되는 공허한 테스트가 된다.
        fakeLlmClient.setEchoPrompt(true);
        LlmRequest request = newRequest(AiFeature.SUMMARY, "v-mask-2",
                "직원 정보: 주민번호 " + RRN + " 확인 요청");

        llmClient.complete(request);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT result_json FROM ai_result_cache WHERE feature = ? AND prompt_version = ?",
                AiFeature.SUMMARY, "v-mask-2");

        String resultJson = String.valueOf(row.get("result_json"));
        assertThat(resultJson).doesNotContain(RRN);
        assertThat(resultJson).contains("[[RRN_1]]");
    }

    @Test
    @DisplayName("같은 요청을 두 번 보내면 두 번째는 캐시가 처리하고 위임하지 않는다")
    void secondCallHitsCacheWithoutDelegating() {
        String prompt = "3월 출장비 정산 540,000원 요약 요청";

        llmClient.complete(newRequest(AiFeature.SUMMARY, "v-hit-1", prompt));
        llmClient.complete(newRequest(AiFeature.SUMMARY, "v-hit-1", prompt));

        assertThat(fakeLlmClient.getReceived()).hasSize(1);

        Integer hitCount = jdbcTemplate.queryForObject(
                "SELECT hit_count FROM ai_result_cache WHERE feature = ? AND prompt_version = ?",
                Integer.class, AiFeature.SUMMARY, "v-hit-1");
        assertThat(hitCount).isEqualTo(1);
    }

    @Test
    @DisplayName("promptVersion 만 다르면 캐시가 미스하고 다시 위임한다")
    void differentPromptVersionMissesCache() {
        String prompt = "3월 출장비 정산 540,000원 요약 요청";

        llmClient.complete(newRequest(AiFeature.SUMMARY, "v-diff-1", prompt));
        llmClient.complete(newRequest(AiFeature.SUMMARY, "v-diff-2", prompt));

        assertThat(fakeLlmClient.getReceived()).hasSize(2);
    }

    @Test
    @DisplayName("AI 호출이 실패해도 로그는 남고, 체인은 예외 대신 empty 를 돌려준다")
    void callIsLoggedEvenOnFailure() {
        fakeLlmClient.setExceptionToThrow(new RuntimeException("의도된 테스트 실패"));

        Optional<LlmResponse> result = llmClient.complete(
                newRequest(AiFeature.PREFLIGHT, "v-fail-1", "사전점검 대상 문서 본문"));

        // 폴백 원칙: AI 실패가 업무 실패가 되어서는 안 된다 - 예외가 아니라 empty.
        assertThat(result).isEmpty();

        Map<String, Object> log = jdbcTemplate.queryForMap(
                "SELECT success_yn FROM ai_call_log WHERE feature = ? AND prompt_version = ?",
                AiFeature.PREFLIGHT, "v-fail-1");
        assertThat(log.get("success_yn")).isEqualTo("N");
    }

    @Test
    @DisplayName("★ 구현을 바꾸면 같은 요청도 다른 결과를 낸다 - 커스터마이징 지점 4 교체 증명 (D4)")
    void twoImplementationsProduceDifferentResults() {
        LlmRequest request = newRequest(AiFeature.SUMMARY, "v-swap-1", "동일한 입력");

        LlmClient implA = fakeLlmClient;

        FakeLlmClient implB = new FakeLlmClient();
        LlmResponse responseB = new LlmResponse();
        responseB.setText("[TEST] 다른 구현이 돌려주는 다른 응답");
        responseB.setModel("other-fake-model");
        responseB.setInputTokens(99);
        responseB.setOutputTokens(99);
        implB.setFixedResponse(responseB);

        Optional<LlmResponse> resultA = implA.complete(request);
        Optional<LlmResponse> resultB = implB.complete(request);

        assertThat(resultA).isPresent();
        assertThat(resultB).isPresent();
        assertThat(resultA.get().getText()).isNotEqualTo(resultB.get().getText());
        assertThat(resultA.get().getModel()).isNotEqualTo(resultB.get().getModel());
    }

    // ── TTL - 실제 Postgres 에서 ON CONFLICT 갱신까지 확인한다 ──

    @Test
    @DisplayName("★ LEAVE_CONTEXT 캐시는 1시간이 지나면 실제 DB 에서도 만료돼 다시 위임한다")
    void leaveContextCacheExpiresAfterOneHourInRealDb() {
        LlmRequest request = newRequest(AiFeature.LEAVE_CONTEXT, "v-ttl-1", "연차 맥락 캐시 만료 테스트");

        llmClient.complete(request);
        assertThat(fakeLlmClient.getReceived()).hasSize(1);

        // created_at 을 1시간 10분 전으로 되돌려 만료를 흉내낸다 - CachingLlmClientTest
        // (단위 테스트, FakeAiResultCacheMapper)와 같은 판정 로직을 실제 DB 값으로 검증한다.
        jdbcTemplate.update(
                "UPDATE ai_result_cache SET created_at = created_at - INTERVAL '70 minutes' "
                        + "WHERE feature = ? AND prompt_version = ?",
                AiFeature.LEAVE_CONTEXT, "v-ttl-1");

        llmClient.complete(request);

        assertThat(fakeLlmClient.getReceived()).hasSize(2); // 만료 -> 미스 -> 다시 위임

        // ON CONFLICT DO UPDATE 로 갱신된 행 - 같은 cache_key 이므로 행은 여전히 하나다.
        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_result_cache WHERE feature = ? AND prompt_version = ?",
                Integer.class, AiFeature.LEAVE_CONTEXT, "v-ttl-1");
        assertThat(rowCount).isEqualTo(1);

        Integer hitCount = jdbcTemplate.queryForObject(
                "SELECT hit_count FROM ai_result_cache WHERE feature = ? AND prompt_version = ?",
                Integer.class, AiFeature.LEAVE_CONTEXT, "v-ttl-1");
        assertThat(hitCount).isEqualTo(0); // 갱신된 행은 아직 재사용된 적이 없다
    }

    @Test
    @DisplayName("SUMMARY 캐시는 실제 DB 에서도 무기한이다 - 아무리 오래돼도 만료되지 않는다")
    void summaryCacheNeverExpiresInRealDb() {
        LlmRequest request = newRequest(AiFeature.SUMMARY, "v-ttl-2", "요약 캐시는 무기한 유지된다");

        llmClient.complete(request);
        jdbcTemplate.update(
                "UPDATE ai_result_cache SET created_at = created_at - INTERVAL '10000 hours' "
                        + "WHERE feature = ? AND prompt_version = ?",
                AiFeature.SUMMARY, "v-ttl-2");

        llmClient.complete(request);

        assertThat(fakeLlmClient.getReceived()).hasSize(1); // 여전히 히트 - 위임하지 않았다
    }

    private LlmRequest newRequest(String feature, String promptVersion, String prompt) {
        LlmRequest request = new LlmRequest();
        request.setFeature(feature);
        request.setPromptVersion(promptVersion);
        request.setPrompt(prompt);
        request.setEmpId(18L);
        return request;
    }
}
