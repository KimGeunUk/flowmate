package com.flowmate.ai.client;

import com.flowmate.ai.domain.LlmRequest;
import com.flowmate.ai.domain.LlmResponse;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import java.util.Optional;

/**
 * 실제 Gemini API 호출 (설계서 §6.4.1 체인의 맨 안쪽) — {@code ClaudeLlmClient} 와 나란한
 * 커스터마이징 지점 4의 세 번째 구현이다.
 *
 * ★ 이 클래스가 존재하는 이유 자체가 증명이다 — 데코레이터 체인({@code CachingLlmClient} →
 *   {@code MaskingLlmClient} → {@code LoggingLlmClient} → {@code ResilientLlmClient})은
 *   한 줄도 손대지 않았다. {@code LlmClient} 구현을 하나 더 만들고 {@code LlmConfig} 의
 *   조건부 배선에 가지 하나를 추가했을 뿐이다. "제공자 교체가 싸다"는 이 프로젝트의
 *   핵심 주장을, 테스트 대역이 아닌 서로 다른 두 회사의 실제 API로 증명한다.
 *
 * ★ API 키는 환경변수 GEMINI_API_KEY 로만 받는다. {@code LlmConfig.geminiLlmClient} 가
 *   기동 시점에 이미 그 값을 검사해 없으면 애플리케이션을 아예 띄우지 않으므로
 *   (ClaudeLlmClient 클래스 주석과 같은 이유·같은 함정), 여기서는 그 값을 다시 읽어
 *   {@code Client} 를 만들기만 한다 — 키 부재를 다시 검사하지 않는다(이미 걸렀다).
 *
 * ★ 구조화 출력 (계획서 5 D2): Gemini Java SDK 에는 Claude 처럼 "클래스를 주면 스키마를
 *   자동으로 뽑아 타입으로 돌려주는" 오버로드가 없다 — SDK 의 {@code Schema} 는
 *   {@code Schema.builder()} 로 손으로 짓거나 {@code Schema.fromJson(String)} 뿐이고,
 *   POJO 에서 자동 유도하는 헬퍼가 없다. 그래서 이 클래스는 스키마 객체를 만들지
 *   않고 {@code responseMimeType} 을 {@code "application/json"} 으로만 설정한다.
 *   실제 JSON 모양은 {@code PromptRepository} 가 불러온 프롬프트 템플릿
 *   (summary.v1.txt/preflight.v1.txt)의 지시문이 책임진다 — {@code SummaryService}/
 *   {@code PreflightService} 둘 다 응답을 관대하게 파싱하고(Jackson 파싱 실패 시
 *   {@code Optional.empty()}) 실패해도 예외를 던지지 않으므로, 스키마 없이 MIME
 *   타입만 지정해도 충분하다(설계서 5 D8 폴백 원칙과 같은 방향 — 스키마 강제가
 *   실패하면 요청 자체를 실패시키는 것이 아니라, 어차피 있는 관대한 파싱에 맡긴다).
 *
 * ★ 거절(Claude 의 refusal)에 준하는 별도 검사를 하지 않는다 — 안전 필터에 걸리는 등
 *   응답에 실제 텍스트가 없는 경우는 {@code text()} 가 빈 문자열을 주거나 예외를
 *   던질 수 있는데, 전자는 아래 {@code isBlank()} 검사로, 후자는 바로 다음 항목의
 *   원칙대로 잡지 않고 그대로 올려보낸다.
 *
 * ★ 예외를 잡지 않는다 — {@code ClaudeLlmClient} 와 같은 이유(그 클래스 주석의 D8):
 *   이 클래스를 감싸는 {@code ResilientLlmClient} 가 유일한 방어선이어야 한다.
 *   여기서 한 번 더 잡으면 "무엇이 진짜 방어선인지"가 흐려진다.
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
