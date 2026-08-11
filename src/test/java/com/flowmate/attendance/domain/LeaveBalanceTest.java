package com.flowmate.attendance.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 잔여일수는 컬럼으로 저장하지 않고 (granted - used) 로 계산한다.
 */
class LeaveBalanceTest {

    @Test
    @DisplayName("잔여일수는 부여일수에서 사용일수를 뺀 값이다")
    void remainingDaysIsGrantedMinusUsed() {
        LeaveBalance balance = new LeaveBalance();
        balance.setGrantedDays(new BigDecimal("17.0"));
        balance.setUsedDays(new BigDecimal("5.0"));

        assertThat(balance.getRemainingDays()).isEqualByComparingTo("12.0");
    }

    @Test
    @DisplayName("사용일수가 0.5 단위(반차)여도 정확히 반영된다")
    void remainingDaysHandlesHalfDayUsage() {
        LeaveBalance balance = new LeaveBalance();
        balance.setGrantedDays(new BigDecimal("15.0"));
        balance.setUsedDays(new BigDecimal("0.5"));

        assertThat(balance.getRemainingDays()).isEqualByComparingTo("14.5");
    }
}
