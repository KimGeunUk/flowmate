package com.flowmate.attendance.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.attendance.calc.BusinessDayCalculator;
import com.flowmate.attendance.domain.LeaveBalance;
import com.flowmate.attendance.mapper.LeaveBalanceMapper;

/**
 * approval 이 기안 화면에서 보는 지점의 구현. 계산은 BusinessDayCalculator 에,
 * 조회는 LeaveBalanceMapper 에 위임한다 — 이 클래스 자체는 로직을 갖지 않고
 * 교차 모듈 경계만 긋는다 (계획서 4 "attendance 는 Service 인터페이스로만 보인다").
 */
@Service
public class DefaultLeaveInquiryService implements LeaveInquiryService {

    private final BusinessDayCalculator businessDayCalculator;
    private final LeaveBalanceMapper leaveBalanceMapper;

    public DefaultLeaveInquiryService(BusinessDayCalculator businessDayCalculator,
                                      LeaveBalanceMapper leaveBalanceMapper) {
        this.businessDayCalculator = businessDayCalculator;
        this.leaveBalanceMapper = leaveBalanceMapper;
    }

    @Override
    public BigDecimal calculateDays(String leaveType, LocalDate startDate, LocalDate endDate) {
        return businessDayCalculator.calculateLeaveDays(leaveType, startDate, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public LeaveBalance findBalance(Long empId, int year) {
        return leaveBalanceMapper.findByEmpIdAndYear(empId, year);
    }
}
