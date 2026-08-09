package com.flowmate.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.approval.domain.ApprovalForm;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.LeaveRequest;
import com.flowmate.approval.mapper.LeaveRequestMapper;
import com.flowmate.attendance.domain.LeaveType;

/**
 * 기안 화면에서 docType=LEAVE 를 고르면 서버가 영업일수를 계산해 leave_request 에
 * 저장한다 (계획서 4 Task 5, D5). 2026년 7월은 공휴일이 없으므로(HolidayMapperIT
 * 로 확인됨) 그 달의 금~월(2026-07-03~07-06)을 경계 검증에 그대로 쓴다.
 *
 * 사원 18(곽수빈)로 기안한다 — 시드에 있는 실제 사원이다.
 */
@SpringBootTest
@Transactional
class ApprovalServiceLeaveDraftIT {

    private static final Long DRAFTER_ID = 18L;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private LeaveRequestMapper leaveRequestMapper;

    @Test
    @DisplayName("★ 완료 기준: LEAVE 기안은 서버가 계산한 영업일수로 leave_request 가 만들어진다")
    void leaveDraftComputesBusinessDaysAndPersistsLeaveRequest() {
        ApprovalForm form = leaveForm(LeaveType.ANNUAL, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 6));

        Long approvalId = approvalService.saveDraft(form, DRAFTER_ID);

        LeaveRequest saved = leaveRequestMapper.findByApprovalId(approvalId);
        assertThat(saved).isNotNull();
        assertThat(saved.getDays()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("★ 영업일이 0일인 기간은 기안 자체가 거부된다 — 의미 없는 신청이기 때문이다")
    void zeroBusinessDayRangeIsRejected() {
        // 2026-07-04(토) ~ 2026-07-05(일) - 주말뿐이라 영업일이 0일이다
        ApprovalForm form = leaveForm(LeaveType.ANNUAL, LocalDate.of(2026, 7, 4), LocalDate.of(2026, 7, 5));

        assertThatThrownBy(() -> approvalService.saveDraft(form, DRAFTER_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("반차는 시작=종료일 때 0.5일로 저장된다")
    void halfDayLeaveIsFixedAtHalfDay() {
        ApprovalForm form = leaveForm(LeaveType.HALF_AM, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 3));

        Long approvalId = approvalService.saveDraft(form, DRAFTER_ID);

        LeaveRequest saved = leaveRequestMapper.findByApprovalId(approvalId);
        assertThat(saved.getDays()).isEqualByComparingTo("0.5");
    }

    @Test
    @DisplayName("반차가 하루를 넘으면 기안이 거부된다")
    void halfDaySpanningMultipleDaysIsRejected() {
        ApprovalForm form = leaveForm(LeaveType.HALF_PM, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 6));

        assertThatThrownBy(() -> approvalService.saveDraft(form, DRAFTER_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("기간과 유형 없이 LEAVE 를 기안할 수 없다")
    void missingPeriodFieldsAreRejected() {
        ApprovalForm form = new ApprovalForm();
        form.setDocType(DocType.LEAVE);
        form.setTitle("연차 신청");
        form.setAmount(BigDecimal.ZERO);

        assertThatThrownBy(() -> approvalService.saveDraft(form, DRAFTER_ID))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("LEAVE 임시저장을 수정하면 leave_request 일수도 다시 계산된다")
    void updatingLeaveDraftRecalculatesDays() {
        ApprovalForm form = leaveForm(LeaveType.ANNUAL, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 6));
        Long approvalId = approvalService.saveDraft(form, DRAFTER_ID);

        ApprovalForm update = leaveForm(LeaveType.ANNUAL, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 3));
        update.setApprovalId(approvalId);
        approvalService.saveDraft(update, DRAFTER_ID);

        LeaveRequest saved = leaveRequestMapper.findByApprovalId(approvalId);
        assertThat(saved.getDays()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("docType 을 LEAVE 에서 다른 유형으로 바꾸면 leave_request 행이 사라진다")
    void changingAwayFromLeaveDeletesLeaveRequest() {
        ApprovalForm form = leaveForm(LeaveType.ANNUAL, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 6));
        Long approvalId = approvalService.saveDraft(form, DRAFTER_ID);

        ApprovalForm update = new ApprovalForm();
        update.setApprovalId(approvalId);
        update.setDocType(DocType.EXPENSE);
        update.setTitle("지출결의로 변경");
        update.setContent("본문");
        update.setAmount(BigDecimal.ZERO);
        approvalService.saveDraft(update, DRAFTER_ID);

        assertThat(leaveRequestMapper.findByApprovalId(approvalId)).isNull();
    }

    @Test
    @DisplayName("LEAVE 가 아닌 기안은 leave_request 를 건드리지 않는다")
    void nonLeaveDraftDoesNotTouchLeaveRequest() {
        ApprovalForm form = new ApprovalForm();
        form.setDocType(DocType.EXPENSE);
        form.setTitle("일반 지출결의");
        form.setContent("본문");
        form.setAmount(BigDecimal.ZERO);

        Long approvalId = approvalService.saveDraft(form, DRAFTER_ID);

        assertThat(leaveRequestMapper.findByApprovalId(approvalId)).isNull();
    }

    private ApprovalForm leaveForm(String leaveType, LocalDate start, LocalDate end) {
        ApprovalForm form = new ApprovalForm();
        form.setDocType(DocType.LEAVE);
        form.setTitle("연차 신청");
        form.setAmount(BigDecimal.ZERO);
        form.setLeaveType(leaveType);
        form.setStartDate(start);
        form.setEndDate(end);
        form.setReason("개인 사유");
        return form;
    }
}
