package com.flowmate.ai.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@code ai_prompt} 테이블 조회 - 없으면 예외, 있으면 캐시한다(FilePromptRepositoryTest 와
 * 같은 계약을 검증한다). TTL 만료는 실제 DB 없이 {@link FakePromptMapper} +
 * 테스트 전용 생성자(짧은 TTL)로 확인한다 - DatabasePromptRepository 클래스 주석 참고.
 */
class DatabasePromptRepositoryTest {

    @Test
    @DisplayName("존재하는 프롬프트를 읽어온다")
    void loadsExistingPrompt() {
        FakePromptMapper mapper = new FakePromptMapper();
        mapper.put("summary", "v1", "DB 프롬프트 본문");
        DatabasePromptRepository repository = new DatabasePromptRepository(mapper);

        assertThat(repository.load("summary", "v1")).isEqualTo("DB 프롬프트 본문");
    }

    @Test
    @DisplayName("없는 프롬프트를 조회하면 예외를 던진다 - 조용히 빈 문자열을 돌려주지 않는다 (FilePromptRepository 와 같은 계약)")
    void throwsWhenPromptMissing() {
        FakePromptMapper mapper = new FakePromptMapper();
        DatabasePromptRepository repository = new DatabasePromptRepository(mapper);

        assertThatThrownBy(() -> repository.load("nonexistent", "v1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("같은 요청을 두 번 하면 DB 를 다시 조회하지 않고 캐시된 것을 돌려준다")
    void cachesAfterFirstRead() {
        FakePromptMapper mapper = new FakePromptMapper();
        mapper.put("summary", "v1", "DB 프롬프트 본문");
        DatabasePromptRepository repository = new DatabasePromptRepository(mapper);

        String first = repository.load("summary", "v1");
        String second = repository.load("summary", "v1");

        assertThat(second).isSameAs(first);
        assertThat(mapper.getFindBodyCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("★ FilePromptRepository 와 달리 TTL 이 지나면 다시 DB 를 조회한다 - 재배포 없이 바뀐 값을 반영하기 위함")
    void reReadsAfterTtlExpires() throws InterruptedException {
        FakePromptMapper mapper = new FakePromptMapper();
        mapper.put("summary", "v1", "DB 프롬프트 본문 - 1차");
        DatabasePromptRepository repository = new DatabasePromptRepository(mapper, Duration.ofMillis(20));

        String first = repository.load("summary", "v1");
        assertThat(first).isEqualTo("DB 프롬프트 본문 - 1차");

        // 관리자가(지금은 이 테스트가) 재배포 없이 DB 값을 바꿨다고 가정한다.
        mapper.put("summary", "v1", "DB 프롬프트 본문 - 2차(갱신됨)");
        Thread.sleep(30); // TTL(20ms) 을 넘긴다

        String second = repository.load("summary", "v1");

        assertThat(second).isEqualTo("DB 프롬프트 본문 - 2차(갱신됨)");
        assertThat(mapper.getFindBodyCalls()).isEqualTo(2);
    }

    @Test
    @DisplayName("TTL 이내면 DB 값이 바뀌어도 캐시된 옛 값을 그대로 돌려준다")
    void staleWithinTtlWindow() {
        FakePromptMapper mapper = new FakePromptMapper();
        mapper.put("summary", "v1", "DB 프롬프트 본문 - 1차");
        DatabasePromptRepository repository = new DatabasePromptRepository(mapper, Duration.ofMinutes(5));

        String first = repository.load("summary", "v1");
        mapper.put("summary", "v1", "DB 프롬프트 본문 - 2차(아직 반영 전)");
        String second = repository.load("summary", "v1");

        assertThat(second).isEqualTo(first);
        assertThat(mapper.getFindBodyCalls()).isEqualTo(1);
    }
}
