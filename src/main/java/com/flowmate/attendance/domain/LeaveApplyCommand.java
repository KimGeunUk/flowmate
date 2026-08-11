package com.flowmate.attendance.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * approval → attendance 경계를 넘는 값 (★ 모듈 의존은 한 방향이다).
 *
 * approvalId 만 넘기면 attendance 가 사원·기간·일수를 알아내려고 approval 의
 * leave_request 테이블을 읽어야 하고, 그러면 attendance → approval 호출이
 * 생겨 의존이 양방향(순환)이 된다. 그래서 approval 이 자기 테이블을 읽어
 * 이 값을 조립해 넘기고, attendance 는 받은 값만 쓴다 — approvalId 는
 * leave_usage 에 남길 참조(중복 반영 방지 조회 키, D2)로만 쓰인다.
 *
 * 불변 값 객체다 — approval.policy.ApproverCandidate(지점 1)와 같은 패턴.
 */
public final class LeaveApplyCommand implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Long approvalId;
    private final Long empId;
    private final String leaveType;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final BigDecimal days;

    public LeaveApplyCommand(Long approvalId, Long empId, String leaveType,
                             LocalDate startDate, LocalDate endDate, BigDecimal days) {
        this.approvalId = approvalId;
        this.empId = empId;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.days = days;
    }

    public Long getApprovalId() {
        return approvalId;
    }

    public Long getEmpId() {
        return empId;
    }

    public String getLeaveType() {
        return leaveType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BigDecimal getDays() {
        return days;
    }
}
