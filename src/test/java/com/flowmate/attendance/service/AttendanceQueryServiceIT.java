package com.flowmate.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.attendance.domain.AttendanceMonthlySummary;
import com.flowmate.attendance.domain.AttendanceStatus;
import com.flowmate.attendance.domain.DeptAttendanceRow;

/**
 * 근태 조회 통합 테스트 (계획서 4 Task 7).
 *
 * 조직도 시드(README): 대표이사실(1) - 경영지원본부(2) - 인사팀(4)/재무팀(5),
 * 사업본부(3) - 마케팅팀(6)/개발팀(7). 인사팀 사원은 최민석(4)·한지우(5)·서다인(6),
 * 재무팀은 오세훈(7)·임채원(8)·강태윤(9)이다.
 *
 * @Transactional 로 감싼다 — 이 테스트들은 롤백 자체를 검증하지 않으므로
 * (ApprovalServiceLeaveApplyIT 와 같은 이유) 함정이 없고, 만든 행은 자동 롤백된다.
 * 2026년 6·7월을 쓰되, 그 달을 @BeforeEach 가 직접 비우고 시작한다 — 시드가 어느
 * 범위를 채우든 이 테스트의 개수 단정이 흔들리지 않게 하기 위해서다.
 */
@SpringBootTest
@Transactional
class AttendanceQueryServiceIT {

    private static final Long KWAK = 18L;          // 개발팀(7)
    private static final Long CHOI_HR = 4L;        // 인사팀(4)
    private static final Long HAN_HR = 5L;         // 인사팀(4)
    private static final Long SEO_HR = 6L;         // 인사팀(4)
    private static final YearMonth JUNE = YearMonth.of(2026, 6);

    @Autowired
    private AttendanceQueryService attendanceQueryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * ★ 이 테스트가 쓰는 달(2026년 6·7월)을 비운 상태에서 시작한다.
     *
     *   이 클래스는 "6월에 3행", "출근 1일", "한지우는 0일" 처럼 **정확한 개수**를
     *   단정한다. 그래서 그 달에 다른 행이 하나라도 있으면 곧바로 깨진다.
     *   원래는 데모 시드가 6·7월을 비워 두는 것에 기대고 있었고, 시드 파일에도
     *   "테스트가 쓰는 달이라 피한다"는 주석이 있었다.
     *
     *   그 방향이 거꾸로였다 — 시드가 테스트를 피해 다니면 시드 범위를 넓힐 때마다
     *   테스트가 깨지고, 어느 달이 예약됐는지 아무도 기억하지 못한다.
     *   테스트가 자기 달을 직접 확보하는 쪽이 맞다. 클래스가 @Transactional 이라
     *   이 삭제도 함께 롤백되어 진짜 데이터는 그대로다.
     */
    @BeforeEach
    void clearMonthsUnderTest() {
        jdbcTemplate.update(
                "DELETE FROM attendance WHERE work_date >= DATE '2026-06-01' AND work_date < DATE '2026-08-01'");
    }

    @Test
    @DisplayName("내 근태: 그 달의 행만 모아 합계를 계산하고, 다른 달은 섞이지 않는다")
    void findMyMonthlyAggregatesOnlyThatMonth() {
        insertAttendance(KWAK, LocalDate.of(2026, 6, 1), "09:10", "18:30", 470, 0, AttendanceStatus.LATE);
        insertAttendance(KWAK, LocalDate.of(2026, 6, 2), "09:00", "20:00", 600, 120, AttendanceStatus.NORMAL);
        insertLeaveOnly(KWAK, LocalDate.of(2026, 6, 3), AttendanceStatus.LEAVE);
        insertAttendance(KWAK, LocalDate.of(2026, 7, 1), "09:00", "18:00", 480, 0, AttendanceStatus.NORMAL);

        AttendanceMonthlySummary summary = attendanceQueryService.findMyMonthly(KWAK, JUNE);

        assertThat(summary.getRows()).hasSize(3);
        assertThat(summary.getWorkingDays()).isEqualTo(2);   // 연차 행은 출근 기록이 없다
        assertThat(summary.getLateCount()).isEqualTo(1);
        assertThat(summary.getOvertimeMinutes()).isEqualTo(120);
        assertThat(summary.getLeaveUsedDays()).isEqualByComparingTo("1.0");
    }

    @Test
    @DisplayName("부서 근태 현황: 리프 부서를 주면 그 부서 사원만, 근태 없는 사원도 0으로 나온다")
    void findDeptMonthlyIncludesZeroFilledEmployees() {
        insertAttendance(CHOI_HR, LocalDate.of(2026, 6, 1), "09:10", "18:00", 470, 0, AttendanceStatus.LATE);

        List<DeptAttendanceRow> rows = attendanceQueryService.findDeptMonthly(4L, JUNE);   // 인사팀 자체

        assertThat(rows).extracting(DeptAttendanceRow::getEmpId)
                .containsExactlyInAnyOrder(CHOI_HR, HAN_HR, SEO_HR);

        DeptAttendanceRow choi = findRow(rows, CHOI_HR);
        assertThat(choi.getWorkingDays()).isEqualTo(1);
        assertThat(choi.getLateCount()).isEqualTo(1);

        DeptAttendanceRow han = findRow(rows, HAN_HR);
        assertThat(han.getWorkingDays()).isZero();
        assertThat(han.getLateCount()).isZero();
        assertThat(han.getLeaveUsedDays()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("부서 근태 현황: 상위 부서를 주면 하위 팀 사원까지 모이고, 다른 계열은 섞이지 않는다")
    void findDeptMonthlyAggregatesAcrossDescendantTeams() {
        List<DeptAttendanceRow> rows = attendanceQueryService.findDeptMonthly(2L, JUNE);   // 경영지원본부

        // 경영지원본부(2) 본인 + 인사팀(4,5,6) + 재무팀(7,8,9) = 7명
        assertThat(rows).extracting(DeptAttendanceRow::getEmpId)
                .containsExactlyInAnyOrder(2L, 4L, 5L, 6L, 7L, 8L, 9L);
        // 사업본부 계열(박현주 3, 곽수빈 18)은 섞이지 않는다
        assertThat(rows).extracting(DeptAttendanceRow::getEmpId).doesNotContain(3L, KWAK);
    }

    private DeptAttendanceRow findRow(List<DeptAttendanceRow> rows, Long empId) {
        return rows.stream().filter(r -> r.getEmpId().equals(empId)).findFirst()
                .orElseThrow(() -> new AssertionError("empId 를 찾을 수 없습니다: " + empId));
    }

    private void insertAttendance(Long empId, LocalDate date, String checkInTime, String checkOutTime,
                                  int workMinutes, int overtimeMinutes, String status) {
        jdbcTemplate.update(
                "INSERT INTO attendance (emp_id, work_date, check_in, check_out, work_minutes, overtime_minutes, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                empId, date,
                Timestamp.valueOf(date.atTime(LocalTime.parse(checkInTime))),
                Timestamp.valueOf(date.atTime(LocalTime.parse(checkOutTime))),
                workMinutes, overtimeMinutes, status);
    }

    /** 연차 반영(DefaultLeaveApplyService.apply)과 같은 모양 — check_in/check_out 이 없다 */
    private void insertLeaveOnly(Long empId, LocalDate date, String status) {
        jdbcTemplate.update(
                "INSERT INTO attendance (emp_id, work_date, work_minutes, overtime_minutes, status) "
                        + "VALUES (?, ?, 0, 0, ?)",
                empId, date, status);
    }
}
