package com.flowmate.attendance.policy;

import java.math.BigDecimal;

import com.flowmate.org.domain.Employee;

/**
 * ★ 커스터마이징 지점 3 — 회사마다 연차 부여 규칙(근속에 따른 차등 여부)이 다르다.
 *
 * WorkTimePolicy(지점 2)와 같은 이유로 순수 로직으로 둔다: employee.hireDate 와
 * year 만으로 판정하므로 매퍼 없이 단위 테스트할 수 있다.
 */
public interface LeaveGrantPolicy {

    /**
     * 해당 연도에 부여할 연차 일수를 계산한다.
     *
     * @param employee 대상 사원. hireDate 를 근속 계산에 쓴다
     * @param year     부여 연도
     * @return 부여 일수 (0.5 단위가 있을 수 있어 BigDecimal)
     */
    BigDecimal grantDays(Employee employee, int year);
}
