package com.flowmate.attendance.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.attendance.domain.Attendance;
import com.flowmate.attendance.domain.DeptAttendanceRow;
import com.flowmate.attendance.domain.TeamAvailability;

/** 일별 근태. */
@Mapper
public interface AttendanceMapper {

    /**
     * 연차 반영 UPSERT. 그날 행이 이미 있을 수 있다 — 오전에 출근을 찍고 오후에
     * 반차를 낸 경우. ON CONFLICT 로 status/note 만 덮어쓰고
     * check_in/check_out/work_minutes 는 건드리지 않는다.
     */
    void upsertForLeave(Attendance attendance);

    /** 테스트 검증·화면 조회용 */
    Attendance findByEmpIdAndWorkDate(@Param("empId") Long empId, @Param("workDate") LocalDate workDate);

    /**
     * 출근 등록. 중복 여부는 AttendanceService 가 먼저 확인해 막으므로 여기서는
     * 순수 INSERT 다 — upsertForLeave 와 달리 "이미 있으면 조용히 덮어쓰기"가
     * 출근 등록에는 맞지 않는다.
     */
    void insertCheckIn(Attendance attendance);

    /** 퇴근 등록. WorkTimePolicy 가 판정한 workMinutes/overtimeMinutes/status 를 함께 반영한다 */
    void updateCheckOut(Attendance attendance);

    /** "내 근태" — 한 사원의 한 달치 행. work_date 오름차순 */
    List<Attendance> findByEmpIdAndMonth(@Param("empId") Long empId,
                                         @Param("start") LocalDate start,
                                         @Param("end") LocalDate end);

    /**
     * "부서 근태 현황" — 사원별 한 달 집계. deptIds 는 호출자가
     * DepartmentService.findDeptAndDescendantIds 로 계산해 넘긴다.
     * employee LEFT JOIN attendance 라 근태 행이 없는 사원도 0 으로 채워져 나온다.
     */
    List<DeptAttendanceRow> findDeptMonthlySummary(@Param("deptIds") List<Long> deptIds,
                                                    @Param("start") LocalDate start,
                                                    @Param("end") LocalDate end);

    /**
     * 팀 가동률. findDeptMonthlySummary 와 달리 하위 부서로 확장하지 않는다 -
     * "팀"은 신청자와 같은 리프 부서를 뜻한다(TeamAvailability 주석 참조).
     */
    TeamAvailability findTeamAvailability(@Param("deptId") Long deptId, @Param("date") LocalDate date);
}
