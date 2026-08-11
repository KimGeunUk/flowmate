package com.flowmate.attendance.calc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.flowmate.attendance.domain.LeaveType;

/**
 * 영업일 계산 — 주말과 holiday 를 뺀다.
 *
 * 사람이 일수를 입력하게 하면 금요일~월요일을 4일로 적는 실수가 반드시 난다는
 * 것이 이 클래스가 존재하는 이유다. 아래 경계값들이 그 실수를 막는다.
 */
class BusinessDayCalculatorTest {

    // 2026-08-03(월) ~ 2026-08-09(일) 한 주를 기준으로 삼는다. 공휴일은
    // FakeHolidayMapper 에 직접 지정하므로 실제 2026년 달력과 일치할 필요가 없다.
    private static final LocalDate MON = LocalDate.of(2026, 8, 3);
    private static final LocalDate TUE = LocalDate.of(2026, 8, 4);
    private static final LocalDate WED = LocalDate.of(2026, 8, 5);
    private static final LocalDate FRI = LocalDate.of(2026, 8, 7);
    private static final LocalDate SAT = LocalDate.of(2026, 8, 8);
    private static final LocalDate SUN = LocalDate.of(2026, 8, 9);
    private static final LocalDate NEXT_MON = LocalDate.of(2026, 8, 10);

    @Test
    @DisplayName("평일로만 이루어진 한 주는 5일이다")
    void plainWeekdaysCountsFive() {
        BusinessDayCalculator calculator = calculatorWithHolidays();

        assertThat(calculator.countBusinessDays(MON, FRI)).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("★ 주말에 낸 반차는 0일이다 — 잔여만 깎이고 근태가 안 생기는 조용한 오류를 막는다")
    void halfDayOnWeekendIsZero() {
        BusinessDayCalculator calculator = calculatorWithHolidays();

        // 0.5 를 돌려주면 기안 검사(days > 0)를 통과해버리고,
        // 승인 시점에는 반영할 영업일이 없어 잔여만 0.5 줄고 근태 행은 안 생긴다.
        // 어느 쪽에서도 예외가 나지 않으므로 조용히 틀린다.
        assertThat(calculator.calculateLeaveDays(LeaveType.HALF_AM, SAT, SAT))
                .isEqualByComparingTo("0");
        assertThat(calculator.calculateLeaveDays(LeaveType.HALF_PM, SUN, SUN))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("★ 공휴일에 낸 반차도 0일이다")
    void halfDayOnHolidayIsZero() {
        BusinessDayCalculator calculator = calculatorWithHolidays(WED);

        assertThat(calculator.calculateLeaveDays(LeaveType.HALF_AM, WED, WED))
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("평일 반차는 그대로 0.5일이다")
    void halfDayOnWeekdayStaysHalf() {
        BusinessDayCalculator calculator = calculatorWithHolidays();

        assertThat(calculator.calculateLeaveDays(LeaveType.HALF_AM, TUE, TUE))
                .isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("★ 금요일~월요일은 주말을 빼면 2일이다 — 사람이 세면 4일로 착각하기 쉬운 경계")
    void fridayToMondayIsTwoBusinessDays() {
        BusinessDayCalculator calculator = calculatorWithHolidays();

        assertThat(calculator.countBusinessDays(FRI, NEXT_MON)).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("주말만으로 이루어진 구간은 0일이다")
    void weekendOnlyRangeIsZero() {
        BusinessDayCalculator calculator = calculatorWithHolidays();

        assertThat(calculator.countBusinessDays(SAT, SUN)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("구간에 평일 공휴일이 끼어 있으면 그 날은 빠진다")
    void rangeSpanningWeekdayHolidayExcludesIt() {
        BusinessDayCalculator calculator = calculatorWithHolidays(TUE);

        // 월~수 3일 중 화요일이 공휴일이므로 월·수 2일만 영업일이다
        assertThat(calculator.countBusinessDays(MON, WED)).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("시작일과 종료일이 같으면 1일이다")
    void sameStartAndEndIsOneDay() {
        BusinessDayCalculator calculator = calculatorWithHolidays();

        assertThat(calculator.countBusinessDays(MON, MON)).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("★ 종료일이 시작일보다 앞서면 예외다")
    void endBeforeStartThrows() {
        BusinessDayCalculator calculator = calculatorWithHolidays();

        assertThatThrownBy(() -> calculator.countBusinessDays(WED, MON))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("★ 구간 전체가 공휴일이면 0일이다 — 이런 신청은 애초에 의미가 없다")
    void rangeMadeEntirelyOfHolidaysIsZero() {
        BusinessDayCalculator calculator = calculatorWithHolidays(MON, TUE);

        assertThat(calculator.countBusinessDays(MON, TUE)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("★ 반차는 시작=종료일 때 0.5일로 고정된다")
    void halfDayIsFixedAtHalfWhenStartEqualsEnd() {
        BusinessDayCalculator calculator = calculatorWithHolidays();

        assertThat(calculator.calculateLeaveDays(LeaveType.HALF_AM, MON, MON)).isEqualByComparingTo("0.5");
        assertThat(calculator.calculateLeaveDays(LeaveType.HALF_PM, MON, MON)).isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("★ 반차가 하루를 넘어가면 입력 오류로 본다 — 기능이 아니라 데이터 오류다")
    void halfDaySpanningMultipleDaysThrows() {
        BusinessDayCalculator calculator = calculatorWithHolidays();

        assertThatThrownBy(() -> calculator.calculateLeaveDays(LeaveType.HALF_AM, MON, TUE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("연차(ANNUAL)는 반차 특례 없이 영업일 계산에 그대로 위임한다")
    void annualLeaveDelegatesToBusinessDayCount() {
        BusinessDayCalculator calculator = calculatorWithHolidays();

        assertThat(calculator.calculateLeaveDays(LeaveType.ANNUAL, FRI, NEXT_MON)).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("★ 금~월 구간의 영업일 목록은 금요일과 다음 월요일뿐이다 — 토·일은 포함되지 않는다")
    void businessDaysBetweenExcludesWeekendFromFridayToMonday() {
        BusinessDayCalculator calculator = calculatorWithHolidays();

        List<LocalDate> days = calculator.businessDaysBetween(FRI, NEXT_MON);

        assertThat(days).containsExactly(FRI, NEXT_MON);
    }

    @Test
    @DisplayName("영업일 목록에서도 평일 공휴일은 빠진다")
    void businessDaysBetweenExcludesWeekdayHoliday() {
        BusinessDayCalculator calculator = calculatorWithHolidays(TUE);

        List<LocalDate> days = calculator.businessDaysBetween(MON, WED);

        assertThat(days).containsExactly(MON, WED);
    }

    @Test
    @DisplayName("영업일 목록도 종료일이 시작일보다 앞서면 예외다")
    void businessDaysBetweenEndBeforeStartThrows() {
        BusinessDayCalculator calculator = calculatorWithHolidays();

        assertThatThrownBy(() -> calculator.businessDaysBetween(WED, MON))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private BusinessDayCalculator calculatorWithHolidays(LocalDate... holidays) {
        return new BusinessDayCalculator(new FakeHolidayMapper(holidays));
    }
}
