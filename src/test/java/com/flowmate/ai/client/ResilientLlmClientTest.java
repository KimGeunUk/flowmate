package com.flowmate.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.flowmate.ai.domain.AiFeature;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 타임아웃과 예외를 흡수하는 가장 안쪽 데코레이터를 검증한다 (설계서 §6.4.3, 계획서 3 D7).
 *
 * SDK 클라이언트 자체의 타임아웃 설정만으로는 부족해서 ExecutorService + Future.get(timeout)
 * 으로 직접 거는 이유가, 바로 이 클래스처럼 "타임아웃이 실제로 발동하는지" 를 테스트할 수
 * 있어야 하기 때문이다. FakeLlmClient 에게 잠들라고 시켜서 그것을 확인한다.
 */
class ResilientLlmClientTest {

    private LlmRequest request() {
        LlmRequest r = new LlmRequest();
        r.setFeature(AiFeature.SUMMARY);
        r.setPromptVersion("v1");
        r.setPrompt("결재 문서 본문");
        return r;
    }

    @Test
    @DisplayName("원본이 제때 응답하면 그 값을 그대로 돌려준다")
    void passesThroughOnSuccess() {
        FakeLlmClient fake = new FakeLlmClient();
        ResilientLlmClient resilient = new ResilientLlmClient(fake, Duration.ofSeconds(2));

        Optional<LlmResponse> result = resilient.complete(request());

        assertThat(result).isPresent();
        assertThat(fake.getReceived()).hasSize(1);
    }

    @Test
    @DisplayName("지정 시간 안에 응답이 없으면 타임아웃으로 empty 를 돌려준다")
    void timesOutToEmpty() {
        FakeLlmClient fake = new FakeLlmClient();
        fake.setDelayMillis(1000);
        ResilientLlmClient resilient = new ResilientLlmClient(fake, Duration.ofMillis(100));

        Optional<LlmResponse> result = resilient.complete(request());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("원본이 예외를 던지면 밖으로 내보내지 않고 empty 를 돌려준다")
    void exceptionBecomesEmpty() {
        FakeLlmClient fake = new FakeLlmClient();
        fake.setExceptionToThrow(new RuntimeException("AI 호출 실패 시뮬레이션"));
        ResilientLlmClient resilient = new ResilientLlmClient(fake, Duration.ofSeconds(2));

        Optional<LlmResponse> result = resilient.complete(request());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("원본이 정상적으로 empty 를 돌려주면 그대로 empty 를 전달한다")
    void delegateEmptyStaysEmpty() {
        LlmClient alwaysEmpty = req -> Optional.empty();
        ResilientLlmClient resilient = new ResilientLlmClient(alwaysEmpty, Duration.ofSeconds(2));

        Optional<LlmResponse> result = resilient.complete(request());

        assertThat(result).isEmpty();
    }
}
