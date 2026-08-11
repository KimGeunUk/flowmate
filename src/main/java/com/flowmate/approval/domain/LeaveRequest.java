package com.flowmate.approval.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 연차 신청서 — approval_doc 의 유형별 확장 (docType=LEAVE 일 때만 존재).
 *
 * approval 모듈이 소유한다. attendance 는 이 테이블을 직접 읽지 않는다 — 승인
 * 반영은 LeaveApplyCommand 값으로만 attendance 에 전달된다.
 */
public class LeaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** approval_doc.approval_id 를 그대로 쓰는 1:1 PK */
    private Long approvalId;
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal days;
    private String reason;

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
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

    public BigDecimal getDays() {
        return days;
    }

    public void setDays(BigDecimal days) {
        this.days = days;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
