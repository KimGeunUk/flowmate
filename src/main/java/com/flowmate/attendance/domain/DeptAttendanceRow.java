package com.flowmate.attendance.domain;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * "부서 근태 현황" 화면의 사원별 한 줄.
 *
 * AttendanceMapper#findDeptMonthlySummary 의 GROUP BY 결과를 그대로 담는
 * 조회 전용 DTO 다 — Attendance(일별 원본 테이블 행)와는 다른 모양이라
 * 별도 클래스로 둔다. employee LEFT JOIN attendance 이므로 그 달에 근태
 * 행이 하나도 없는 사원도 0으로 채워진 행으로 나온다(누락되지 않는다).
 */
public class DeptAttendanceRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long empId;
    private String empName;
    private String deptName;
    private Integer workingDays;
    private Integer lateCount;
    private Integer overtimeMinutes;
    private BigDecimal leaveUsedDays;

    public Long getEmpId() {
        return empId;
    }

    public void setEmpId(Long empId) {
        this.empId = empId;
    }

    public String getEmpName() {
        return empName;
    }

    public void setEmpName(String empName) {
        this.empName = empName;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public Integer getWorkingDays() {
        return workingDays;
    }

    public void setWorkingDays(Integer workingDays) {
        this.workingDays = workingDays;
    }

    public Integer getLateCount() {
        return lateCount;
    }

    public void setLateCount(Integer lateCount) {
        this.lateCount = lateCount;
    }

    public Integer getOvertimeMinutes() {
        return overtimeMinutes;
    }

    public void setOvertimeMinutes(Integer overtimeMinutes) {
        this.overtimeMinutes = overtimeMinutes;
    }

    public BigDecimal getLeaveUsedDays() {
        return leaveUsedDays;
    }

    public void setLeaveUsedDays(BigDecimal leaveUsedDays) {
        this.leaveUsedDays = leaveUsedDays;
    }
}
