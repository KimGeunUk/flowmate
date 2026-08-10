package com.flowmate.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.ai.prompt.FilePromptRepository;
import com.flowmate.ai.prompt.PromptRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * ★ 커스터마이징 지점 4의 기본값 배선 확인(계획서 5 Task 8) - application.yml 의
 * {@code ai.prompt-repository: file}(기본값)이면 {@link FilePromptRepository} 가
 * 선택된다. {@code PromptRepositoryConfig} 의 {@code matchIfMissing=true} 가 file
 * 쪽에만 있다는 것을 실제 컨텍스트 기동으로 확인한다 - 그 클래스 주석이 경고하는
 * 대로, 두 조건이 동시에 matchIfMissing 이 되는 실수는 mvnw test 로는 잡히지
 * 않고 mvnw verify(컨텍스트를 실제로 띄우는 이 테스트 같은 것)에서만 드러난다.
 *
 * 이 클래스는 다른 대부분의 IT 와 같은 기본 컨텍스트(속성 오버라이드 없음)를 쓰므로
 * Spring TestContext 캐시를 그대로 재사용한다 - 추가 컨텍스트 기동 비용이 없다.
 */
@SpringBootTest
class PromptRepositoryConfigIT {

    @Autowired
    private PromptRepository promptRepository;

    @Test
    @DisplayName("ai.prompt-repository 를 설정하지 않으면(기본값 file) FilePromptRepository 가 선택된다")
    void defaultsToFileImplementation() {
        assertThat(promptRepository).isInstanceOf(FilePromptRepository.class);
    }
}
