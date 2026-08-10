package com.flowmate.attendance.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.flowmate.attendance.domain.Attendance;
import com.flowmate.attendance.domain.DeptAttendanceRow;
import com.flowmate.attendance.domain.TeamAvailability;

/**
 * 일별 근태. Task 6(연차 반영)은 upsertForLeave 만 썼다. 출퇴근 등록(Task 3)의
 * insertCheckIn/updateCheckOut 을 여기에 추가한다.
 */
@Mapper
public interface AttendanceMapper {

    /**
     * 연차 반영 UPSERT (계획서 4 D6). 그날 행이 이미 있을 수 있다 — 오전에
     * 출근을 찍고 오후에 반차를 낸 경우. ON CONFLICT 로 status/note 만
     * 덮어쓰고 check_in/check_out/work_minutes 는 건드리지 않는다.
     *
     * 여기서도 제약 위반을 잡지 않는다(D2 와 같은 이유) — ON CONFLICT 는
     * DB 안에서 해결되어 예외를 만들지 않으므로 트랜잭션이 중단되지 않는다.
     */
    void upsertForLeave(Attendance attendance);

    /** 테스트 검증·화면 조회용 */
    Attendance findByEmpIdAndWorkDate(@Param("empId") Long empId, @Param("workDate") LocalDate workDate);

    /**
     * 출근 등록 — 그날 행이 아직 없을 때만 부른다. 중복 여부는
     * AttendanceService 가 findByEmpIdAndWorkDate 로 먼저 확인해 막으므로
     * 여기서는 순수 INSERT 다(UPSERT 가 아니다) — upsertForLeave 와 달리
     * "이미 있으면 조용히 덮어쓰기"가 출근 등록에는 맞지 않는다(계획서 4 Task 3).
     */
    void insertCheckIn(Attendance attendance);

    /**
     * 퇴근 등록. WorkTimePolicy 가 판정한 workMinutes/overtimeMinutes/status 를
     * 함께 반영한다. attId 로 갱신한다 — 그날 행은 findByEmpIdAndWorkDate 로
     * 이미 조회된 상태(attId 확보됨)에서만 이 메서드가 불린다.
     */
    void updateCheckOut(Attendance attendance);

    /** "내 근태"(Task 7) — 한 사원의 한 달치 행. work_date 오름차순 */
    List<Attendance> findByEmpIdAndMonth(@Param("empId") Long empId,
                                         @Param("start") LocalDate start,
                                         @Param("end") LocalDate end);

    /**
     * "부서 근태 현황"(Task 7) — 주어진 deptId 목록(본인 부서 + 하위 전체, 호출자가
     * DepartmentService.findDeptAndDescendantIds 로 계산해 넘긴다)에 속한 사원별
     * 한 달 집계. employee LEFT JOIN attendance 라 그 달에 근태 행이 없는 사원도
     * 0으로 채워진 행으로 나온다.
     */
    List<DeptAttendanceRow> findDeptMonthlySummary(@Param("deptIds") List<Long> deptIds,
                                                    @Param("start") LocalDate start,
                                                    @Param("end") LocalDate end);

    /**
     * 팀 가동률(계획서 5 Task 4) - deptId 에 속한 재직 사원 수와, 그 중 date 에
     * 연차·반차로 부재인 인원 수를 한 행으로 집계한다. findDeptMonthlySummary 와
     * 달리 deptId 하나만 받는다(하위 부서로 확장하지 않는다) - "팀"은 신청자와
     * 같은 리프 부서를 뜻하기 때문이다(TeamAvailability 클래스 주석 참조).
     */
    TeamAvailability findTeamAvailability(@Param("deptId") Long deptId, @Param("date") LocalDate date);
}
