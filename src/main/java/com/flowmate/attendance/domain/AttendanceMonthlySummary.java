package com.flowmate.attendance.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * "내 근태" 화면의 한 달치 목록 + 합계.
 *
 * of(rows) 는 순수 함수다 — DB 매퍼를 부르지 않고 이미 조회된 Attendance
 * 목록만으로 합계를 계산한다. 그래서 DB 없이 단위 테스트할 수 있다
 * (BusinessDayCalculator·WorkTimePolicy 와 같은 이유).
 *
 * 합계 4종은 화면이 요구하는 그대로다: 근무일수·지각·연장·연차사용.
 * "근무일수"는 출근 기록이 있는 날(checkedIn)로 센다 — 지각·조퇴여도 출근은
 * 했으므로 근무일수에는 포함된다. 지각 횟수와는 별개 지표다.
 */
public final class AttendanceMonthlySummary {

    private static final BigDecimal HALF_DAY = new BigDecimal("0.5");

    private final List<Attendance> rows;
    private final int workingDays;
    private final int lateCount;
    private final int overtimeMinutes;
    private final BigDecimal leaveUsedDays;
    private final int absentCount;

    private AttendanceMonthlySummary(List<Attendance> rows, int workingDays, int lateCount,
                                     int overtimeMinutes, BigDecimal leaveUsedDays, int absentCount) {
        this.rows = rows;
        this.workingDays = workingDays;
        this.lateCount = lateCount;
        this.overtimeMinutes = overtimeMinutes;
        this.leaveUsedDays = leaveUsedDays;
        this.absentCount = absentCount;
    }

    public static AttendanceMonthlySummary of(List<Attendance> rows) {
        if (rows == null) {
            rows = Collections.emptyList();
        }
        int workingDays = 0;
        int lateCount = 0;
        int overtimeMinutes = 0;
        BigDecimal leaveUsedDays = BigDecimal.ZERO;
        int absentCount = 0;

        for (Attendance row : rows) {
            if (row.isCheckedIn()) {
                workingDays++;
            }
            if (AttendanceStatus.LATE.equals(row.getStatus())) {
                lateCount++;
            }
            if (row.getOvertimeMinutes() != null) {
                overtimeMinutes += row.getOvertimeMinutes();
            }
            if (AttendanceStatus.LEAVE.equals(row.getStatus())) {
                leaveUsedDays = leaveUsedDays.add(BigDecimal.ONE);
            } else if (AttendanceStatus.HALF_LEAVE.equals(row.getStatus())) {
                leaveUsedDays = leaveUsedDays.add(HALF_DAY);
            }
            if (AttendanceStatus.ABSENT.equals(row.getStatus())) {
                absentCount++;
            }
        }
        return new AttendanceMonthlySummary(rows, workingDays, lateCount, overtimeMinutes, leaveUsedDays, absentCount);
    }

    public List<Attendance> getRows() {
        return rows;
    }

    public int getWorkingDays() {
        return workingDays;
    }

    public int getLateCount() {
        return lateCount;
    }

    public int getOvertimeMinutes() {
        return overtimeMinutes;
    }

    public BigDecimal getLeaveUsedDays() {
        return leaveUsedDays;
    }

    /**
     * 결근 횟수(연차 맥락 패널의 "최근 3개월" 항목). 기존
     * 4개 합계(근무일수·지각·연장·연차사용)는 Phase 4 화면이 요구한 것이고,
     * 이 필드는 그 화면들을 건드리지 않고 덧붙인 것이다 - of(rows) 는 여전히
     * 순수 함수이므로 기존 호출부는 이 필드의 존재를 몰라도 된다.
     */
    public int getAbsentCount() {
        return absentCount;
    }

    /** 연장근무를 시간 단위로 - 화면(JSP)이 분을 60으로 나누는 계산을 하지 않게 한다 */
    public double getOvertimeHours() {
        return overtimeMinutes / 60.0;
    }
}
