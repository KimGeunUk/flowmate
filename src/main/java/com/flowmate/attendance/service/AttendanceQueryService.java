package com.flowmate.attendance.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.attendance.domain.Attendance;
import com.flowmate.attendance.domain.AttendanceMonthlySummary;
import com.flowmate.attendance.domain.DeptAttendanceRow;
import com.flowmate.attendance.domain.TeamAvailability;
import com.flowmate.attendance.mapper.AttendanceMapper;
import com.flowmate.org.service.DepartmentService;

/**
 * 근태 조회 — 개인/부서 (계획서 4 Task 7). 쓰기(AttendanceService)와 분리한
 * 이유는 ApprovalQueryService/ApprovalService 와 같다: 트랜잭션 속성이
 * readOnly 로 다르고, 조회 전용 화면 데이터 조립이 이 클래스에만 필요하다.
 */
@Service
public class AttendanceQueryService {

    private final AttendanceMapper attendanceMapper;
    private final DepartmentService departmentService;

    public AttendanceQueryService(AttendanceMapper attendanceMapper, DepartmentService departmentService) {
        this.attendanceMapper = attendanceMapper;
        this.departmentService = departmentService;
    }

    /** 내 근태 — 한 사원의 한 달치 목록 + 합계 */
    @Transactional(readOnly = true)
    public AttendanceMonthlySummary findMyMonthly(Long empId, YearMonth yearMonth) {
        List<Attendance> rows = attendanceMapper.findByEmpIdAndMonth(
                empId, yearMonth.atDay(1), yearMonth.atEndOfMonth());
        return AttendanceMonthlySummary.of(rows);
    }

    /**
     * 부서 근태 현황 — viewerDeptId 와 그 하위 부서 전체에 속한 사원의 한 달 집계.
     *
     * ★ 권한(계획서 4 Task 7): viewerDeptId 는 컨트롤러가 로그인 주체의 deptId 를
     * 그대로 넘긴 값이어야 한다 — 이 메서드는 요청 파라미터로 받은 임의의 deptId 를
     * 받지 않는다. 그래야 다른 부서를 조회하도록 URL 을 조작할 여지 자체가 없다.
     */
    @Transactional(readOnly = true)
    public List<DeptAttendanceRow> findDeptMonthly(Long viewerDeptId, YearMonth yearMonth) {
        List<Long> deptIds = departmentService.findDeptAndDescendantIds(viewerDeptId);
        if (deptIds.isEmpty()) {
            return List.of();
        }
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();
        return attendanceMapper.findDeptMonthlySummary(deptIds, start, end);
    }

    /**
     * 특정 날짜의 팀 가동률(계획서 5 Task 4 - 연차 맥락 패널). {@code ai}/{@code approval}
     * 모듈은 이 메서드를 통해서만 팀 부재 현황을 본다 - attendance 의 매퍼를 직접
     * 부르지 않는다(Phase 4 D1의 모듈 경계, 계획서 5 Task 4가 그대로 잇는다).
     */
    @Transactional(readOnly = true)
    public TeamAvailability findTeamAvailability(Long deptId, LocalDate date) {
        return attendanceMapper.findTeamAvailability(deptId, date);
    }

    /**
     * 임의의 기간(달력월 경계가 아니어도 된다)에 대한 근태 합계 - "최근 3개월"
     * 같은 계획서 5 Task 4 의 조회를 위해 findMyMonthly 와 별개로 둔다.
     * findByEmpIdAndMonth 는 이름과 달리 SQL 자체가 이미 임의의 start/end 를
     * 받으므로(WHERE work_date BETWEEN #{start} AND #{end}) 새 쿼리를 추가하지
     * 않고 그대로 재사용한다.
     */
    @Transactional(readOnly = true)
    public AttendanceMonthlySummary findRecentSummary(Long empId, LocalDate start, LocalDate end) {
        List<Attendance> rows = attendanceMapper.findByEmpIdAndMonth(empId, start, end);
        return AttendanceMonthlySummary.of(rows);
    }
}
