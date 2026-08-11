package com.flowmate.ai.domain;

import java.io.Serializable;

/**
 * LLM 호출 요청 한 건. 데코레이터 체인 전체가 이 객체를 그대로 넘긴다.
 *
 * ★ outputType: 구조화 출력이 필요한 호출은 이 필드에
 * 결과를 담을 POJO 의 Class 를 넣는다. {@code ClaudeLlmClient} 가 이 값이 있으면
 * Anthropic SDK 의 클래스 기반 오버로드(outputConfig(Class))를 써서 스키마를 자동
 * 생성하고 타입으로 결과를 받는다. null 이면 지금까지와 같은 평문 응답 경로다.
 *
 * 데코레이터들은 이 객체를 그대로 넘기기만 하면 되므로 대부분 손댈 곳이 없다 - 단,
 * {@code MaskingLlmClient} 처럼 필드를 복사해 새 인스턴스를 만드는 곳은 outputType 도
 * 같이 복사해야 한다. 빠뜨리면 마스킹 단계에서 구조화 출력 요청이 조용히 평문 요청으로
 * 바뀐다(MaskingLlmClientTest.preservesOutputTypeUnchanged 가 이걸 잡는다).
 */
public class LlmRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private String feature;
    private String promptVersion;
    private String prompt;
    private Long empId;
    private Long approvalId;
    private Class<?> outputType;

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

    public Class<?> getOutputType() {
        return outputType;
    }

    public void setOutputType(Class<?> outputType) {
        this.outputType = outputType;
    }
}
