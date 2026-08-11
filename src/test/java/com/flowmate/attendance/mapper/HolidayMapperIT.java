package com.flowmate.attendance.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시드(2026년 한국 공휴일 15건)를 전제로 한다.
 */
@SpringBootTest
@Transactional
class HolidayMapperIT {

    @Autowired
    private HolidayMapper holidayMapper;

    @Test
    @DisplayName("구간 안의 공휴일만 돌려준다 — 신정 하루짜리 구간")
    void findDatesBetweenReturnsHolidayInsideRange() {
        List<LocalDate> holidays = holidayMapper.findDatesBetween(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 1));

        assertThat(holidays).containsExactly(LocalDate.of(2026, 1, 1));
    }

    @Test
    @DisplayName("설 연휴 3일 구간은 3건이 온다")
    void findDatesBetweenReturnsAllHolidaysInMultiDayRange() {
        List<LocalDate> holidays = holidayMapper.findDatesBetween(
                LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 18));

        assertThat(holidays).containsExactlyInAnyOrder(
                LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 17), LocalDate.of(2026, 2, 18));
    }

    @Test
    @DisplayName("공휴일이 없는 구간은 빈 목록이다")
    void findDatesBetweenReturnsEmptyWhenNoneInRange() {
        List<LocalDate> holidays = holidayMapper.findDatesBetween(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));

        assertThat(holidays).isEmpty();
    }
}
