package com.flowmate.ai.domain;

import com.flowmate.approval.domain.LeaveRequest;
import com.flowmate.attendance.domain.AttendanceMonthlySummary;
import com.flowmate.attendance.domain.LeaveBalance;
import com.flowmate.attendance.domain.TeamAvailability;
import java.io.Serializable;

/**
 * 연차 맥락 패널이 화면에 보여줄 값을 한데 모은 조회 전용 뷰 객체
 * (설계서 §6.4.7 3a, 계획서 5 Task 4). LLM 을 쓰지 않는다 - ai 패키지 소속인
 * 이유는 이 기능이 "AI 기능 3종" 중 하나(결재-근태 통합을 보여주는 기능)로
 * 설계서에 묶여 있기 때문이지, LLM 호출이 있어서가 아니다. 3b(LLM 판단 코멘트)는
 * 설계서 §9.1이 지정한 첫 번째 축소 대상이라 이 Phase에서 만들지 않는다.
 *
 * 신청자·부서·직급은 담지 않는다 - 컨트롤러가 이미 조회해 둔 {@code doc}
 * (drafterName/deptName/drafterPositionName) 을 JSP 가 그대로 재사용하므로
 * 중복해서 나를 필요가 없다.
 *
 * ai 패키지가 approval/attendance 의 도메인 객체를 참조하는 것은 설계서 §4.3
 * 규칙("ai 패키지는 도메인을 참조할 수 있으나 역방향은 인터페이스로만")이
 * 허용하는 방향이다.
 */
public class LeaveContext implements Serializable {

    private static final long serialVersionUID = 1L;

    private LeaveRequest leaveRequest;
    private LeaveBalance leaveBalance;
    private TeamAvailability teamAvailability;
    private AttendanceMonthlySummary recentSummary;

    public LeaveRequest getLeaveRequest() {
        return leaveRequest;
    }

    public void setLeaveRequest(LeaveRequest leaveRequest) {
        this.leaveRequest = leaveRequest;
    }

    public LeaveBalance getLeaveBalance() {
        return leaveBalance;
    }

    public void setLeaveBalance(LeaveBalance leaveBalance) {
        this.leaveBalance = leaveBalance;
    }

    public TeamAvailability getTeamAvailability() {
        return teamAvailability;
    }

    public void setTeamAvailability(TeamAvailability teamAvailability) {
        this.teamAvailability = teamAvailability;
    }

    public AttendanceMonthlySummary getRecentSummary() {
        return recentSummary;
    }

    public void setRecentSummary(AttendanceMonthlySummary recentSummary) {
        this.recentSummary = recentSummary;
    }
}
