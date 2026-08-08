package com.flowmate.attendance.policy;

import java.math.BigDecimal;

import com.flowmate.org.domain.Employee;

/**
 * 전원 15일. 근속을 참조하지 않는 가장 단순한 구현이다.
 */
public class FlatLeaveGrantPolicy implements LeaveGrantPolicy {

    private static final BigDecimal FLAT_DAYS = BigDecimal.valueOf(15);

    @Override
    public BigDecimal grantDays(Employee employee, int year) {
        return FLAT_DAYS;
    }
}
