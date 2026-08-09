package com.flowmate.approval.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.flowmate.approval.domain.LeaveRequest;
import com.flowmate.attendance.domain.LeaveBalance;

/**
 * exceedsBalance 는 DB 를 건드리지 않는 순수 판정이므로, 매퍼에 null 을 넣고도
 * 단위 테스트할 수 있다 — ApprovalQueryServiceIT 이 이미 있는 매퍼 의존
 * 메서드들과는 성격이 다르다.
 */
class ApprovalQueryServiceTest {

    private final ApprovalQueryService queryService = new ApprovalQueryService(null, null, null);

    @Test
    @DisplayName("신청 일수가 잔여보다 많으면 초과다")
    void exceedsWhenRequestedMoreThanRemaining() {
        LeaveRequest request = leaveRequestOf("3.0");
        LeaveBalance balance = balanceOf("17.0", "15.0"); // 잔여 2.0

        assertThat(queryService.exceedsBalance(request, balance)).isTrue();
    }

    @Test
    @DisplayName("신청 일수가 잔여와 같으면 초과가 아니다")
    void notExceedsWhenRequestedEqualsRemaining() {
        LeaveRequest request = leaveRequestOf("2.0");
        LeaveBalance balance = balanceOf("17.0", "15.0"); // 잔여 2.0

        assertThat(queryService.exceedsBalance(request, balance)).isFalse();
    }

    @Test
    @DisplayName("신청 일수가 잔여보다 적으면 초과가 아니다")
    void notExceedsWhenRequestedLessThanRemaining() {
        LeaveRequest request = leaveRequestOf("1.0");
        LeaveBalance balance = balanceOf("17.0", "5.0"); // 잔여 12.0

        assertThat(queryService.exceedsBalance(request, balance)).isFalse();
    }

    @Test
    @DisplayName("★ 신청서나 잔여 정보가 없으면 초과로 판정하지 않는다 — 저장을 막을 근거가 없다")
    void doesNotExceedWhenEitherSideIsMissing() {
        LeaveRequest request = leaveRequestOf("100.0");

        assertThat(queryService.exceedsBalance(null, balanceOf("17.0", "5.0"))).isFalse();
        assertThat(queryService.exceedsBalance(request, null)).isFalse();
    }

    private LeaveRequest leaveRequestOf(String days) {
        LeaveRequest lr = new LeaveRequest();
        lr.setDays(new BigDecimal(days));
        return lr;
    }

    private LeaveBalance balanceOf(String granted, String used) {
        LeaveBalance balance = new LeaveBalance();
        balance.setGrantedDays(new BigDecimal(granted));
        balance.setUsedDays(new BigDecimal(used));
        return balance;
    }
}
