package com.flowmate.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * ★ 커스터마이징 지점 4 완성의 증명 - 같은
 * {@code (feature, version)} 에 File 과 DB 가 서로 다른 본문을 갖고 있을 때,
 * {@code ai.prompt-repository=database} 로 설정을 바꾸면 실제로 DB 쪽 문구가
 * 나오는 것을 단정한다. Phase 2({@code ApprovalLinePolicy})·Phase 4
 * ({@code WorkTimePolicy}/{@code LeaveGrantPolicy})가 이미 쓴 것과 같은 형태의
 * 교체 증명이다 - 같은 입력(같은 feature+version 키), 다른 설정 → 다른 결과.
 *
 * {@code ai_prompt} 는 시작 시점에 비어 있다 - 이 테스트가 넣는 행은
 * {@code @Transactional} 로 자동 롤백되므로 최종 행 수(0)에 영향이 없다.
 */
@SpringBootTest(properties = "ai.prompt-repository=database")
@Transactional
class DatabasePromptRepositoryIT {

    @Autowired
    private PromptRepository promptRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("★ ai.prompt-repository=database 면 PromptRepository 빈이 DatabasePromptRepository 다")
    void wiresDatabaseImplementationWhenConfigured() {
        assertThat(promptRepository).isInstanceOf(DatabasePromptRepository.class);
    }

    @Test
    @DisplayName("★ 같은 (feature, version) 이라도 File 과 DB 의 내용이 다르면 설정에 따라 다른 문구가 나온다")
    void sameKeyDifferentContentAcrossImplementations() {
        String dbOnlyText = "[DB 전용] 이 문구는 ai_prompt 테이블에만 있다 - 파일에는 없다.";
        jdbcTemplate.update(
                "INSERT INTO ai_prompt (feature, version, body) VALUES (?, ?, ?) "
                        + "ON CONFLICT (feature, version) DO UPDATE SET body = EXCLUDED.body",
                "summary", "v1", dbOnlyText);

        // 이 컨텍스트는 ai.prompt-repository=database 이므로 promptRepository 는
        // DatabasePromptRepository 다 - 같은 (feature="summary", version="v1")
        // 키를 FilePromptRepository 에 직접(Spring 없이) 물어 두 결과를 나란히 비교한다.
        String fromDatabaseConfig = promptRepository.load("summary", "v1");
        String fromFileConfig = new FilePromptRepository().load("summary", "v1");

        assertThat(fromDatabaseConfig).isEqualTo(dbOnlyText);
        assertThat(fromFileConfig).doesNotContain("[DB 전용]");
        assertThat(fromDatabaseConfig).isNotEqualTo(fromFileConfig);
    }

    @Test
    @DisplayName("DB 에 없는 프롬프트를 조회하면 예외를 던진다 - File 구현과 같은 계약")
    void throwsWhenPromptMissingInDatabase() {
        assertThatThrownBy(() -> promptRepository.load("nonexistent-feature", "v99"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
