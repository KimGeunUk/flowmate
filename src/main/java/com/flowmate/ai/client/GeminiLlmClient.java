package com.flowmate.ai.client;

import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import java.util.Optional;

/**
 * 실제 Gemini API 호출 (체인의 맨 안쪽) — {@code ClaudeLlmClient} 와 나란한
 * 커스터마이징 지점 4의 세 번째 구현이다.
 *
 * ★ 구조화 출력: Gemini Java SDK 에는 Claude 처럼 "클래스를 주면 스키마를 자동으로
 *   뽑아 주는" 오버로드가 없다 - {@code Schema} 는 손으로 짓거나 JSON 문자열에서
 *   읽는 방법뿐이다. 그래서 스키마 객체를 만들지 않고 {@code responseMimeType} 만
 *   {@code application/json} 으로 설정하고, 실제 JSON 모양은 프롬프트 템플릿의
 *   지시문에 맡긴다. 응답 파싱이 관대하므로(LlmJson) 스키마를 강제하지 않아도
 *   모양이 어긋난 응답이 기능 전체를 죽이지는 않는다.
 *
 * ★ 예외를 잡지 않는다 - 이 클래스를 감싸는 {@code ResilientLlmClient} 가 유일한
 *   방어선이어야 한다. 여기서 한 번 더 잡으면 무엇이 진짜 방어선인지 흐려진다.
 *   안전 필터에 걸려 텍스트가 없는 응답은 아래 {@code isBlank()} 검사가 걸러낸다.
 */
public class GeminiLlmClient implements LlmClient {

    private static final String JSON_MIME_TYPE = "application/json";

    private final Client client;
    private final String model;

    public GeminiLlmClient(String model) {
        this.client = Client.builder()
                .apiKey(System.getenv("GEMINI_API_KEY"))
                .build();
        this.model = model;
    }

    @Override
    public Optional<LlmResponse> complete(LlmRequest request) {
        GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder();
        if (request.getOutputType() != null) {
            configBuilder.responseMimeType(JSON_MIME_TYPE);
        }

        GenerateContentResponse response =
                client.models.generateContent(model, request.getPrompt(), configBuilder.build());

        String text = response.text();
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }

        LlmResponse result = new LlmResponse();
        result.setText(text);
        result.setModel(response.modelVersion().orElse(model));
        result.setInputTokens(response.usageMetadata()
                .flatMap(GenerateContentResponseUsageMetadata::promptTokenCount)
                .orElse(0));
        result.setOutputTokens(response.usageMetadata()
                .flatMap(GenerateContentResponseUsageMetadata::candidatesTokenCount)
                .orElse(0));
        return Optional.of(result);
    }
}
