package com.flowmate.attendance.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.flowmate.org.domain.Employee;

/**
 * 1년 미만 월 1일, 이후 15일 + 2년마다 1일 (최대 25일).
 *
 * 근속 계산 기준일은 부여 연도 1월 1일로 고정한다 — grantDays(employee, year) 가
 * 특정 시각이 아니라 연도만 받으므로, "그 해가 시작될 때까지 채운 근속"을
 * 기준으로 삼는 것이 자연스럽다. 아래 경계값은 전부 이 기준으로 고정한 것이다.
 */
class TenureBasedLeaveGrantPolicyTest {

    private final LeaveGrantPolicy policy = new TenureBasedLeaveGrantPolicy();

    @Test
    @DisplayName("입사 11개월(1년 미만)은 근속 월수만큼 — 11일")
    void elevenMonthsGetsElevenDays() {
        // 2025-02-01 입사, 기준일 2026-01-01 → 11개월
        Employee employee = employeeHiredOn(LocalDate.of(2025, 2, 1));

        BigDecimal days = policy.grantDays(employee, 2026);

        assertThat(days).isEqualByComparingTo("11");
    }

    @Test
    @DisplayName("입사 0개월(이번 달 입사)은 0일이다")
    void zeroMonthsGetsZeroDays() {
        Employee employee = employeeHiredOn(LocalDate.of(2026, 1, 1));

        BigDecimal days = policy.grantDays(employee, 2026);

        assertThat(days).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("★ 정확히 1년 근속은 15일이다 — 월할 계산에서 정액제로 전환하는 경계")
    void exactlyOneYearGetsFifteen() {
        // 2025-01-01 입사, 기준일 2026-01-01 → 정확히 12개월
        Employee employee = employeeHiredOn(LocalDate.of(2025, 1, 1));

        BigDecimal days = policy.grantDays(employee, 2026);

        assertThat(days).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("2년 근속은 아직 15일이다 — 2년 단위 가산은 1년 시점부터 2년이 더 지나야 붙는다")
    void twoYearsStillFifteen() {
        Employee employee = employeeHiredOn(LocalDate.of(2024, 1, 1));

        BigDecimal days = policy.grantDays(employee, 2026);

        assertThat(days).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("★ 3년 근속은 16일이다 — 1년 시점 이후 2년마다 1일씩 붙는다")
    void threeYearsGetsSixteen() {
        // 2023-01-01 입사, 기준일 2026-01-01 → 정확히 3년
        Employee employee = employeeHiredOn(LocalDate.of(2023, 1, 1));

        BigDecimal days = policy.grantDays(employee, 2026);

        assertThat(days).isEqualByComparingTo("16");
    }

    @Test
    @DisplayName("5년 근속은 17일이다")
    void fiveYearsGetsSeventeen() {
        Employee employee = employeeHiredOn(LocalDate.of(2021, 1, 1));

        BigDecimal days = policy.grantDays(employee, 2026);

        assertThat(days).isEqualByComparingTo("17");
    }

    @Test
    @DisplayName("★ 아주 오래 근속하면 25일에서 상한이 걸리고 더 늘지 않는다")
    void veryLongTenureCapsAtTwentyFive() {
        // 2026 - 1990 = 36년 근속. 공식대로면 15 + floor(35/2) = 32 → 25로 잘려야 한다
        Employee employee = employeeHiredOn(LocalDate.of(1990, 1, 1));

        BigDecimal days = policy.grantDays(employee, 2026);

        assertThat(days).isEqualByComparingTo("25");
    }

    @Test
    @DisplayName("상한을 넘긴 근속끼리는 전부 같은 25일이다 — 상한 이후 더 늘지 않는다는 것의 재확인")
    void beyondCapStaysAtCeiling() {
        Employee tenureThirtySix = employeeHiredOn(LocalDate.of(1990, 1, 1));
        Employee tenureFifty = employeeHiredOn(LocalDate.of(1976, 1, 1));

        assertThat(policy.grantDays(tenureThirtySix, 2026)).isEqualByComparingTo("25");
        assertThat(policy.grantDays(tenureFifty, 2026)).isEqualByComparingTo("25");
    }

    private Employee employeeHiredOn(LocalDate hireDate) {
        Employee employee = new Employee();
        employee.setHireDate(hireDate);
        return employee;
    }
}
