package com.flowmate.attendance.calc;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.flowmate.attendance.domain.LeaveType;
import com.flowmate.attendance.mapper.HolidayMapper;

/**
 * 영업일 계산 — 주말과 holiday 테이블을 뺀다 (계획서 4 D5).
 *
 * 사람이 연차 신청서에 일수를 직접 입력하게 두면 금요일~월요일을 4일로 적는
 * 실수가 반드시 난다. 서버가 계산해서 신뢰할 수 있는 값 하나만 남긴다.
 *
 * HolidayMapper 를 통해서만 공휴일을 조회한다 — ApprovalLinePolicy(지점 1),
 * WorkTimePolicy(지점 2)와 달리 이 클래스는 매퍼를 직접 물고 있지만, 매퍼가
 * 인터페이스이므로 테스트는 FakeHolidayMapper 로 DB 없이 돌아간다.
 *
 * @Component 인 이유: WorkTimePolicy·LeaveGrantPolicy 와 달리 이 클래스는
 * 커스터마이징 지점이 아니다(구현이 하나뿐이다) — 그래서 *PolicyConfig 처럼
 * 손으로 배선하지 않고 Spring 이 그냥 빈으로 등록하게 둔다.
 */
@Component
public class BusinessDayCalculator {

    private static final BigDecimal HALF_DAY = new BigDecimal("0.5");

    private final HolidayMapper holidayMapper;

    public BusinessDayCalculator(HolidayMapper holidayMapper) {
        this.holidayMapper = holidayMapper;
    }

    /**
     * [start, end] 구간(양끝 포함)의 영업일 수. 주말과 공휴일을 뺀다.
     *
     * @throws IllegalArgumentException 종료일이 시작일보다 앞설 때
     */
    public BigDecimal countBusinessDays(LocalDate start, LocalDate end) {
        return BigDecimal.valueOf(businessDaysBetween(start, end).size());
    }

    /**
     * [start, end] 구간(양끝 포함)에서 주말·공휴일을 뺀 날짜 목록 그대로.
     *
     * DefaultLeaveApplyService(Task 6)가 연차 반영 시 근태를 UPSERT 할 날짜를
     * 정할 때 쓴다 — countBusinessDays 는 개수만 주므로 이 반영 루프에는
     * 쓸 수 없다.
     */
    public List<LocalDate> businessDaysBetween(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new IllegalArgumentException(
                    "종료일이 시작일보다 앞설 수 없습니다: start=" + start + " end=" + end);
        }

        Set<LocalDate> holidays = new HashSet<>(holidayMapper.findDatesBetween(start, end));

        List<LocalDate> businessDays = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            if (!isWeekend(day) && !holidays.contains(day)) {
                businessDays.add(day);
            }
        }
        return businessDays;
    }

    /**
     * 연차 신청서의 일수. 반차(HALF_AM/HALF_PM)는 하루짜리만 허용하고 0.5일로
     * 고정한다 — 반차가 며칠에 걸치는 것은 기능이 아니라 데이터 오류다.
     * 그 외 유형은 영업일 계산에 그대로 위임한다.
     */
    public BigDecimal calculateLeaveDays(String leaveType, LocalDate start, LocalDate end) {
        if (LeaveType.isHalfDay(leaveType)) {
            if (!start.equals(end)) {
                throw new IllegalArgumentException(
                        "반차는 시작일과 종료일이 같아야 합니다: start=" + start + " end=" + end);
            }
            return HALF_DAY;
        }
        return countBusinessDays(start, end);
    }

    private boolean isWeekend(LocalDate day) {
        DayOfWeek dow = day.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }
}
