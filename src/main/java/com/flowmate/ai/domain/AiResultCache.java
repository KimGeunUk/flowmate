package com.flowmate.ai.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 호출 결과 캐시 한 행 (ai_result_cache).
 *
 * {@code cacheKey} 는 {@code SHA256(feature + ":" + promptVersion + ":" + prompt)} 의 hex다.
 * {@code CachingLlmClient} 만 이 값을 계산하고 읽고 쓴다.
 */
public class AiResultCache implements Serializable {

    private static final long serialVersionUID = 1L;

    private String cacheKey;
    private String feature;
    private String promptVersion;
    private String resultJson;
    private String model;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer hitCount;
    private LocalDateTime createdAt;

    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public String getFeature() {
        return feature;
    }

    public void setFeature(String feature) {
        this.feature = feature;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public void setPromptVersion(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getInputTokens() {
        return inputTokens;
    }

    public void setInputTokens(Integer inputTokens) {
        this.inputTokens = inputTokens;
    }

    public Integer getOutputTokens() {
        return outputTokens;
    }

    public void setOutputTokens(Integer outputTokens) {
        this.outputTokens = outputTokens;
    }

    public Integer getHitCount() {
        return hitCount;
    }

    public void setHitCount(Integer hitCount) {
        this.hitCount = hitCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
