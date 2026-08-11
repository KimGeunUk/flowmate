package com.flowmate.approval.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalForm;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.DocType;
import com.flowmate.attendance.domain.Attendance;
import com.flowmate.attendance.domain.AttendanceStatus;
import com.flowmate.attendance.domain.LeaveApplyCommand;
import com.flowmate.attendance.domain.LeaveBalance;
import com.flowmate.attendance.domain.LeaveType;
import com.flowmate.attendance.mapper.AttendanceMapper;
import com.flowmate.attendance.mapper.LeaveBalanceMapper;
import com.flowmate.attendance.mapper.LeaveUsageMapper;
import com.flowmate.attendance.service.LeaveApplyService;

/**
 * ★★ 이 Phase의 척추 — 성공 경로들.
 *
 * 실패·롤백 경로(테스트 2, 3)는 별도 클래스 {@link ApprovalServiceLeaveApplyRollbackIT}
 * 에 있다 — 이 클래스처럼 {@code @Transactional} 을 쓰면, approve() 안에서 이미
 * 실행된 쓰기(문서 상태 갱신 등)가 "아직 롤백되지 않은 채" 같은 커넥션으로
 * 다시 읽혀서 거짓으로 보일 수 있기 때문이다 (그 클래스의 클래스 주석 참조).
 * 이 클래스의 테스트들은 예외를 기대하지 않으므로 그 함정이 없다.
 *
 * 사원 18(곽수빈)의 2026년 잔여는 부여 17.0/사용 5.0(잔여 12.0)이다 (시드,
 * LeaveBalanceMapperIT 로 확인됨). 2026년 7월은 공휴일이 없다(HolidayMapperIT).
 * 곽수빈의 결재선은 신동혁(14, 팀장) → 박현주(3, 부장) 2단계다 (ApprovalServiceIT).
 */
@SpringBootTest
@Transactional
class ApprovalServiceLeaveApplyIT {

    private static final Long KWAK = 18L;
    private static final Long SHIN = 14L;
    private static final Long PARK = 3L;

    private static final LocalDate FRI = LocalDate.of(2026, 7, 3);
    private static final LocalDate SAT = LocalDate.of(2026, 7, 4);
    private static final LocalDate SUN = LocalDate.of(2026, 7, 5);
    private static final LocalDate MON = LocalDate.of(2026, 7, 6);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * ★ nonLeaveApprovalTouchesNothing 이 "오늘 근태 행이 없다"를 단정하므로
     *   그 상태를 테스트가 직접 만든다. 이 DB 는 앱과 공유하는 것이라 누구든
     *   화면에서 출근을 누르면 그 단정이 깨진다(AttendanceServiceIT 와 같은 이유).
     *   클래스가 @Transactional 이라 이 삭제도 함께 롤백된다.
     */
    @BeforeEach
    void clearTodaysAttendance() {
        jdbcTemplate.update("DELETE FROM attendance WHERE emp_id = ? AND work_date = ?",
                KWAK, LocalDate.now());
    }

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalQueryService queryService;

    @Autowired
    private LeaveBalanceMapper leaveBalanceMapper;

    @Autowired
    private AttendanceMapper attendanceMapper;

    @Autowired
    private LeaveUsageMapper leaveUsageMapper;

    @Autowired
    private LeaveApplyService leaveApplyService;

    @Test
    @DisplayName("★ 완료 기준: 연차 승인이 완료되면 잔여가 줄고 해당 일자 근태가 LEAVE 로 바뀐다")
    void approvingLeaveReducesBalanceAndMarksAttendance() {
        Long id = fullyApproveLeave(KWAK, LeaveType.ANNUAL, FRI, MON);

        ApprovalDoc doc = queryService.findDoc(id, KWAK);
        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.APPROVED);

        LeaveBalance balance = leaveBalanceMapper.findByEmpIdAndYear(KWAK, 2026);
        assertThat(balance.getUsedDays()).isEqualByComparingTo("7.0");   // 5.0 + 2일
        assertThat(balance.getRemainingDays()).isEqualByComparingTo("10.0");

        assertThat(attendanceMapper.findByEmpIdAndWorkDate(KWAK, FRI).getStatus())
                .isEqualTo(AttendanceStatus.LEAVE);
        assertThat(attendanceMapper.findByEmpIdAndWorkDate(KWAK, MON).getStatus())
                .isEqualTo(AttendanceStatus.LEAVE);
    }

    @Test
    @DisplayName("같은 결재를 두 번 반영해도 1회만 반영된다 (D2 — 조회 후 분기)")
    void applyingTwiceDoesNotDoubleCount() {
        Long id = fullyApproveLeave(KWAK, LeaveType.ANNUAL, FRI, MON);

        // approve() 가 이미 한 번 반영했다. 같은 값으로 다시 반영을 시도한다 —
        // 재시도·중복 요청처럼 leave_usage 가 이미 있는 approvalId 로 apply() 가
        // 다시 불리는 상황을 흉내낸다.
        leaveApplyService.apply(new LeaveApplyCommand(id, KWAK, LeaveType.ANNUAL, FRI, MON, new BigDecimal("2")));

        LeaveBalance balance = leaveBalanceMapper.findByEmpIdAndYear(KWAK, 2026);
        assertThat(balance.getUsedDays()).isEqualByComparingTo("7.0");   // 9.0 이 아니라 여전히 7.0
    }

    @Test
    @DisplayName("★ 금~월 연차는 토·일에 근태를 만들지 않는다")
    void weekendsAndHolidaysAreNotMarked() {
        fullyApproveLeave(KWAK, LeaveType.ANNUAL, FRI, MON);

        assertThat(attendanceMapper.findByEmpIdAndWorkDate(KWAK, SAT)).isNull();
        assertThat(attendanceMapper.findByEmpIdAndWorkDate(KWAK, SUN)).isNull();
        assertThat(attendanceMapper.findByEmpIdAndWorkDate(KWAK, FRI)).isNotNull();
        assertThat(attendanceMapper.findByEmpIdAndWorkDate(KWAK, MON)).isNotNull();
    }

    @Test
    @DisplayName("반차 승인은 HALF_LEAVE 로 표시되고 잔여는 0.5 만 줄어든다")
    void halfDayLeaveMarksHalfLeave() {
        LocalDate wed = LocalDate.of(2026, 7, 8);

        Long id = fullyApproveLeave(KWAK, LeaveType.HALF_AM, wed, wed);

        assertThat(queryService.findDoc(id, KWAK).getStatus()).isEqualTo(ApprovalStatus.APPROVED);

        LeaveBalance balance = leaveBalanceMapper.findByEmpIdAndYear(KWAK, 2026);
        assertThat(balance.getUsedDays()).isEqualByComparingTo("5.5");

        Attendance attendance = attendanceMapper.findByEmpIdAndWorkDate(KWAK, wed);
        assertThat(attendance.getStatus()).isEqualTo(AttendanceStatus.HALF_LEAVE);
    }

    @Test
    @DisplayName("연차가 아닌 문서를 승인해도 근태·연차 잔여는 건드리지 않는다")
    void nonLeaveApprovalTouchesNothing() {
        ApprovalForm form = new ApprovalForm();
        form.setDocType(DocType.EXPENSE);
        form.setTitle("지출결의 - 근태 무관");
        form.setContent("본문");
        form.setAmount(new BigDecimal("10000"));

        Long id = approvalService.saveDraft(form, KWAK);
        approvalService.submit(id, KWAK);
        approvalService.approve(id, SHIN, null);
        approvalService.approve(id, PARK, null);

        assertThat(queryService.findDoc(id, KWAK).getStatus()).isEqualTo(ApprovalStatus.APPROVED);

        LeaveBalance balance = leaveBalanceMapper.findByEmpIdAndYear(KWAK, 2026);
        assertThat(balance.getUsedDays()).isEqualByComparingTo("5.0");   // 시드값 그대로
        assertThat(attendanceMapper.findByEmpIdAndWorkDate(KWAK, LocalDate.now())).isNull();
    }

    /** 기안 → 상신 → 2단계 전부 승인까지 진행해 완료(APPROVED)시킨다 */
    private Long fullyApproveLeave(Long drafterId, String leaveType, LocalDate start, LocalDate end) {
        ApprovalForm form = new ApprovalForm();
        form.setDocType(DocType.LEAVE);
        form.setTitle("연차 신청");
        form.setAmount(BigDecimal.ZERO);
        form.setLeaveType(leaveType);
        form.setStartDate(start);
        form.setEndDate(end);
        form.setReason("통합 테스트");

        Long id = approvalService.saveDraft(form, drafterId);
        approvalService.submit(id, drafterId);
        approvalService.approve(id, SHIN, "확인");
        approvalService.approve(id, PARK, "승인");
        return id;
    }
}
