package com.flowmate.attendance.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.flowmate.org.domain.Employee;

/**
 * 전원 15일. 근속과 무관하다.
 */
class FlatLeaveGrantPolicyTest {

    private final LeaveGrantPolicy policy = new FlatLeaveGrantPolicy();

    @Test
    @DisplayName("입사 1개월 신입도 15일이다")
    void newHireGetsFifteen() {
        Employee employee = employeeHiredOn(LocalDate.of(2026, 7, 1));

        BigDecimal days = policy.grantDays(employee, 2026);

        assertThat(days).isEqualByComparingTo("15");
    }

    @Test
    @DisplayName("근속 10년차도 15일이다 — 근속을 참조하지 않는다")
    void longTenureStillGetsFifteen() {
        Employee employee = employeeHiredOn(LocalDate.of(2015, 3, 1));

        BigDecimal days = policy.grantDays(employee, 2026);

        assertThat(days).isEqualByComparingTo("15");
    }

    private Employee employeeHiredOn(LocalDate hireDate) {
        Employee employee = new Employee();
        employee.setHireDate(hireDate);
        return employee;
    }
}
