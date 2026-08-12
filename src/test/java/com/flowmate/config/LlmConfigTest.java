package com.flowmate.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.ai.client.FakeLlmClient;
import com.flowmate.ai.domain.AiCallLog;
import com.flowmate.ai.domain.AiResultCache;
import com.flowmate.ai.mapper.AiCallLogMapper;
import com.flowmate.ai.mapper.AiResultCacheMapper;
import com.flowmate.ai.mask.SensitiveDataMasker;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ★ LlmConfig 의 3방향 배선(ai.enabled × ai.provider)을 실제 Spring 조건 평가로
 * 검증한다 (Gemini 추가 - AI 제공자 3종화). DB 도 API 키도 필요 없다 - {@code LlmConfig}
 * 전체를 등록하되 {@code llmClient()} 가 요구하는 나머지 협력 빈(마스커·매퍼)은
 * 이 클래스가 손으로 만든 무동작(no-op) 대역으로 채운다.
 *
 * ★ 왜 @SpringBootTest 가 아니라 ApplicationContextRunner 인가 - PromptRepositoryConfigIT
 * 같은 기존 IT 들은 "설정값 하나 → 컨텍스트가 정상 기동"만 확인하면 충분했지만,
 * 여기서 확인해야 할 것 중 두 가지(provider=claude, provider=gemini)는 키가 없는 이
 * 빌드에서는 "컨텍스트 기동이 실패하는 것 자체가 정상"이다. JUnit5 SpringExtension 이
 * 관리하는 @SpringBootTest 컨텍스트가 기동에 실패하면 그 실패는 테스트 메서드 진입
 * 전에 터져서 테스트 자체가 "실패"로 기록될 뿐 어떤 예외인지 단정할 수 없다.
 * ApplicationContextRunner 는 컨텍스트 기동을 테스트 메서드 안에서 직접 수행하므로
 * "기동이 실패했다 + 그 원인이 의도한 메시지다"를 하나의 통과하는 단정으로 쓸 수 있다.
 * (mvnw test 로 도는 Surefire 테스트다 - DB 불필요.)
 */
class LlmConfigTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesConfig.class, CollaboratorsConfig.class, LlmConfig.class);

    @Test
    @DisplayName("ai.enabled 를 생략하면(기본값 false) FakeLlmClient 가 선택되고 컨텍스트가 정상 기동한다")
    void defaultsToFakeLlmClient() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(FakeLlmClient.class)).isNotNull();
        });
    }

    @Test
    @DisplayName("★ ai.enabled=true + ai.provider=claude, 키 없음 → ANTHROPIC_API_KEY 를 언급하며 기동 실패한다")
    void claudeProviderFailsFastWithoutAnthropicKey() {
        // 아래 NO_CLAUDE_KEY 주석 참고 - 실행하는 사람이 ANTHROPIC_API_KEY 를
        // 설정해 두었더라도 이 테스트는 "키 없음" 조건을 스스로 만든다.
        runner.withPropertyValues("ai.enabled=true", "ai.provider=claude", "ANTHROPIC_API_KEY=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("ANTHROPIC_API_KEY")
                            .hasMessageContaining("ai.enabled=true");
                });
    }

    /**
     * ★ 키를 "없는 상태"로 만드는 것을 테스트가 직접 한다.
     *
     *   원래는 아무것도 주입하지 않고 System.getenv 가 비어 있기를 기대했는데,
     *   그러면 이 테스트가 **실행하는 사람의 환경에 의존한다.** 실제로 개발 중에
     *   GEMINI_API_KEY 를 설정하자마자 아래 두 건이 깨졌다. public 저장소에서는
     *   키를 가진 사람이 흔하므로 그대로 두면 남의 빌드를 깨뜨린다.
     *
     *   LlmConfig 가 Spring Environment 를 통해 읽으므로, 빈 값을 프로퍼티로
     *   덮어쓰면 실제 환경변수가 무엇이든 "없음"이 된다.
     */
    private static final String NO_KEYS = "GEMINI_API_KEY=";
    private static final String NO_CLAUDE_KEY = "ANTHROPIC_API_KEY=";

    @Test
    @DisplayName("★ ai.enabled=true + ai.provider=gemini, 키 없음 → GEMINI_API_KEY 를 언급하며 기동 실패한다")
    void geminiProviderFailsFastWithoutGeminiKey() {
        runner.withPropertyValues("ai.enabled=true", "ai.provider=gemini", NO_KEYS)
                .run(this::assertFailsWithMissingGeminiKeyMessage);
    }

    @Test
    @DisplayName("ai.enabled=true 인데 ai.provider 를 생략하면(기본값 gemini) 여전히 gemini 경로가 선택된다")
    void enabledWithoutProviderStillPicksGemini() {
        // application.yml 의 ai.provider 기본값이 gemini 이므로, provider 를 아예 주지
        // 않은 최소 구성도 gemini 를 골라야 한다 - geminiLlmClient() 의 matchIfMissing=true
        // 가 지키는 계약이다. 키가 없으므로 여전히 실패하지만, 실패 메시지가
        // GEMINI_API_KEY 를 언급해야 "정확히 gemini 쪽이 선택됐다"가 증명된다
        // (claude 쪽이 잘못 선택됐다면 ANTHROPIC_API_KEY 를 언급했을 것이다).
        runner.withPropertyValues("ai.enabled=true", NO_KEYS)
                .run(this::assertFailsWithMissingGeminiKeyMessage);
    }

    private void assertFailsWithMissingGeminiKeyMessage(AssertableApplicationContext context) {
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure())
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GEMINI_API_KEY")
                .hasMessageContaining("ai.enabled=true");
    }

    /** ai.* 프로퍼티 바인딩만 위한 최소 설정 - application.yml 은 로드하지 않는다 */
    @Configuration
    @EnableConfigurationProperties(AiProperties.class)
    static class PropertiesConfig {
    }

    /**
     * {@code LlmConfig.llmClient()} 가 요구하는 협력 빈을 무동작 대역으로 채운다.
     * 이 클래스 자체가 검증 대상이 아니므로(검증 대상은 baseLlmClient 후보 선택뿐)
     * 이 프로젝트의 관례(Mockito 를 새로 들이지 않는다 - FakeAiResultCacheMapper 클래스
     * 주석 참고)를 따라 손으로 만든 최소 구현을 쓴다.
     */
    @Configuration
    static class CollaboratorsConfig {

        @Bean
        SensitiveDataMasker sensitiveDataMasker() {
            return new SensitiveDataMasker();
        }

        @Bean
        AiCallLogMapper aiCallLogMapper() {
            return new AiCallLogMapper() {
                @Override
                public void insert(AiCallLog log) {
                    // no-op - 이 테스트는 baseLlmClient 선택만 본다
                }

                @Override
                public long countSince(LocalDateTime since) {
                    // no-op - 이 테스트는 baseLlmClient 선택만 본다
                    return 0;
                }
            };
        }

        @Bean
        AiResultCacheMapper aiResultCacheMapper() {
            return new AiResultCacheMapper() {
                @Override
                public AiResultCache findByCacheKey(String cacheKey) {
                    return null;
                }

                @Override
                public void insert(AiResultCache cache) {
                    // no-op
                }

                @Override
                public void incrementHitCount(String cacheKey) {
                    // no-op
                }
            };
        }
    }
}
