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
 * ★ 커스터마이징 지점 4의 배선 - AI 제공자 교체와 데코레이터 체인 조립을 모두 이 클래스가 한다.
 *
 * 데코레이터 순서는 설계서 §6.4.1 이 정한 것이고 그 근거가 있다:
 *
 *   Caching (바깥) → 히트하면 마스킹도 API 호출도 안 한다. 비용 0.
 *     Masking     → 실제 호출 직전에 치환한다. 어느 경로로 들어와도 원문이 안 나간다.
 *       Logging   → 마스킹 이후에 로그를 남긴다. 로그에도 원문이 없다.
 *         Resilient (안쪽) → 타임아웃·예외를 흡수한다.
 *           실제 구현 (ClaudeLlmClient / GeminiLlmClient / FakeLlmClient 중 하나)
 *
 *   ★ Masking 이 실제 호출보다 바깥인 것이 중요하다 — 이게 유출을 막는 지점이다.
 *     실제 LLM 응답은 입력을 인용할 수 있다(요약문에 계좌번호가 들어오는 것은 정상 동작이다).
 *     마스킹이 호출 뒤에 있거나 빠지면 응답 자체가 원문을 실어오고,
 *     그 응답이 ai_result_cache 와 ai_call_log 에 저장된다.
 *     LlmChainIT 가 "실제 호출에 도달한 프롬프트가 이미 마스킹돼 있다"를 단정한다.
 *
 *   Masking 과 Caching 의 상대 순서는 유출과 무관하다 —
 *   ai_result_cache 에는 프롬프트 컬럼이 없고 키는 해시이므로 어느 순서든 원문이 저장되지 않는다.
 *   이 순서가 바꾸는 것은 비용이다: Caching 이 바깥이면 히트 시 마스킹조차 건너뛴다.
 *
 * ★ 제공자 선택은 두 축이다 (ai.enabled, ai.provider) — enabled=false 면 provider 는
 *   아예 안 읽힌다(FakeLlmClient 확정). enabled=true 일 때만 provider 값(claude|gemini)이
 *   의미가 있다. baseLlmClient 자격의 세 빈이 정확히 하나만 매칭되게 하는 규칙은
 *   AttendancePolicyConfig/PromptRepositoryConfig 와 같다 — "기본값 쪽에만
 *   matchIfMissing = true". 여기서는 그 규칙을 두 단계로 적용한다: enabled 축은
 *   false 쪽(fakeLlmClient)에, provider 축은 gemini 쪽(geminiLlmClient, application.yml
 *   기본값과 일치)에 matchIfMissing 을 둔다. 이렇게 하면 ai.enabled=true 인데
 *   ai.provider 를 안 준 상태(예: 최소 구성 테스트)에서도 정확히 하나(geminiLlmClient)만
 *   매칭된다. 세 조건 중 둘 이상이 겹치면 컨텍스트 기동 시점에
 *   NoUniqueBeanDefinitionException 이 난다 - mvnw test 로는 안 잡히고 mvnw verify 에서만
 *   드러난다(AttendancePolicyConfig 클래스 주석이 설명하는 것과 같은 함정).
 *
 * ★ 순환 참조 함정 — llmClient() 가 LlmClient 를 반환하면서 동시에 LlmClient 파라미터를
 *   받는다. 실제로 이 프로젝트(Spring Boot 3.5.16 / Spring 6.2)에서 시험해 보니
 *   BeanCurrentlyInCreationException 은 나지 않았다 - Spring 이 타입 후보를 고를 때
 *   "지금 만들고 있는 빈 자기 자신"을 후보에서 제외하기 때문에(자기 참조 배제),
 *   ai.enabled/ai.provider 값에 따라 claudeLlmClient/geminiLlmClient/fakeLlmClient 중
 *   정확히 하나만 활성화되는 이 배선에서는 후보가 단 하나로 좁혀져 모호함이 생기지
 *   않는다. 그래도 그 암묵적 동작에 기대지 않도록 @Qualifier 로 주입 지점을 명시한다 -
 *   조건 없는 네 번째 LlmClient 빈이 추가되는 순간 암묵적 배제만으로는 후보가 둘로
 *   늘어나 다시 모호해질 수 있기 때문이다.
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

    /**
     * ai.enabled=true 이고 ai.provider=claude 일 때 실제 호출을 배선한다 (계획서 3 D3).
     *
     * ★ 키가 없으면 기동을 막는다. 이유가 있다 — 실측으로 확인한 것이다:
     *
     *   AnthropicOkHttpClient.fromEnv() 는 ANTHROPIC_API_KEY 가 없어도
     *   예외를 던지지 않고 클라이언트를 만들어 준다. 즉 이 검사가 없으면
     *   키 없는 배포가 정상 기동한다.
     *
     *   그다음이 문제다. 첫 호출에서 401 이 나고, 그 401 은 바로 바깥의
     *   ResilientLlmClient 가 설계대로 흡수해 Optional.empty() 로 바꾼다.
     *   화면에는 "AI 기능을 일시적으로 사용할 수 없습니다"가 뜬다.
     *   → **설정 실수가 일시적 장애와 구별되지 않는다.** 아무도 눈치채지 못하고
     *     AI 기능이 영구히 죽은 채로 운영된다.
     *
     *   그래서 기동 시점에 크게 실패시킨다. 배포한 사람이 즉시 알아야 하는 종류의
     *   문제이지, 사용자가 "AI가 안 되는데요"로 알려줄 문제가 아니다.
     */
    @Bean
    @Qualifier("baseLlmClient")
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "ai.provider", havingValue = "claude")
    public LlmClient claudeLlmClient(AiProperties props, Environment env) {
        requireApiKey(env, "ANTHROPIC_API_KEY");
        return new ClaudeLlmClient(props.getModel());
    }

    /**
     * 키 존재를 확인하고 없으면 기동을 막는다.
     *
     * ★ System.getenv 가 아니라 Spring Environment 를 통해 읽는다.
     *   처음에는 System.getenv 를 직접 썼는데, 그러면 이 검사를 확인하는 테스트가
     *   **그 머신의 환경에 의존한다.** 키가 없는 사람은 통과하고 키를 설정해 둔
     *   사람은 실패한다 — 실제로 개발 중에 GEMINI_API_KEY 를 설정한 순간
     *   LlmConfigTest 두 건이 깨졌다. public 저장소에서는 키를 가진 사람이 흔하므로
     *   그대로 두면 남의 빌드를 깨뜨린다.
     *
     *   Environment 는 환경변수를 그대로 읽어주므로(relaxed binding) 운영 동작은
     *   같고, 테스트는 프로퍼티로 빈 값을 주입해 조건을 스스로 만들 수 있다.
     *   "테스트가 조건을 통제한다"가 "테스트가 환경을 관찰한다"보다 항상 낫다.
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

    /**
     * ai.enabled=true 이고 ai.provider=gemini(기본값)일 때 실제 호출을 배선한다.
     * ★ 이것이 LlmClient 의 두 번째 구현이다(README "커스터마이징 지점" 표의 덤 항목 참고) -
     *   Gemini 는 무료 등급이 있어 API 키 없이도 가입만으로 실제 호출을 시연할 수 있다
     *   (Claude 는 유료뿐이다). 데코레이터 체인은 한 줄도 바뀌지 않았다 - 이 빈 하나와
     *   아래 @ConditionalOnProperty 조건 하나가 추가의 전부다.
     *
     * ★ 키가 없으면 기동을 막는다 - claudeLlmClient() 와 정확히 같은 이유·같은 근거다.
     *   Gemini Java SDK 의 Client 도 GEMINI_API_KEY 가 없다고 예외를 던지지 않고
     *   클라이언트를 만들어 준다(빈 문자열/null 을 apiKey 로 받아도 build() 는 성공한다) -
     *   검사가 없으면 키 없는 배포가 정상 기동하고, 첫 호출의 인증 실패가
     *   ResilientLlmClient 에 흡수되어 "일시적 장애"로 보인다. claudeLlmClient() 클래스
     *   주석이 설명하는 함정과 동일하다.
     *
     * ★ matchIfMissing = true 를 provider 조건에 둔 이유: application.yml 기본값이
     *   ai.provider: gemini 이므로, ai.enabled=true 인데 provider 를 아예 안 준 설정도
     *   "gemini 를 쓰겠다"는 뜻으로 해석하는 것이 일관적이다. claudeLlmClient() 의
     *   provider 조건에는 matchIfMissing 을 두지 않는다 - 두 쪽 다 두면 provider 를
     *   생략했을 때 두 빈이 동시에 매칭되어 NoUniqueBeanDefinitionException 이 난다
     *   (클래스 주석 참고).
     */
    @Bean
    @Qualifier("baseLlmClient")
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "true")
    @ConditionalOnProperty(name = "ai.provider", havingValue = "gemini", matchIfMissing = true)
    public LlmClient geminiLlmClient(AiProperties props, Environment env) {
        requireApiKey(env, "GEMINI_API_KEY");
        return new GeminiLlmClient(props.getModel());
    }

    /**
     * 기본값. 키 없이 앱이 뜨고 테스트가 돈다.
     * ★ 이것이 LlmClient 의 세 번째 구현이다 (계획서 3 D4) - Claude/Gemini/Fake 셋 중
     *   "AI 를 실제로 부르지 않는" 쪽이다.
     */
    @Bean
    @Qualifier("baseLlmClient")
    @ConditionalOnProperty(name = "ai.enabled", havingValue = "false", matchIfMissing = true)
    public LlmClient fakeLlmClient() {
        return new FakeLlmClient();
    }
}
