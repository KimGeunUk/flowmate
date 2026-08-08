package com.flowmate.ai.client;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import java.util.Optional;

/**
 * 실제 Anthropic API 호출 (설계서 §6.4.1 체인의 맨 안쪽).
 *
 * ★ Claude Opus 5 의 요청 규약 (계획서 3 D6) — 아래는 전부 의도된 것이다:
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
 * ★ LlmRequest 에 별도 시스템 프롬프트 필드가 없다 (계획서 3 D1 - 구조화 출력과 마찬가지로
 *   Phase 5로 미룬 결정). PromptRepository 가 불러온 시스템 프롬프트 템플릿과 실제 문서
 *   본문을 합치는 것은 이 게이트웨이가 아니라 Phase 5 기능 쪽의 책임이므로, 여기서는
 *   request.getPrompt() 하나만 사용자 메시지로 보낸다.
 */
public class ClaudeLlmClient implements LlmClient {

    private final AnthropicClient client;
    private final String model;

    public ClaudeLlmClient(String model) {
        this.client = AnthropicOkHttpClient.fromEnv();
        this.model = model;
    }

    @Override
    public Optional<LlmResponse> complete(LlmRequest request) {
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
        if (isRefusal(response)) {
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

    private boolean isRefusal(Message response) {
        return String.valueOf(response.stopReason()).toLowerCase().contains("refusal");
    }
}
