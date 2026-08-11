package com.flowmate.approval.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 기안 화면 입력. 도메인 객체와 분리한다.
 *
 * 화면이 status·currentStep·docNo 를 보내오더라도 신뢰하지 않기 위한 경계다.
 * 그 값들은 Service 와 도메인 객체가 정한다.
 *
 * leaveType/startDate/endDate/reason 은 docType=LEAVE 일 때만 쓰는 필드다.
 * days 는 여기 없다 — 서버(BusinessDayCalculator)가 계산한다. 기안자가 일수를
 * 직접 입력하게 하면 금요일~월요일을 4일로 적는 실수가 반드시 난다.
 */
public class ApprovalForm {

    /** null 이면 신규, 값이 있으면 임시저장 문서 수정 */
    private Long approvalId;
    private String docType;
    private String title;
    private String content;
    private BigDecimal amount;

    // docType=LEAVE 전용
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;

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

    public String getLeaveType() {
        return leaveType;
    }

    public void setLeaveType(String leaveType) {
        this.leaveType = leaveType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
