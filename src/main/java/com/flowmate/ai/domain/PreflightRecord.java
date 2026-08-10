package com.flowmate.ai.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code ai_preflight_result} 행 (설계서 §5.5, 계획서 5 Task 5).
 *
 * ApprovalDoc 이 DB 컬럼과 "조회 표시용" 필드를 함께 갖는 것과 같은 방식으로,
 * 이 클래스도 두 종류의 필드를 섞는다:
 *   - DB 컬럼:      resultId · approvalId · verdict · findingsJson · ignoredYn · checkedAt
 *   - 표시/응답용:  findings (findingsJson 을 디코드하지 않고, PreflightService 가
 *                   LLM 응답을 파싱한 List<Finding> 을 그대로 채워 컨트롤러가
 *                   basedOnRejectCount 까지 포함한 구조화 JSON 을 화면에 돌려주게 한다)
 *
 * findingsJson 은 REST 응답에 그대로 노출하지 않는다({@code @JsonIgnore}) - findings
 * 필드가 이미 같은 내용을 구조화된 형태로 담고 있어 중복이고, 원시 JSON 문자열을 다시
 * JSON 안에 문자열로 얹는 것은 화면 쪽에서 다루기 번거롭다.
 *
 * ★ PASS 판정은 이 테이블에 남기지 않는다(PreflightService 의 결정, 계획서가 명시하지
 * 않아 이 Phase 가 내린 선택). ai_preflight_result 의 존재 이유가 테이블 코멘트에 적힌
 * 대로 "경고를 무시하고 상신했는지 추적"이므로, 경고가 없었던 호출까지 남기면 그 측정의
 * 신호가 옅어진다. resultId 가 있는 행은 전부 "실제로 화면에 WARN 모달을 띄웠던 순간"이다.
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
