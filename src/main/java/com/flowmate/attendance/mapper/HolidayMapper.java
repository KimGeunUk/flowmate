package com.flowmate.attendance.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 공휴일 조회. BusinessDayCalculator 가 주말과 함께 영업일 계산에서 제외한다.
 */
@Mapper
public interface HolidayMapper {

    /** [start, end] 구간(양끝 포함)에 속하는 공휴일 날짜 목록 */
    List<LocalDate> findDatesBetween(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
