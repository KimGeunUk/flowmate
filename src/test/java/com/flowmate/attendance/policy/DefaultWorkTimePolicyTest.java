package com.flowmate.attendance.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.flowmate.attendance.domain.AttendanceStatus;
import com.flowmate.attendance.domain.WorkTimeResult;

/**
 * 09:00 시작 · 18:00 종료 · 소정 8시간 정책.
 *
 * 이 클래스가 지키는 함정 3가지:
 *   1. 점심시간 1시간을 빼야 한다 — 정시 출퇴근이 연장근무로 잡히면 안 된다
 *   2. 퇴근 미등록은 ABSENT 가 아니다
 *   3. 지각·조퇴가 동시에 발생하면 LATE 가 우선한다
 */
class DefaultWorkTimePolicyTest {

    private final WorkTimePolicy policy = new DefaultWorkTimePolicy();
    private static final LocalDate DATE = LocalDate.of(2026, 8, 10);

    private static LocalDateTime at(String time) {
        return LocalDateTime.of(DATE, LocalTime.parse(time));
    }

    @Test
    @DisplayName("★ 정시 출퇴근(09:00~18:00)은 점심시간 1시간을 빼면 소정 8시간이라 연장근무가 없다")
    void standardDayHasNoOvertimeAfterLunchDeduction() {
        WorkTimeResult result = policy.evaluate(at("09:00"), at("18:00"), DATE);

        assertThat(result.getWorkMinutes()).isEqualTo(480);
        assertThat(result.getOvertimeMinutes()).isZero();
        assertThat(result.getStatus()).isEqualTo(AttendanceStatus.NORMAL);
    }

    @Test
    @DisplayName("09:00 정각 출근은 지각이 아니다")
    void checkInExactlyAtNineIsNotLate() {
        WorkTimeResult result = policy.evaluate(at("09:00"), at("18:10"), DATE);

        assertThat(result.getStatus()).isEqualTo(AttendanceStatus.NORMAL);
    }

    @Test
    @DisplayName("09:01 이후 출근은 지각이다")
    void checkInAfterNineIsLate() {
        WorkTimeResult result = policy.evaluate(at("09:01"), at("18:10"), DATE);

        assertThat(result.getStatus()).isEqualTo(AttendanceStatus.LATE);
    }

    @Test
    @DisplayName("18:00 이전 퇴근은 조퇴다")
    void checkOutBeforeSixIsEarlyLeave() {
        WorkTimeResult result = policy.evaluate(at("09:00"), at("17:00"), DATE);

        assertThat(result.getStatus()).isEqualTo(AttendanceStatus.EARLY_LEAVE);
    }

    @Test
    @DisplayName("★ 지각과 조퇴가 동시에 일어나면 LATE 가 우선한다 (상태는 하나뿐이다)")
    void lateTakesPriorityOverEarlyLeave() {
        WorkTimeResult result = policy.evaluate(at("09:30"), at("17:00"), DATE);

        assertThat(result.getStatus()).isEqualTo(AttendanceStatus.LATE);
    }

    @Test
    @DisplayName("★ 퇴근 미등록은 ABSENT 가 아니다 — 판정을 유보하고 근무시간은 0으로 둔다")
    void missingCheckOutIsNotAbsent() {
        WorkTimeResult result = policy.evaluate(at("09:00"), null, DATE);

        assertThat(result.getWorkMinutes()).isZero();
        assertThat(result.getStatus()).isNotEqualTo(AttendanceStatus.ABSENT);
        assertThat(result.getStatus()).isNull();
    }

    @Test
    @DisplayName("소정 8시간을 넘긴 근무는 초과분이 연장근무로 잡힌다")
    void overtimeBeyondContractedEightHoursIsCounted() {
        WorkTimeResult result = policy.evaluate(at("09:00"), at("19:30"), DATE);

        assertThat(result.getWorkMinutes()).isEqualTo(570);
        assertThat(result.getOvertimeMinutes()).isEqualTo(90);
        assertThat(result.getStatus()).isEqualTo(AttendanceStatus.NORMAL);
    }
}
