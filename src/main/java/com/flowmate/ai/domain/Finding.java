package com.flowmate.ai.domain;

import java.io.Serializable;

/**
 * 사전 점검 지적 항목 한 건.
 *
 * {@code ClaudeLlmClient} 가 {@code outputConfig(PreflightResult.class)} 로 이 POJO 에서
 * JSON 스키마를 뽑아낸다 - 기본 생성자와 getter/setter 만으로 충분하다(SummaryResult 와
 * 같은 규약).
 *
 * ★ basedOnRejectCount 가 이 기능의 존재 이유다. AI 가 "더 자세히
 * 쓰세요" 같은 뻔한 조언을 하는 것이 아니라 "과거 반려 3건에 근거함"을 숫자로 제시해야
 * 한다. 이 숫자는 {@code PreflightService} 가 집계한 {@link RejectPattern#getCount()} 를
 * 프롬프트에 그대로 넣고, 모델이 그 숫자를 인용하도록 지시해서 나온다 -
 * {@code preflight.v1.txt} 가 "제시된 건수를 그대로 사용하라, 임의의 숫자를 만들지
 * 않는다"고 못박은 이유다.
 */
public class Finding implements Serializable {

    private static final long serialVersionUID = 1L;

    private String severity;
    private String category;
    private String message;
    private String suggestion;
    private int basedOnRejectCount;

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public void setSuggestion(String suggestion) {
        this.suggestion = suggestion;
    }

    public int getBasedOnRejectCount() {
        return basedOnRejectCount;
    }

    public void setBasedOnRejectCount(int basedOnRejectCount) {
        this.basedOnRejectCount = basedOnRejectCount;
    }
}
