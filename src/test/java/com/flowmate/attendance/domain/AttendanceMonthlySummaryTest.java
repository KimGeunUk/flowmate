package com.flowmate.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * of(rows) 는 순수 함수라 DB 없이 테스트한다.
 */
class AttendanceMonthlySummaryTest {

    @Test
    @DisplayName("빈 목록이면 합계도 전부 0이다")
    void emptyRowsProduceZeroSummary() {
        AttendanceMonthlySummary summary = AttendanceMonthlySummary.of(List.of());

        assertThat(summary.getRows()).isEmpty();
        assertThat(summary.getWorkingDays()).isZero();
        assertThat(summary.getLateCount()).isZero();
        assertThat(summary.getOvertimeMinutes()).isZero();
        assertThat(summary.getLeaveUsedDays()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("null 목록도 빈 목록과 같이 처리한다")
    void nullRowsTreatedAsEmpty() {
        AttendanceMonthlySummary summary = AttendanceMonthlySummary.of(null);

        assertThat(summary.getRows()).isEmpty();
        assertThat(summary.getWorkingDays()).isZero();
    }

    @Test
    @DisplayName("근무일수는 출근 기록이 있는 날을 센다 — 지각·조퇴여도 포함된다")
    void workingDaysCountsCheckedInDaysRegardlessOfLateness() {
        List<Attendance> rows = List.of(
                row(LocalDate.of(2026, 6, 1), "09:10", AttendanceStatus.LATE),
                row(LocalDate.of(2026, 6, 2), "09:00", AttendanceStatus.EARLY_LEAVE),
                row(LocalDate.of(2026, 6, 3), "09:00", AttendanceStatus.NORMAL));

        AttendanceMonthlySummary summary = AttendanceMonthlySummary.of(rows);

        assertThat(summary.getWorkingDays()).isEqualTo(3);
        assertThat(summary.getLateCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("연차 행은 출근 기록이 없으므로 근무일수에서 빠지고, 연차사용에 1일로 잡힌다")
    void leaveRowIsNotAWorkingDayButCountsTowardLeaveUsed() {
        List<Attendance> rows = List.of(leaveRow(LocalDate.of(2026, 6, 5), AttendanceStatus.LEAVE));

        AttendanceMonthlySummary summary = AttendanceMonthlySummary.of(rows);

        assertThat(summary.getWorkingDays()).isZero();
        assertThat(summary.getLeaveUsedDays()).isEqualByComparingTo("1.0");
    }

    @Test
    @DisplayName("반차는 연차사용에 0.5일로 잡힌다")
    void halfLeaveCountsAsHalfDay() {
        List<Attendance> rows = List.of(leaveRow(LocalDate.of(2026, 6, 5), AttendanceStatus.HALF_LEAVE));

        AttendanceMonthlySummary summary = AttendanceMonthlySummary.of(rows);

        assertThat(summary.getLeaveUsedDays()).isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("연장근무는 행마다 합산된다")
    void overtimeMinutesSumsAcrossRows() {
        List<Attendance> rows = List.of(
                row(LocalDate.of(2026, 6, 1), "09:00", AttendanceStatus.NORMAL, 30),
                row(LocalDate.of(2026, 6, 2), "09:00", AttendanceStatus.NORMAL, 90));

        AttendanceMonthlySummary summary = AttendanceMonthlySummary.of(rows);

        assertThat(summary.getOvertimeMinutes()).isEqualTo(120);
    }

    private Attendance row(LocalDate date, String checkInTime, String status) {
        return row(date, checkInTime, status, 0);
    }

    private Attendance row(LocalDate date, String checkInTime, String status, int overtimeMinutes) {
        Attendance attendance = new Attendance();
        attendance.setWorkDate(date);
        attendance.setCheckIn(date.atTime(java.time.LocalTime.parse(checkInTime)));
        attendance.setStatus(status);
        attendance.setOvertimeMinutes(overtimeMinutes);
        return attendance;
    }

    /** 연차 반영은 checkIn 을 채우지 않는다 (DefaultLeaveApplyService.apply 참조) */
    private Attendance leaveRow(LocalDate date, String status) {
        Attendance attendance = new Attendance();
        attendance.setWorkDate(date);
        attendance.setStatus(status);
        attendance.setOvertimeMinutes(0);
        return attendance;
    }
}
