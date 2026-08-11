package com.flowmate.attendance.mapper;

import java.math.BigDecimal;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.attendance.domain.LeaveBalance;

/**
 * 연차 잔여 조회·갱신.
 *
 * findForUpdate 는 행 잠금 조회다 — 같은 사원의 연차 신청서 2건이
 * 동시에 승인되면, 잠그지 않을 경우 둘 다 같은 used_days 를 읽고 둘 다 같은 값을
 * 써서 하루가 사라진다(갱신 분실). findByEmpIdAndYear 는 잠그지 않는 일반 조회용
 * (근태 화면 등 읽기 전용)이다.
 */
@Mapper
public interface LeaveBalanceMapper {

    /** 읽기 전용 조회. 화면 표시 등 잠금이 필요 없는 곳에서 쓴다 */
    LeaveBalance findByEmpIdAndYear(@Param("empId") Long empId, @Param("year") int year);

    /**
     * 잠금 조회. SELECT ... FOR UPDATE.
     * 잔여를 차감하는 트랜잭션(연차 승인 반영)에서만 쓴다.
     */
    LeaveBalance findForUpdate(@Param("empId") Long empId, @Param("year") int year);

    /** 사용일수 갱신. 갱신 시각도 함께 찍는다 */
    int updateUsedDays(@Param("empId") Long empId, @Param("year") int year,
                       @Param("usedDays") BigDecimal usedDays);
}
