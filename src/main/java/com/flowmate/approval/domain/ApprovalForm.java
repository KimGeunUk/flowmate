package com.flowmate.approval.domain;

import java.math.BigDecimal;

/**
 * 기안 화면 입력. 도메인 객체와 분리한다.
 *
 * 화면이 status·currentStep·docNo 를 보내오더라도 신뢰하지 않기 위한 경계다.
 * 그 값들은 Service 와 도메인 객체가 정한다.
 */
public class ApprovalForm {

    /** null 이면 신규, 값이 있으면 임시저장 문서 수정 */
    private Long approvalId;
    private String docType;
    private String title;
    private String content;
    private BigDecimal amount;

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = (title == null) ? null : title.trim();
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    /** 금액을 비워 보내면 0으로 본다. 결재선 정책이 null 을 다루지 않게 한다 */
    public void setAmount(BigDecimal amount) {
        this.amount = (amount == null) ? BigDecimal.ZERO : amount;
    }
}
