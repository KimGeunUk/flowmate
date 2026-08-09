package com.flowmate.attendance.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 연차 반영 이력 (설계서 §5.4). 결재 승인 1건당 정확히 1행 —
 * approval_id 가 UNIQUE 다 (중복 반영 방지, 계획서 4 D2).
 *
 * DefaultLeaveApplyService 가 existsByApprovalId 로 먼저 조회해 분기하므로,
 * 이 UNIQUE 제약은 정상 경로에서는 걸리지 않는다 — 걸린다면 조회-후-분기
 * 로직이 뚫린 것이므로 최후 방어선으로 예외가 트랜잭션 전체를 롤백시키는
 * 것이 올바른 동작이다.
 */
public class LeaveUsage implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long usageId;
    private Long empId;
    private Long approvalId;
    private String leaveType;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal days;
    private LocalDateTime appliedAt;

    public Long getUsageId() {
        return usageId;
    }

    public void setUsageId(Long usageId) {
        this.usageId = usageId;
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

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }
}
