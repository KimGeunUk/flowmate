package com.flowmate.ai.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code ai_preflight_result} 행.
 *
 * DB 컬럼과 응답용 필드를 함께 갖는다. findings 는 findingsJson 을 디코드한 것이
 * 아니라 PreflightService 가 LLM 응답을 파싱한 값을 그대로 채운 것이고,
 * findingsJson 은 REST 응답에서 뺀다({@code @JsonIgnore}) - 같은 내용이 중복이고
 * JSON 안에 JSON 문자열을 얹으면 화면에서 다루기 번거롭다.
 *
 * ★ PASS 판정은 이 테이블에 남기지 않는다. 이 표의 존재 이유가 "경고를 무시하고
 * 상신했는지 추적"이므로 경고가 없던 호출까지 남기면 측정 신호가 옅어진다.
 * 여기 있는 행은 전부 실제로 WARN 모달을 띄웠던 순간이다.
 */
public class PreflightRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long resultId;
    private Long approvalId;
    private String verdict;
    private String ignoredYn;
    private LocalDateTime checkedAt;

    @JsonIgnore
    private String findingsJson;

    private List<Finding> findings;

    public Long getResultId() {
        return resultId;
    }

    public void setResultId(Long resultId) {
        this.resultId = resultId;
    }

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public String getVerdict() {
        return verdict;
    }

    public void setVerdict(String verdict) {
        this.verdict = verdict;
    }

    public String getIgnoredYn() {
        return ignoredYn;
    }

    public void setIgnoredYn(String ignoredYn) {
        this.ignoredYn = ignoredYn;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }

    public String getFindingsJson() {
        return findingsJson;
    }

    public void setFindingsJson(String findingsJson) {
        this.findingsJson = findingsJson;
    }

    public List<Finding> getFindings() {
        return findings;
    }

    public void setFindings(List<Finding> findings) {
        this.findings = findings;
    }
}
