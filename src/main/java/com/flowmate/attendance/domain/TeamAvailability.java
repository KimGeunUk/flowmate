package com.flowmate.attendance.domain;

import java.io.Serializable;

/**
 * 팀 가동률 - 특정 날짜에 부서 인원 중
 * 몇 명이 부재(연차·반차)인지와 그로 인한 가동률.
 *
 * teamSize 는 신청자가 속한 바로 그 부서(리프 팀)의 재직 사원 수다 -
 * {@code findDeptMonthlySummary} 처럼 하위 부서까지 확장하지 않는다. "팀"은
 * 조직도 하위 트리가 아니라 신청자와 같은 dept_id 를 가진 동료들을 뜻하기
 * 때문이다("마케팅팀"처럼 실제 인원이 있는 리프 부서를 전제한다).
 */
public class TeamAvailability implements Serializable {

    private static final long serialVersionUID = 1L;

    private int teamSize;
    private int absentCount;

    public int getTeamSize() {
        return teamSize;
    }

    public void setTeamSize(int teamSize) {
        this.teamSize = teamSize;
    }

    public int getAbsentCount() {
        return absentCount;
    }

    public void setAbsentCount(int absentCount) {
        this.absentCount = absentCount;
    }

    /** 팀 가동률(%) = (팀원 - 부재자) / 팀원 * 100, 반올림. 팀원이 0이면 0(나눗셈 방지) */
    public int getAvailabilityPercent() {
        if (teamSize <= 0) {
            return 0;
        }
        return Math.round(100f * (teamSize - absentCount) / teamSize);
    }
}
