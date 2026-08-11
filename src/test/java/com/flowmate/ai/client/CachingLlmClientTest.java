package com.flowmate.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.ai.domain.AiFeature;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 가장 바깥 데코레이터. cache_key = SHA256(feature:promptVersion:modelKey:outputType:prompt)
 * 로 조회/저장한다.
 *
 * promptVersion·modelKey 가 캐시 키에 들어가는 이유와 PREFLIGHT 를 캐시하지 않는
 * 이유는 CachingLlmClient 클래스 주석 참고.
 */
class CachingLlmClientTest {

    /** 이 테스트에서 캐시를 채우는 제공자. 모델이 캐시를 가르는지 볼 때만 다른 값을 쓴다 */
    private static final String MODEL_KEY = "gemini:gemini-3.5-flash-lite";

    private LlmRequest request(String feature, String version, String prompt) {
        LlmRequest r = new LlmRequest();
        r.setFeature(feature);
        r.setPromptVersion(version);
        r.setPrompt(prompt);
        return r;
    }

    @Test
    @DisplayName("캐시 미스면 위임하고 결과를 저장한다")
    void missDelegatesAndStores() {
        FakeLlmClient fake = new FakeLlmClient();
        FakeAiResultCacheMapper cacheMapper = new FakeAiResultCacheMapper();
        CachingLlmClient caching = new CachingLlmClient(fake, cacheMapper, MODEL_KEY);

        Optional<LlmResponse> result = caching.complete(request(AiFeature.SUMMARY, "v1", "요약할 문서"));

        assertThat(result).isPresent();
        assertThat(fake.getReceived()).hasSize(1);
        assertThat(cacheMapper.getInsertCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("캐시 히트면 위임하지 않고 hit_count 를 올린다")
    void hitDoesNotDelegateAndIncrementsHitCount() {
        FakeLlmClient fake = new FakeLlmClient();
        FakeAiResultCacheMapper cacheMapper = new FakeAiResultCacheMapper();
        CachingLlmClient caching = new CachingLlmClient(fake, cacheMapper, MODEL_KEY);
        LlmRequest req = request(AiFeature.SUMMARY, "v1", "요약할 문서");

        caching.complete(req);
        Optional<LlmResponse> second = caching.complete(req);

        assertThat(second).isPresent();
        assertThat(fake.getReceived()).hasSize(1); // 두 번째 호출은 위임하지 않았다
        assertThat(cacheMapper.getIncrementHitCountCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("★ 모델이 다르면 다른 캐시 키라서 미스가 난다 — 제공자를 바꾸면 옛 결과가 무효가 된다")
    void differentModelMisses() {
        // 이 단정이 없으면 ai.enabled 를 false 에서 true 로 바꿔도 FakeLlmClient 가
        // 만들어 둔 "[FAKE] 고정 응답입니다" 가 영원히 그대로 나온다. 실제로 겪은
        // 일이다 - 컨테이너에 키를 넣고 AI 를 켰는데 요약이 계속 null 로 왔고
        // ai_call_log 에는 새 호출 기록조차 없었다.
        FakeAiResultCacheMapper cacheMapper = new FakeAiResultCacheMapper();
        LlmRequest req = request(AiFeature.SUMMARY, "v1", "요약할 문서");

        FakeLlmClient fakeProvider = new FakeLlmClient();
        new CachingLlmClient(fakeProvider, cacheMapper, "fake").complete(req);

        // 같은 캐시 테이블을 그대로 두고 제공자만 바꾼다
        FakeLlmClient realProvider = new FakeLlmClient();
        new CachingLlmClient(realProvider, cacheMapper, "gemini:gemini-3.5-flash-lite").complete(req);

        assertThat(realProvider.getReceived())
                .as("제공자가 바뀌었으므로 캐시를 건너뛰고 실제로 호출해야 한다")
                .hasSize(1);
        assertThat(cacheMapper.getIncrementHitCountCalls()).isZero();
        assertThat(cacheMapper.getInsertCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("promptVersion 이 다르면 다른 캐시 키라서 미스가 난다")
    void differentPromptVersionMisses() {
        FakeLlmClient fake = new FakeLlmClient();
        FakeAiResultCacheMapper cacheMapper = new FakeAiResultCacheMapper();
        CachingLlmClient caching = new CachingLlmClient(fake, cacheMapper, MODEL_KEY);

        caching.complete(request(AiFeature.SUMMARY, "v1", "요약할 문서"));
        caching.complete(request(AiFeature.SUMMARY, "v2", "요약할 문서"));

        assertThat(fake.getReceived()).hasSize(2);
        assertThat(cacheMapper.getInsertCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("★ outputType 이 다르면 입력이 같아도 캐시가 미스한다")
    void differentOutputTypeMisses() {
        // 구조화 출력이 생기면 입력이 같아도 스키마(outputType)가 다르면
        // 결과 모양이 달라진다. 캐시 키에 outputType 이 없으면 옛 모양의 캐시를 그대로
        // 돌려줘서, 화면이 새 필드를 읽다가 조용히 null 을 받는다. 이 테스트가 그 부채를
        // 갚았다는 증거다 - 같은 feature·같은 promptVersion·같은 input 인데 outputType
        // 만 다르면 반드시 캐시 미스여야 한다.
        FakeLlmClient fake = new FakeLlmClient();
        FakeAiResultCacheMapper cacheMapper = new FakeAiResultCacheMapper();
        CachingLlmClient caching = new CachingLlmClient(fake, cacheMapper, MODEL_KEY);

        LlmRequest withoutType = request(AiFeature.SUMMARY, "v1", "같은 입력");
        LlmRequest withSampleType = request(AiFeature.SUMMARY, "v1", "같은 입력");
        withSampleType.setOutputType(SampleAiResult.class);
        LlmRequest withAnotherType = request(AiFeature.SUMMARY, "v1", "같은 입력");
        withAnotherType.setOutputType(AnotherAiResult.class);

        caching.complete(withoutType);
        caching.complete(withSampleType);
        caching.complete(withAnotherType);

        // 세 요청 모두 서로 다른 캐시 키를 가져야 하므로 매번 위임하고 매번 저장한다.
        assertThat(fake.getReceived()).hasSize(3);
        assertThat(cacheMapper.getInsertCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("PREFLIGHT 는 캐시하지 않는다 - 수정 후 재실행이 정상 동작이다")
    void preflightNotCached() {
        FakeLlmClient fake = new FakeLlmClient();
        FakeAiResultCacheMapper cacheMapper = new FakeAiResultCacheMapper();
        CachingLlmClient caching = new CachingLlmClient(fake, cacheMapper, MODEL_KEY);
        LlmRequest req = request(AiFeature.PREFLIGHT, "v1", "상신 전 점검 대상 문서");

        caching.complete(req);
        caching.complete(req);

        assertThat(fake.getReceived()).hasSize(2); // 매번 위임한다
        assertThat(cacheMapper.getInsertCount()).isEqualTo(0);
    }

    // ── TTL ──────────────────────────────────

    @Test
    @DisplayName("★ SUMMARY 는 무기한 캐시다 - 시간이 아무리 지나도 히트한다")
    void summaryNeverExpires() {
        FakeLlmClient fake = new FakeLlmClient();
        FakeAiResultCacheMapper cacheMapper = new FakeAiResultCacheMapper();
        CachingLlmClient caching = new CachingLlmClient(fake, cacheMapper, MODEL_KEY);
        LlmRequest req = request(AiFeature.SUMMARY, "v1", "요약할 문서");

        caching.complete(req);
        cacheMapper.ageAllEntries(Duration.ofDays(3650)); // 10년 - 무기한임을 극단적으로 보여준다
        Optional<LlmResponse> second = caching.complete(req);

        assertThat(second).isPresent();
        assertThat(fake.getReceived()).hasSize(1); // 여전히 히트 - 위임하지 않았다
        assertThat(cacheMapper.getIncrementHitCountCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("★ LEAVE_CONTEXT 는 1시간이 지나면 만료돼 미스처럼 동작한다")
    void leaveContextExpiresAfterOneHour() {
        FakeLlmClient fake = new FakeLlmClient();
        FakeAiResultCacheMapper cacheMapper = new FakeAiResultCacheMapper();
        CachingLlmClient caching = new CachingLlmClient(fake, cacheMapper, MODEL_KEY);
        LlmRequest req = request(AiFeature.LEAVE_CONTEXT, "v1", "연차 맥락 캐시 대상");

        caching.complete(req);
        cacheMapper.ageAllEntries(Duration.ofHours(1).plusMinutes(1)); // 딱 1시간을 살짝 넘긴다
        Optional<LlmResponse> second = caching.complete(req);

        assertThat(second).isPresent();
        assertThat(fake.getReceived()).hasSize(2); // 만료 -> 미스 -> 다시 위임했다
        // 만료된 항목을 다시 저장한 것 - insert 가 두 번(신규 1 + 갱신 1) 불렸다.
        assertThat(cacheMapper.getInsertCount()).isEqualTo(2);
        // 갱신된 행은 아직 재사용된 적이 없다 - hit_count 는 다시 0부터다.
        assertThat(cacheMapper.getIncrementHitCountCalls()).isEqualTo(0);
    }

    @Test
    @DisplayName("LEAVE_CONTEXT 는 1시간 이내면 그대로 히트한다")
    void leaveContextHitsWithinOneHour() {
        FakeLlmClient fake = new FakeLlmClient();
        FakeAiResultCacheMapper cacheMapper = new FakeAiResultCacheMapper();
        CachingLlmClient caching = new CachingLlmClient(fake, cacheMapper, MODEL_KEY);
        LlmRequest req = request(AiFeature.LEAVE_CONTEXT, "v1", "연차 맥락 캐시 대상");

        caching.complete(req);
        cacheMapper.ageAllEntries(Duration.ofMinutes(59));
        Optional<LlmResponse> second = caching.complete(req);

        assertThat(second).isPresent();
        assertThat(fake.getReceived()).hasSize(1); // 아직 만료 전이므로 히트
        assertThat(cacheMapper.getIncrementHitCountCalls()).isEqualTo(1);
    }
}
