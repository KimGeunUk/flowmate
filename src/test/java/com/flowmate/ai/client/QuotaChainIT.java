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
