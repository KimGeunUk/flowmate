package com.flowmate.ai.domain;

import java.io.Serializable;

/**
 * LLM 호출 응답. 텍스트뿐 아니라 토큰 수와 모델명을 함께 나른다 (계획서 3 D2).
 *
 * {@code LoggingLlmClient} 가 {@code ai_call_log.input_tokens} 를 기록하고
 * {@code CachingLlmClient} 가 {@code ai_result_cache.model} 을 저장해야 하는데,
 * 그 값은 체인 맨 안쪽의 {@code ClaudeLlmClient} 만 안다. 응답이 문자열뿐이면
 * 바깥 데코레이터가 알 방법이 없다.
 */
public class LlmResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String text;
    private int inputTokens;
    private int outputTokens;
    private String model;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public int getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(int inputTokens) {
        this.inputTokens = inputTokens;
    }

    public int getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(int outputTokens) {
        this.outputTokens = outputTokens;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }
}
