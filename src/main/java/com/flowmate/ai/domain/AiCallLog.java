package com.flowmate.ai.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 호출 로그 한 행 (ai_call_log). 성공·실패 무관하게 전부 남긴다.
 *
 * {@code emp_id} / {@code approval_id} 에 FK 가 없는 이유는 원본이 지워져도
 * 호출 이력은 남아야 하기 때문이다 (30-schema-ai.sql 주석 참고).
 */
public class AiCallLog implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long logId;
    private String feature;
    private String promptVersion;
    private Long empId;
    private Long approvalId;
    private Integer inputTokens;
    private Integer outputTokens;
    private Integer latencyMs;
    private String successYn;
    private String errorMsg;
    private LocalDateTime calledAt;

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
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

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
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

    public Integer getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Integer latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getSuccessYn() {
        return successYn;
    }

    public void setSuccessYn(String successYn) {
        this.successYn = successYn;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public LocalDateTime getCalledAt() {
        return calledAt;
    }

    public void setCalledAt(LocalDateTime calledAt) {
        this.calledAt = calledAt;
    }
}
