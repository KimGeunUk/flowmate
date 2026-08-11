package com.flowmate.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmate.ai.domain.AiFeature;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * outputType 이 있을 때 {@link FakeLlmClient} 가 그 타입에 맞는 JSON 을 돌려주는지 검증한다
 * {@code ClaudeLlmClient} 의 구조화 출력 경로는 실제 API 호출 없이는
 * 테스트할 수 없으므로, 키 없이 이 경로를 훈련시키는 대역이 FakeLlmClient 다.
 */
class FakeLlmClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LlmRequest request(Class<?> outputType) {
        LlmRequest r = new LlmRequest();
        r.setFeature(AiFeature.SUMMARY);
        r.setPromptVersion("v1");
        r.setPrompt("입력");
        r.setOutputType(outputType);
        return r;
    }

    @Test
    @DisplayName("outputType 이 없으면 지금까지처럼 고정 평문 응답을 그대로 돌려준다")
    void withoutOutputTypeReturnsFixedResponse() {
        FakeLlmClient fake = new FakeLlmClient();

        Optional<LlmResponse> result = fake.complete(request(null));

        assertThat(result).isPresent();
        assertThat(result.get().getText()).contains("FAKE");
    }

    @Test
    @DisplayName("outputType 이 있으면 그 타입으로 역직렬화되는 JSON 을 돌려준다")
    void withOutputTypeReturnsParsableJson() throws Exception {
        FakeLlmClient fake = new FakeLlmClient();

        Optional<LlmResponse> result = fake.complete(request(SampleAiResult.class));

        assertThat(result).isPresent();
        SampleAiResult parsed = objectMapper.readValue(result.get().getText(), SampleAiResult.class);
        assertThat(parsed).isNotNull();
    }

    @Test
    @DisplayName("테스트가 타입별로 등록해 둔 고정값이 있으면 그 값을 JSON 으로 돌려준다")
    void returnsRegisteredFixedResultForType() throws Exception {
        FakeLlmClient fake = new FakeLlmClient();
        SampleAiResult expected = new SampleAiResult();
        expected.setValue("고정값");
        fake.setFixedResult(SampleAiResult.class, expected);

        Optional<LlmResponse> result = fake.complete(request(SampleAiResult.class));

        SampleAiResult parsed = objectMapper.readValue(result.get().getText(), SampleAiResult.class);
        assertThat(parsed.getValue()).isEqualTo("고정값");
    }

    @Test
    @DisplayName("★ outputType 이 다르면 서로 다른 모양의 JSON 을 돌려준다 - 같은 대역으로 두 기능을 함께 테스트할 수 있어야 한다")
    void differentOutputTypesProduceDifferentShapes() throws Exception {
        FakeLlmClient fake = new FakeLlmClient();

        String sampleJson = fake.complete(request(SampleAiResult.class)).get().getText();
        String anotherJson = fake.complete(request(AnotherAiResult.class)).get().getText();

        assertThat(objectMapper.readValue(sampleJson, SampleAiResult.class)).isNotNull();
        assertThat(objectMapper.readValue(anotherJson, AnotherAiResult.class)).isNotNull();
    }
}
