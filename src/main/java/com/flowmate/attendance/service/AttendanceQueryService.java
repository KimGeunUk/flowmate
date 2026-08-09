package com.flowmate.attendance.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.attendance.domain.Attendance;
import com.flowmate.attendance.domain.AttendanceMonthlySummary;
import com.flowmate.attendance.domain.DeptAttendanceRow;
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
}
