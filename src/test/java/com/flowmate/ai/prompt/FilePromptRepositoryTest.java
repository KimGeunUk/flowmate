package com.flowmate.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * classpath:prompts/{feature}.{version}.txt 조회. 없으면 예외, 있으면 캐시한다.
 */
class FilePromptRepositoryTest {

    private final FilePromptRepository repository = new FilePromptRepository();

    @Test
    @DisplayName("존재하는 프롬프트를 읽어온다")
    void loadsExistingPrompt() {
        String prompt = repository.load("summary", "v1");

        assertThat(prompt).contains("당신은 한국 기업의 전자결재 문서를 요약합니다.");
    }

    @Test
    @DisplayName("없는 프롬프트를 조회하면 예외를 던진다 - 조용히 빈 문자열을 돌려주지 않는다")
    void throwsWhenPromptMissing() {
        assertThatThrownBy(() -> repository.load("nonexistent", "v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("같은 요청을 두 번 하면 파일을 다시 읽지 않고 캐시된 것을 돌려준다")
    void cachesAfterFirstRead() {
        String first = repository.load("summary", "v1");
        String second = repository.load("summary", "v1");

        assertThat(second).isSameAs(first);
    }

    @Test
    @DisplayName("한글이 깨지지 않고 그대로 읽힌다 - RRN 토큰 안내 문구까지 포함한다")
    void readsKoreanTextCorrectly() {
        String prompt = repository.load("summary", "v1");

        assertThat(prompt).contains("[[RRN_1]]");
        assertThat(prompt).contains("그 자리의 실제 값을 추측하지 말고 토큰을 그대로 두십시오.");
    }
}
