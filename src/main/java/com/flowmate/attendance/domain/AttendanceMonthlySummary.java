package com.flowmate.attendance.domain;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

/**
 * "내 근태" 화면의 한 달치 목록 + 합계 (계획서 4 Task 7).
 *
 * of(rows) 는 순수 함수다 — DB 매퍼를 부르지 않고 이미 조회된 Attendance
 * 목록만으로 합계를 계산한다. 그래서 DB 없이 단위 테스트할 수 있다
 * (BusinessDayCalculator·WorkTimePolicy 와 같은 이유).
 *
 * 합계 4종은 설계서 §6.3이 요구한 것 그대로다: 근무일수·지각·연장·연차사용.
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

    private AttendanceMonthlySummary(List<Attendance> rows, int workingDays, int lateCount,
                                     int overtimeMinutes, BigDecimal leaveUsedDays) {
        this.rows = rows;
        this.workingDays = workingDays;
        this.lateCount = lateCount;
        this.overtimeMinutes = overtimeMinutes;
        this.leaveUsedDays = leaveUsedDays;
    }

    public static AttendanceMonthlySummary of(List<Attendance> rows) {
        if (rows == null) {
            rows = Collections.emptyList();
        }
        int workingDays = 0;
        int lateCount = 0;
        int overtimeMinutes = 0;
        BigDecimal leaveUsedDays = BigDecimal.ZERO;

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
        }
        return new AttendanceMonthlySummary(rows, workingDays, lateCount, overtimeMinutes, leaveUsedDays);
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
}
