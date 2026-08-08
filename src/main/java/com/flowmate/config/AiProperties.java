package com.flowmate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * {@code ai.*} 설정 묶음 (application.yml). {@link LlmConfig} 가 체인을 조립할 때
 * 함께 주입받는다.
 *
 * {@code ai.enabled} 이 false(기본값)면 FakeLlmClient, true 면 ClaudeLlmClient 로
 * 배선이 바뀐다 (계획서 3 D3) - 이 값 자체는 {@code @ConditionalOnProperty} 가 직접 읽고,
 * 이 클래스는 나머지(모델명·타임아웃·기능 플래그)를 하나로 묶어 전달하는 역할이다.
 */
@Component
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private boolean enabled = false;
    private String model = "claude-opus-5";
    private int timeoutSeconds = 30;
    private Features features = new Features();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public Features getFeatures() {
        return features;
    }

    public void setFeatures(Features features) {
        this.features = features;
    }

    /** 기능별 on/off. Phase 5 가 기능을 만들 때 이 플래그로 켜고 끈다 - Phase 3 에는 소비자가 없다 */
    public static class Features {
        private boolean summary = true;
        private boolean preflight = true;
        private boolean leaveContext = true;

        public boolean isSummary() {
            return summary;
        }

        public void setSummary(boolean summary) {
            this.summary = summary;
        }

        public boolean isPreflight() {
            return preflight;
        }

        public void setPreflight(boolean preflight) {
            this.preflight = preflight;
        }

        public boolean isLeaveContext() {
            return leaveContext;
        }

        public void setLeaveContext(boolean leaveContext) {
            this.leaveContext = leaveContext;
        }
    }
}
