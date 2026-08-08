package com.flowmate.ai.domain;

import java.io.Serializable;

/**
 * LLM 호출 요청 한 건. 데코레이터 체인 전체가 이 객체를 그대로 넘긴다 (설계서 §6.4.1).
 *
 * 구조화 출력 스키마를 지금 넣지 않는 이유는 계획서 3 D1 참고 - Phase 5로 미룬다.
 * 필드가 늘어도 데코레이터는 이 객체를 그대로 넘기기만 하므로 손댈 곳이 없다.
 */
public class LlmRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String feature;
    private String promptVersion;
    private String prompt;
    private Long empId;
    private Long approvalId;

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

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
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
}
