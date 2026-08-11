package com.flowmate.ai.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StructuredContentBlock;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import java.util.Optional;

/**
 * 실제 Anthropic API 호출 (체인의 맨 안쪽).
 *
 * ★ Claude Opus 5 의 요청 규약 — 아래는 전부 의도된 것이다:
 *
 *   temperature/top_p/top_k 를 넣지 않는다 — Opus 5 에서 제거됐다. 보내면 400 이다.
 *
 *   thinking 을 설정하지 않는다 — Opus 5 는 생략하면 thinking 이 켜진다(기본값 adaptive).
 *     끄는 것은 문서화된 실패 모드가 있어 권장되지 않으므로 켜둔 채 effort 로 조절한다.
 *
 *   maxTokens 4096 — ★ thinking 과 응답 텍스트를 합쳐서 제한한다.
 *     3줄 요약이라고 작게 주면 thinking 이 다 먹고 응답이 잘린다.
 *
 *   effort LOW — 요약·사전점검은 깊은 추론이 필요한 작업이 아니다.
 *
 * ★ API 키는 환경변수 ANTHROPIC_API_KEY 로만 받는다.
 *   fromEnv() 가 직접 읽으므로 코드에도 설정 파일에도 키 문자열이 등장하지 않는다.
 *
 * ★ LlmRequest 에 별도 시스템 프롬프트 필드가 없다 (구조화 출력과 마찬가지로
 *   Phase 5로 미룬 결정). PromptRepository 가 불러온 시스템 프롬프트 템플릿과 실제 문서
 *   본문을 합치는 것은 이 게이트웨이가 아니라 Phase 5 기능 쪽의 책임이므로, 여기서는
 *   request.getPrompt() 하나만 사용자 메시지로 보낸다.
 *
 * ★ 구조화 출력: request.getOutputType 이 있으면
 *   SDK 의 클래스 기반 오버로드(outputConfig(Class))를 쓴다. 스키마를 손으로 쓰지 않고
 *   POJO 에서 자동으로 뽑아내고, 결과도 문자열이 아니라 그 타입으로 돌아온다
 *   (StructuredTextBlock.text() 가 T 를 준다). 이 클래스는 LlmResponse.text 에 항상
 *   문자열을 담아야 하므로(데코레이터·캐시가 그 계약에 맞춰져 있다), 받은 타입 값을
 *   다시 JSON 문자열로 직렬화해 넣는다 - 그래서 인터페이스도 데코레이터도 바뀔 필요가 없다.
 *
 *   구조화 출력과 인용(citations)은 함께 쓸 수 없고, 거절(refusal) 시에는 스키마를
 * 지키지 않는다. 그래서 거절 검사는
 *   구조화 출력 경로에서도 content 를 읽기 전에 그대로 먼저 돈다.
 */
public class ClaudeLlmClient implements LlmClient {

    private final AnthropicClient client;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClaudeLlmClient(String model) {
        this.client = AnthropicOkHttpClient.fromEnv();
        this.model = model;
    }

    @Override
    public Optional<LlmResponse> complete(LlmRequest request) {
        if (request.getOutputType() != null) {
            return completeStructured(request);
        }

        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(4096L)
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.LOW)
                        .build())
                .addUserMessage(request.getPrompt())
                .build();

        Message response = client.messages().create(params);

        // ★ content 를 읽기 전에 거절부터 검사한다.
        //   Opus 5 의 안전 분류기가 요청을 거절하면 HTTP 200 에 빈 content 가 온다.
        //   content.get(0) 을 먼저 하는 코드는 그 자리에서 IndexOutOfBounds 로 깨진다.
        if (isRefusal(response.stopReason())) {
            return Optional.empty();
        }

        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(t -> t.text())
                .reduce("", String::concat);

        if (text.isEmpty()) {
            return Optional.empty();
        }

        LlmResponse result = new LlmResponse();
        result.setText(text);
        result.setModel(response.model().toString());
        result.setInputTokens((int) response.usage().inputTokens());
        result.setOutputTokens((int) response.usage().outputTokens());
        return Optional.of(result);
    }

    private Optional<LlmResponse> completeStructured(LlmRequest request) {
        StructuredMessageCreateParams<?> params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(4096L)
                .outputConfig(request.getOutputType())
                .addUserMessage(request.getPrompt())
                .build();

        StructuredMessage<?> response = client.messages().create(params);

        // 구조화 출력도 거절될 수 있고, 거절되면 스키마를 지키지 않는다 - 평문 경로와
        // 같은 이유로 content 를 읽기 전에 먼저 검사한다.
        if (isRefusal(response.stopReason())) {
            return Optional.empty();
        }

        Optional<?> value = response.content().stream()
                .filter(StructuredContentBlock::isText)
                .map(block -> block.asText().text())
                .findFirst();

        if (value.isEmpty()) {
            return Optional.empty();
        }

        LlmResponse result = new LlmResponse();
        result.setText(toJson(value.get()));
        result.setModel(response.model().toString());
        result.setInputTokens((int) response.usage().inputTokens());
        result.setOutputTokens((int) response.usage().outputTokens());
        return Optional.of(result);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("구조화 출력 결과를 JSON 으로 직렬화할 수 없습니다", e);
        }
    }

    private boolean isRefusal(Object stopReason) {
        return String.valueOf(stopReason).toLowerCase().contains("refusal");
    }
}
