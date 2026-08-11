package com.flowmate.config;

import com.flowmate.ai.client.CachingLlmClient;
import com.flowmate.ai.client.ClaudeLlmClient;
import com.flowmate.ai.client.FakeLlmClient;
import com.flowmate.ai.client.GeminiLlmClient;
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
import org.springframework.core.env.Environment;

/**
 * ★ 커스터마이징 지점 4의 배선 - AI 제공자 교체와 데코레이터 체인 조립.
 *
 * <pre>
 *   Caching (바깥)  히트하면 마스킹도 API 호출도 하지 않는다
 *     Masking       실제 호출 직전에 치환한다
 *       Logging     마스킹 이후라 로그에도 원문이 없다
 *         Resilient 타임아웃·예외를 흡수한다
 *           Claude / Gemini / Fake 중 하나
 * </pre>
 *
 * ★ Masking 이 실제 호출보다 바깥인 것이 유출을 막는 지점이다. LLM 응답은 입력을
 * 인용할 수 있으므로(요약문에 계좌번호가 들어오는 것은 정상 동작이다) 마스킹이
 * 호출 뒤에 있으면 응답이 원문을 실어 오고, 그 응답이 캐시와 로그에 저장된다.
 * LlmChainIT 가 "실제 호출에 도달한 프롬프트가 이미 마스킹돼 있다"를 단정한다.
 * 반면 Masking 과 Caching 의 상대 순서는 유출과 무관하다 - ai_result_cache 에는
 * 프롬프트 컬럼이 없다. 그 순서가 바꾸는 것은 비용뿐이다.
 *
 * ★ 제공자 선택은 두 축이다(ai.enabled, ai.provider). 축마다 기본값 쪽에만
 * matchIfMissing 을 둔다 - enabled 축은 false(fake), provider 축은 gemini.
 * 양쪽에 두면 provider 를 생략했을 때 두 빈이 동시에 매칭되어 기동 시점에
 * NoUniqueBeanDefinitionException 이 난다. mvnw test 로는 안 잡히고 verify 에서만
 * 드러나는 종류의 실수다.
 *
 * ★ llmClient() 가 LlmClient 를 반환하면서 LlmClient 를 주입받는다. Spring 이
 * 자기 자신을 후보에서 빼기 때문에 실제로 순환 참조는 나지 않지만, 조건 없는
 * 네 번째 LlmClient 빈이 생기면 다시 모호해진다. @Qualifier 로 못박아 둔다.
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
        chain = new CachingLlmClient(chain, cacheMapper, modelKey(props));
        return chain;
    }

    /**
     * 캐시 키에 들어갈 "누가 이 답을 만들었는가"(이유는 CachingLlmClient 주석).
     *
     * 꺼져 있을 때 props.getModel() 을 쓰지 않는 것이 핵심이다. 그 값은 AI 를 꺼
     * 두어도 gemini-3.5-flash-lite 로 남아 있어서, 모델명만 쓰면 꺼진 상태와 켠
     * 상태가 같은 키가 된다.
     */
    private String modelKey(AiProperties props) {
        return props.isEnabled() ? props.getProvider() + ":" + props.getModel() : "fake";
    }

    @Bean
    @Qualifier("baseLlmClient")
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "ai.provider", havingValue = "claude")
    public LlmClient claudeLlmClient(AiProperties props, Environment env) {
        requireApiKey(env, "ANTHROPIC_API_KEY");
        return new ClaudeLlmClient(props.getModel());
    }

    @Bean
    @Qualifier("baseLlmClient")
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "ai.provider", havingValue = "gemini", matchIfMissing = true)
    public LlmClient geminiLlmClient(AiProperties props, Environment env) {
        requireApiKey(env, "GEMINI_API_KEY");
        return new GeminiLlmClient(props.getModel());
    }

    /** 기본값. 키 없이 앱이 뜨고 테스트가 돈다. */
    @Bean
    @Qualifier("baseLlmClient")
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "false", matchIfMissing = true)
    public LlmClient fakeLlmClient() {
        return new FakeLlmClient();
    }

    /**
     * 키가 없으면 기동을 막는다.
     *
     * ★ 두 SDK 모두 키가 없어도 예외를 던지지 않고 클라이언트를 만들어 준다.
     * 이 검사가 없으면 키 없는 배포가 정상 기동하고, 첫 호출의 401 을 바깥의
     * ResilientLlmClient 가 설계대로 흡수해 "AI 를 일시적으로 사용할 수 없습니다"로
     * 바꾼다. 그러면 **설정 실수가 일시적 장애와 구별되지 않아** 아무도 눈치채지
     * 못한 채 AI 기능이 영구히 죽는다. 배포한 사람이 즉시 알아야 하는 문제다.
     *
     * ★ System.getenv 가 아니라 Environment 로 읽는다. 직접 읽으면 이 검사를
     * 확인하는 테스트가 그 머신의 환경에 의존하게 된다 - 키를 가진 사람의 빌드만
     * 깨진다. Environment 는 환경변수를 그대로 읽어 주므로 운영 동작은 같으면서,
     * 테스트는 프로퍼티로 빈 값을 주입해 조건을 스스로 만들 수 있다.
     */
    private void requireApiKey(Environment env, String name) {
        String apiKey = env.getProperty(name);
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "ai.enabled=true 인데 환경변수 " + name + " 가 없습니다. "
                    + "키를 설정하거나 ai.enabled 를 false 로 두십시오. "
                    + "(false 로 두면 FakeLlmClient 가 배선되어 AI 기능만 비활성화되고 나머지는 정상 동작합니다)");
        }
    }
}
