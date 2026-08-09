package com.flowmate.attendance.calc;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.flowmate.attendance.mapper.HolidayMapper;

/**
 * 실제 DB 없이 BusinessDayCalculator 를 단위 테스트하기 위한 테스트 전용 대역.
 * com.flowmate.ai.client.FakeLlmClient 와 같은 패턴이다.
 */
class FakeHolidayMapper implements HolidayMapper {

    private final Set<LocalDate> holidays;

    FakeHolidayMapper(LocalDate... holidays) {
        this.holidays = new HashSet<>(List.of(holidays));
    }

    @Override
    public List<LocalDate> findDatesBetween(LocalDate start, LocalDate end) {
        List<LocalDate> result = new ArrayList<>();
        for (LocalDate d : holidays) {
            if (!d.isBefore(start) && !d.isAfter(end)) {
                result.add(d);
            }
        }
        return result;
    }
}
