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
 * 자율출퇴근제 — 지각·조퇴 개념이 없다. 총 근무시간(점심시간 제외)만 본다.
 *
 * 이 클래스의 존재 이유는 기능이 아니라 **교체 가능성의 증명**이다.
 * 마지막 테스트가 같은 입력에 두 정책이 서로 다른 판정을 내는 것을 나란히 보여준다.
 */
class FlexWorkTimePolicyTest {

    private final WorkTimePolicy flex = new FlexWorkTimePolicy();
    private final WorkTimePolicy defaultPolicy = new DefaultWorkTimePolicy();
    private static final LocalDate DATE = LocalDate.of(2026, 8, 10);

    private static LocalDateTime at(String time) {
        return LocalDateTime.of(DATE, LocalTime.parse(time));
    }

    @Test
    @DisplayName("점심시간을 뺀 총 근무시간이 8시간 이상이면 정상이다")
    void eightHoursNetIsNormal() {
        WorkTimeResult result = flex.evaluate(at("09:00"), at("18:00"), DATE);

        assertThat(result.getWorkMinutes()).isEqualTo(480);
        assertThat(result.getStatus()).isEqualTo(AttendanceStatus.NORMAL);
    }

    @Test
    @DisplayName("총 근무시간이 8시간 미만이면 지각·조퇴 여부와 무관하게 조퇴(부족)다")
    void lessThanEightHoursIsEarlyLeave() {
        WorkTimeResult result = flex.evaluate(at("09:00"), at("17:00"), DATE);

        assertThat(result.getWorkMinutes()).isEqualTo(420);
        assertThat(result.getStatus()).isEqualTo(AttendanceStatus.EARLY_LEAVE);
    }

    @Test
    @DisplayName("늦게 출근해도 총 근무시간 8시간을 채우면 정상이다 — 지각 개념이 없다")
    void lateCheckInStillNormalIfEightHoursWorked() {
        WorkTimeResult result = flex.evaluate(at("09:30"), at("18:30"), DATE);

        assertThat(result.getWorkMinutes()).isEqualTo(480);
        assertThat(result.getStatus()).isEqualTo(AttendanceStatus.NORMAL);
    }

    @Test
    @DisplayName("퇴근 미등록은 이 정책에서도 ABSENT 가 아니다")
    void missingCheckOutIsNotAbsent() {
        WorkTimeResult result = flex.evaluate(at("09:00"), null, DATE);

        assertThat(result.getWorkMinutes()).isZero();
        assertThat(result.getStatus()).isNull();
    }

    @Test
    @DisplayName("★★ 같은 입력(09:30 출근·18:30 퇴근)에 두 정책이 다른 결과를 낸다 - 교체 가능성의 증명")
    void samInputProducesDifferentResultsAcrossPolicies() {
        LocalDateTime checkIn = at("09:30");
        LocalDateTime checkOut = at("18:30");

        WorkTimeResult byDefault = defaultPolicy.evaluate(checkIn, checkOut, DATE);
        WorkTimeResult byFlex = flex.evaluate(checkIn, checkOut, DATE);

        // 고정 출퇴근제: 09:30 은 09:00 을 넘겼으므로 지각
        assertThat(byDefault.getStatus()).isEqualTo(AttendanceStatus.LATE);
        // 자율출퇴근제: 09:30~18:30 은 점심시간을 빼면 정확히 8시간을 채웠으므로 정상
        assertThat(byFlex.getStatus()).isEqualTo(AttendanceStatus.NORMAL);
    }
}
