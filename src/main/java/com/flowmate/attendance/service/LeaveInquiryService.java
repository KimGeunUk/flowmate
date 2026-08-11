package com.flowmate.attendance.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.flowmate.attendance.domain.LeaveBalance;

/**
 * ★ approval 모듈이 연차 신청서 기안 화면에서 보는 지점.
 *
 * 교차 모듈 규칙: approval 은 attendance 의 Service 인터페이스만 보고,
 * 매퍼(HolidayMapper·LeaveBalanceMapper)를 직접 호출하지 않는다. 기안 화면이
 * 필요로 하는 두 가지 — 영업일수 계산과 잔여 조회 — 를 이 인터페이스 하나로
 * 모은다.
 *
 * LeaveApplyService(승인 반영용 쓰기 경로)와는 목적이 다르다 — 이쪽은
 * 기안 시점의 읽기 전용 조회다.
 */
public interface LeaveInquiryService {

    /**
     * 연차 신청서의 일수를 계산한다. 반차는 0.5일 고정(하루짜리만 허용),
     * 그 외는 영업일수(주말·공휴일 제외)다.
     *
     * @throws IllegalArgumentException 종료일이 시작일보다 앞서거나,
     *         반차인데 시작일과 종료일이 다를 때
     */
    BigDecimal calculateDays(String leaveType, LocalDate startDate, LocalDate endDate);

    /**
     * 해당 연도의 연차 잔여. 잠그지 않는 읽기 전용 조회다 — 화면 표시·안내용이며
     * 승인 시점의 권위 있는 검증(잠금 안에서 재확인)을 대신하지 않는다.
     *
     * @return 잔여 정보가 없으면 null
     */
    LeaveBalance findBalance(Long empId, int year);
}
