package com.flowmate.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.attendance.domain.LeaveBalance;
import com.flowmate.attendance.domain.LeaveType;

/**
 * approval 이 실제로 의존할 지점. 2026년 7월은 공휴일이 없다는 것을
 * HolidayMapperIT 이 확인했으므로, 그 달의 금~월을 경계 검증에 그대로 쓴다.
 *
 * 사원 18(곽수빈) 2026년 잔여(부여 17.0/사용 5.0)는 Task 1 시드다.
 */
@SpringBootTest
@Transactional
class LeaveInquiryServiceIT {

    @Autowired
    private LeaveInquiryService leaveInquiryService;

    @Test
    @DisplayName("연차 일수 계산은 BusinessDayCalculator 에 위임한다 — 금~월은 2일")
    void calculateDaysDelegatesToBusinessDayCalculator() {
        assertThat(leaveInquiryService.calculateDays(
                LeaveType.ANNUAL, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 6)))
                .isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("반차는 0.5일로 고정된다")
    void calculateDaysFixesHalfDay() {
        assertThat(leaveInquiryService.calculateDays(
                LeaveType.HALF_AM, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 3)))
                .isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("종료일이 시작일보다 앞서면 예외가 그대로 전달된다")
    void calculateDaysPropagatesInvalidRange() {
        assertThatThrownBy(() -> leaveInquiryService.calculateDays(
                LeaveType.ANNUAL, LocalDate.of(2026, 7, 6), LocalDate.of(2026, 7, 3)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("잔여 조회는 시드된 값을 그대로 돌려준다")
    void findBalanceReturnsSeededRow() {
        LeaveBalance balance = leaveInquiryService.findBalance(18L, 2026);

        assertThat(balance).isNotNull();
        assertThat(balance.getRemainingDays()).isEqualByComparingTo("12.0");
    }

    @Test
    @DisplayName("잔여 정보가 없으면 null 이다")
    void findBalanceReturnsNullWhenAbsent() {
        assertThat(leaveInquiryService.findBalance(18L, 1999)).isNull();
    }
}
