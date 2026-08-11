package com.flowmate.ai.feature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowmate.ai.domain.LeaveContext;
import com.flowmate.approval.domain.ApprovalForm;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.service.ApprovalService;
import com.flowmate.attendance.domain.AttendanceStatus;
import com.flowmate.attendance.domain.LeaveType;
import com.flowmate.common.exception.ApprovalAccessDeniedException;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기능 3a(연차 맥락 표시, LLM 없음) 배선 검증.
 *
 * 사원 18(곽수빈, 개발팀=dept 7)로 LEAVE 를 임시저장한다 - 상신·승인까지 갈 필요는
 * 없다(패널은 문서를 볼 수 있는 사람이면 상태와 무관하게 보인다). 곽수빈의 2026년
 * 잔여는 부여 17.0/사용 5.0(잔여 12.0) - 시드 값이고, LeaveInquiryServiceIT 로
 * 이미 확인된 값이다. 개발팀(dept 7)은 7명(14~20) - 11-seed-org.sql.
 *
 * 신청일을 2026-09-01(화, 평일)로 고정한다 - "최근 3개월" 창은
 * [2026-06-01, 2026-08-31] 이 된다. 그 구간을 아래 @BeforeEach 가 비우고
 * 시작하므로 이 테스트가 직접 넣는 행만 집계에 잡힌다 - 값을 손으로 계산할
 * 필요 없이 넣은 그대로 기대할 수 있다.
 *
 * (예전에는 "데모 시드가 2~4월만 채우니 6~8월은 원래 비어 있다"에 기대고
 *  있었는데, 시드를 1월~오늘로 넓히자 그 전제가 깨졌다.)
 */
@SpringBootTest
@Transactional
class LeaveContextServiceIT {

    private static final Long KWAK = 18L;    // 개발팀(7) - 잔여 12.0(부여 17.0/사용 5.0)
    private static final Long OUTSIDER = 5L; // 기안자도 결재선도 아니다
    private static final Long TEAMMATE = 19L; // 개발팀(7) 동료 - 팀 부재 시나리오용

    private static final LocalDate LEAVE_DATE = LocalDate.of(2026, 9, 1);

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private LeaveContextService leaveContextService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * ★ "최근 3개월" 창(신청일 2026-09-01 기준 6~8월)을 비운 상태에서 시작한다.
     *
     *   이 테스트는 그 창의 지각 1회·결근 1회·연장 1.0시간을 **정확한 수**로 단정하고,
     *   그 값을 만들려고 8/3·8/4·8/5 에 자기 행을 직접 넣는다. 데모 시드가 그 구간을
     *   채우면 INSERT 가 UNIQUE(emp_id, work_date) 로 깨지고, 깨지지 않더라도 집계가
     *   틀어진다.
     *
     *   AttendanceQueryServiceIT 와 같은 판단이다 - 시드가 테스트를 피해 다니는 대신
     *   테스트가 자기 구간을 확보한다. 클래스가 @Transactional 이라 함께 롤백된다.
     */
    @BeforeEach
    void clearRecentWindow() {
        jdbcTemplate.update(
                "DELETE FROM attendance WHERE work_date >= DATE '2026-06-01' AND work_date < DATE '2026-09-02'");
    }

    @Test
    @DisplayName("★ 완료 기준: LEAVE 문서를 열면 신청 정보·연차 현황·팀 가동률·최근 3개월이 한 번에 채워진다")
    void buildsFullContextForLeaveDocument() {
        insertAttendance(KWAK, LocalDate.of(2026, 8, 3), "09:10", AttendanceStatus.LATE, 0);
        insertAttendance(KWAK, LocalDate.of(2026, 8, 4), "09:00", AttendanceStatus.NORMAL, 60);
        insertAbsence(KWAK, LocalDate.of(2026, 8, 5));
        // 팀 동료 한 명이 같은 날 이미 연차 - 팀 부재 시나리오.
        insertLeave(TEAMMATE, LEAVE_DATE);

        Long approvalId = draftLeave(KWAK, LEAVE_DATE, LEAVE_DATE);

        LeaveContext context = leaveContextService.build(approvalId, KWAK);

        assertThat(context).isNotNull();
        assertThat(context.getLeaveRequest().getStartDate()).isEqualTo(LEAVE_DATE);
        assertThat(context.getLeaveRequest().getDays()).isEqualByComparingTo("1");

        assertThat(context.getLeaveBalance()).isNotNull();
        assertThat(context.getLeaveBalance().getGrantedDays()).isEqualByComparingTo("17.0");
        assertThat(context.getLeaveBalance().getUsedDays()).isEqualByComparingTo("5.0");
        assertThat(context.getLeaveBalance().getRemainingDays()).isEqualByComparingTo("12.0");
        assertThat(context.getLeaveBalance().getUsedPercent()).isEqualTo(29); // round(5/17*100)

        // 개발팀 7명 중 1명(동료)이 그날 이미 연차 -> 가동률 round(6/7*100)=86%
        assertThat(context.getTeamAvailability().getTeamSize()).isEqualTo(7);
        assertThat(context.getTeamAvailability().getAbsentCount()).isEqualTo(1);
        assertThat(context.getTeamAvailability().getAvailabilityPercent()).isEqualTo(86);

        assertThat(context.getRecentSummary().getLateCount()).isEqualTo(1);
        assertThat(context.getRecentSummary().getAbsentCount()).isEqualTo(1);
        assertThat(context.getRecentSummary().getOvertimeHours()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("LEAVE 가 아닌 문서는 null - 패널 자체가 나타나지 않는다")
    void returnsNullForNonLeaveDocument() {
        ApprovalForm form = new ApprovalForm();
        form.setDocType(DocType.EXPENSE);
        form.setTitle("지출결의 - 연차 아님");
        form.setContent("본문");
        form.setAmount(BigDecimal.TEN);
        Long approvalId = approvalService.saveDraft(form, KWAK);

        assertThat(leaveContextService.build(approvalId, KWAK)).isNull();
    }

    @Test
    @DisplayName("★ 권한: 문서를 볼 수 없는 사람은 연차 맥락도 볼 수 없다 - ApprovalQueryService.findDoc 을 그대로 태운다")
    void deniesContextToNonViewer() {
        Long approvalId = draftLeave(KWAK, LEAVE_DATE, LEAVE_DATE);

        assertThatThrownBy(() -> leaveContextService.build(approvalId, OUTSIDER))
                .isInstanceOf(ApprovalAccessDeniedException.class);
    }

    @Test
    @DisplayName("팀에 그날 부재자가 없으면 가동률은 100%다")
    void fullAvailabilityWhenNoOneIsAbsent() {
        Long approvalId = draftLeave(KWAK, LEAVE_DATE, LEAVE_DATE);

        LeaveContext context = leaveContextService.build(approvalId, KWAK);

        assertThat(context.getTeamAvailability().getAbsentCount()).isZero();
        assertThat(context.getTeamAvailability().getAvailabilityPercent()).isEqualTo(100);
    }

    private Long draftLeave(Long drafterId, LocalDate start, LocalDate end) {
        ApprovalForm form = new ApprovalForm();
        form.setDocType(DocType.LEAVE);
        form.setTitle("연차 신청 - 맥락 패널 테스트");
        form.setAmount(BigDecimal.ZERO);
        form.setLeaveType(LeaveType.ANNUAL);
        form.setStartDate(start);
        form.setEndDate(end);
        form.setReason("통합 테스트");
        return approvalService.saveDraft(form, drafterId);
    }

    private void insertAttendance(Long empId, LocalDate date, String checkInTime, String status, int overtimeMinutes) {
        jdbcTemplate.update(
                "INSERT INTO attendance (emp_id, work_date, check_in, work_minutes, overtime_minutes, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                empId, date, Timestamp.valueOf(date.atTime(LocalTime.parse(checkInTime))),
                480, overtimeMinutes, status);
    }

    /** 결근 - 출근 기록이 없다(41-seed-attendance.sql 의 mm=0 행과 같은 모양) */
    private void insertAbsence(Long empId, LocalDate date) {
        jdbcTemplate.update(
                "INSERT INTO attendance (emp_id, work_date, work_minutes, overtime_minutes, status) "
                        + "VALUES (?, ?, 0, 0, ?)",
                empId, date, AttendanceStatus.ABSENT);
    }

    /** 연차 반영과 같은 모양 - check_in/check_out 이 없다(DefaultLeaveApplyService.apply 참조) */
    private void insertLeave(Long empId, LocalDate date) {
        jdbcTemplate.update(
                "INSERT INTO attendance (emp_id, work_date, work_minutes, overtime_minutes, status) "
                        + "VALUES (?, ?, 0, 0, ?)",
                empId, date, AttendanceStatus.LEAVE);
    }
}
