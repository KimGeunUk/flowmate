package com.flowmate.config;

import com.flowmate.ai.client.CachingLlmClient;
import com.flowmate.ai.client.ClaudeLlmClient;
import com.flowmate.ai.client.FakeLlmClient;
import com.flowmate.ai.client.LlmClient;
import com.flowmate.ai.client.LoggingLlmClient;
import com.flowmate.ai.client.MaskingLlmClient;
import com.flowmate.ai.client.ResilientLlmClient;
import com.flowmate.ai.mapper.AiCallLogMapper;
import com.flowmate.ai.mapper.AiResultCacheMapper;
import com.flowmate.ai.mask.SensitiveDataMasker;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ★ 커스터마이징 지점 4의 배선 - AI 제공자 교체와 데코레이터 체인 조립을 모두 이 클래스가 한다.
 *
 * 데코레이터 순서는 설계서 §6.4.1 이 정한 것이고 그 근거가 있다:
 *
 *   Caching (바깥) → 히트하면 마스킹도 API 호출도 안 한다. 비용 0.
 *     Masking     → 실제 호출 직전에 치환한다. 어느 경로로 들어와도 원문이 안 나간다.
 *       Logging   → 마스킹 이후에 로그를 남긴다. 로그에도 원문이 없다.
 *         Resilient (안쪽) → 타임아웃·예외를 흡수한다.
 *           실제 구현 (ClaudeLlmClient 또는 FakeLlmClient)
 *
 *   Masking 이 Caching 보다 안쪽인 것이 특히 중요하다.
 *   뒤집히면 캐시 테이블에 원문이 그대로 저장된다.
 *   LlmChainIT 가 이 순서를 단정한다.
 *
 * ★ 순환 참조 함정 — llmClient() 가 LlmClient 를 반환하면서 동시에 LlmClient 파라미터를
 *   받는다. 실제로 이 프로젝트(Spring Boot 3.5.16 / Spring 6.2)에서 시험해 보니
 *   BeanCurrentlyInCreationException 은 나지 않았다 - Spring 이 타입 후보를 고를 때
 *   "지금 만들고 있는 빈 자기 자신"을 후보에서 제외하기 때문에(자기 참조 배제),
 *   ai.enabled 값에 따라 claudeLlmClient/fakeLlmClient 중 정확히 하나만 활성화되는
 *   이 배선에서는 후보가 단 하나로 좁혀져 모호함이 생기지 않는다.
 *   그래도 그 암묵적 동작에 기대지 않도록 @Qualifier 로 주입 지점을 명시한다 -
 *   나중에 조건 없는 세 번째 LlmClient 빈이 추가되는 순간 암묵적 배제만으로는
 *   후보가 둘로 늘어나 다시 모호해질 수 있기 때문이다.
 */
@Configuration
public class LlmConfig {

    @Bean
    public LlmClient llmClient(@Qualifier("baseLlmClient") LlmClient baseClient,
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
    @Qualifier("baseLlmClient")
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    public LlmClient claudeLlmClient(AiProperties props) {
        return new ClaudeLlmClient(props.getModel());
    }

    /**
     * 기본값. 키 없이 앱이 뜨고 테스트가 돈다.
     * ★ 이것이 커스터마이징 지점 4의 두 번째 구현이다 (계획서 3 D4).
     */
    @Bean
    @Qualifier("baseLlmClient")
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "false", matchIfMissing = true)
    public LlmClient fakeLlmClient() {
        return new FakeLlmClient();
    }
}
