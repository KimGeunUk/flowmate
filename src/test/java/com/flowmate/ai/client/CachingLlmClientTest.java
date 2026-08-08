package com.flowmate.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.ai.domain.AiFeature;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 가장 바깥 데코레이터. cache_key = SHA256(feature:promptVersion:prompt) 로 조회/저장한다
 * (설계서 §6.4.3, 계획서 3 D8).
 *
 * promptVersion 이 캐시 키에 들어가는 이유와 PREFLIGHT 를 캐시하지 않는 이유는
 * CachingLlmClient 클래스 주석 참고.
 */
class CachingLlmClientTest {

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
        CachingLlmClient caching = new CachingLlmClient(fake, cacheMapper);

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
        CachingLlmClient caching = new CachingLlmClient(fake, cacheMapper);
        LlmRequest req = request(AiFeature.SUMMARY, "v1", "요약할 문서");

        caching.complete(req);
        Optional<LlmResponse> second = caching.complete(req);

        assertThat(second).isPresent();
        assertThat(fake.getReceived()).hasSize(1); // 두 번째 호출은 위임하지 않았다
        assertThat(cacheMapper.getIncrementHitCountCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("promptVersion 이 다르면 다른 캐시 키라서 미스가 난다")
    void differentPromptVersionMisses() {
        FakeLlmClient fake = new FakeLlmClient();
        FakeAiResultCacheMapper cacheMapper = new FakeAiResultCacheMapper();
        CachingLlmClient caching = new CachingLlmClient(fake, cacheMapper);

        caching.complete(request(AiFeature.SUMMARY, "v1", "요약할 문서"));
        caching.complete(request(AiFeature.SUMMARY, "v2", "요약할 문서"));

        assertThat(fake.getReceived()).hasSize(2);
        assertThat(cacheMapper.getInsertCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("PREFLIGHT 는 캐시하지 않는다 - 수정 후 재실행이 정상 동작이다")
    void preflightNotCached() {
        FakeLlmClient fake = new FakeLlmClient();
        FakeAiResultCacheMapper cacheMapper = new FakeAiResultCacheMapper();
        CachingLlmClient caching = new CachingLlmClient(fake, cacheMapper);
        LlmRequest req = request(AiFeature.PREFLIGHT, "v1", "상신 전 점검 대상 문서");

        caching.complete(req);
        caching.complete(req);

        assertThat(fake.getReceived()).hasSize(2); // 매번 위임한다
        assertThat(cacheMapper.getInsertCount()).isEqualTo(0);
    }
}
