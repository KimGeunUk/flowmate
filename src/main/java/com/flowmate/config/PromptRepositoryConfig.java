package com.flowmate.config;

import com.flowmate.ai.mapper.PromptMapper;
import com.flowmate.ai.prompt.DatabasePromptRepository;
import com.flowmate.ai.prompt.FilePromptRepository;
import com.flowmate.ai.prompt.PromptRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ★ 커스터마이징 지점 4의 교체 배선 (계획서 5 Task 8, 설계서 §6.4.4·§7).
 *
 * ApprovalPolicyConfig(지점 1)·AttendancePolicyConfig(지점 2·3)와 정확히 같은
 * 형태다 - application.yml 값으로 구현체를 고르고, 설정이 없으면 file 로
 * 떨어진다.
 *
 *   ai.prompt-repository   file(기본값) | database
 *
 * ★ matchIfMissing 은 file 쪽에만 둔다(AttendancePolicyConfig 클래스 주석이 이미
 * 설명한 함정과 완전히 같다) - 두 쪽 다 두면 설정이 비어 있을 때 두 구현이
 * 동시에 매칭되어 NoUniqueBeanDefinitionException 이 컨텍스트 기동 시점에
 * 난다. mvnw test(컨텍스트를 띄우지 않는 단위 테스트)로는 잡히지 않고
 * mvnw verify(@SpringBootTest 통합 테스트)에서만 드러난다 - 그래서 이 프로젝트는
 * "정확히 한쪽에만 matchIfMissing" 규칙으로 원천 차단하고, PromptRepositoryConfigIT/
 * DatabasePromptRepositoryIT 가 각 설정에서 컨텍스트가 정상 기동하는 것으로
 * 그 규칙이 지켜졌음을 실제로 확인한다.
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
