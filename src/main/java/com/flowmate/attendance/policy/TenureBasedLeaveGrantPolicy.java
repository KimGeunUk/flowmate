package com.flowmate.attendance.policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.flowmate.org.domain.Employee;

/**
 * 1년 미만은 근속 월수만큼(월 1일), 1년부터는 15일 + 2년마다 1일, 최대 25일.
 *
 * ★ 기준일: 부여 연도의 1월 1일로 고정한다. grantDays(employee, year) 는 특정
 * 시각이 아니라 연도만 받으므로, "그 해가 시작되는 시점까지 채운 근속"을
 * 기준으로 삼는 것이 자연스럽다 (계획서가 정하지 않은 것을 이 구현이 확정한다 —
 * 테스트 클래스 주석 참조).
 */
public class TenureBasedLeaveGrantPolicy implements LeaveGrantPolicy {

    private static final int BASE_YEARS_THRESHOLD = 1;
    private static final BigDecimal BASE_DAYS = BigDecimal.valueOf(15);
    private static final BigDecimal CAP_DAYS = BigDecimal.valueOf(25);
    private static final int YEARS_PER_BONUS_DAY = 2;

    @Override
    public BigDecimal grantDays(Employee employee, int year) {
        LocalDate asOf = LocalDate.of(year, 1, 1);
        LocalDate hireDate = employee.getHireDate();

        long completedMonths = Math.max(0, ChronoUnit.MONTHS.between(hireDate, asOf));

        if (completedMonths < 12) {
            return BigDecimal.valueOf(completedMonths);
        }

        long completedYears = completedMonths / 12;
        long bonusDays = (completedYears - BASE_YEARS_THRESHOLD) / YEARS_PER_BONUS_DAY;
        BigDecimal days = BASE_DAYS.add(BigDecimal.valueOf(bonusDays));

        return days.min(CAP_DAYS);
    }
}
