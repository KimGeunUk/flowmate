package com.flowmate.config;

import com.flowmate.ai.mapper.PromptMapper;
import com.flowmate.ai.prompt.DatabasePromptRepository;
import com.flowmate.ai.prompt.FilePromptRepository;
import com.flowmate.ai.prompt.PromptRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ★ 커스터마이징 지점 4의 교체 배선.
 *
 * ApprovalPolicyConfig(지점 1)·AttendancePolicyConfig(지점 2·3)와 정확히 같은
 * 형태다 - application.yml 값으로 구현체를 고르고, 설정이 없으면 file 로
 * 떨어진다.
 *
 *   ai.prompt-repository   file(기본값) | database
 *
 * ★ matchIfMissing 은 file 쪽에만 둔다 - 양쪽에 두면 설정이 비어 있을 때 두 구현이
 * 동시에 매칭되어 기동 시점에 NoUniqueBeanDefinitionException 이 난다
 * (AttendancePolicyConfig·LlmConfig 도 같은 규칙을 쓴다).
 * PromptRepositoryConfigIT/DatabasePromptRepositoryIT 가 각 설정에서 컨텍스트가
 * 실제로 뜨는 것을 확인한다.
 */
@Configuration
public class PromptRepositoryConfig {

    @Bean
    @ConditionalOnProperty(name = "ai.prompt-repository", havingValue = "database")
    public PromptRepository databasePromptRepository(PromptMapper promptMapper) {
        return new DatabasePromptRepository(promptMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "ai.prompt-repository", havingValue = "file", matchIfMissing = true)
    public PromptRepository filePromptRepository() {
        return new FilePromptRepository();
    }
}
