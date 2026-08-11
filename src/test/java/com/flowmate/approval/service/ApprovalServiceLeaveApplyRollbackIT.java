package com.flowmate.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalForm;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.LineStatus;
import com.flowmate.approval.mapper.ApprovalDocMapper;
import com.flowmate.approval.mapper.ApprovalLineMapper;
import com.flowmate.attendance.domain.LeaveBalance;
import com.flowmate.attendance.domain.LeaveType;
import com.flowmate.attendance.mapper.AttendanceMapper;
import com.flowmate.attendance.mapper.LeaveBalanceMapper;
import com.flowmate.attendance.mapper.LeaveUsageMapper;

/**
 * ★★ 테스트 2 · 3 — approve() 가 실패하면 **정말로 전부 롤백되는지** 증명한다
 * Spring 이벤트를 거부하고 같은
 * 트랜잭션의 직접 호출을 선택한 이유가 이것이고, 이 클래스가 없으면 그 판단이
 * 증명되지 않는다.
 *
 * ★ 왜 이 클래스는 {@code @Transactional} 을 쓰지 않는가.
 *
 * {@code @Transactional} 테스트는 테스트 메서드 전체를 하나의 물리 트랜잭션으로
 * 감싸고, 그 안에서 부르는 모든 Spring 빈 호출(기본 전파 REQUIRED)이 **같은
 * 커넥션**을 공유한다. approve() 안에서 근태 반영 직전까지 이미 실행된 쓰기
 * (결재선 상태 갱신, 문서 상태 갱신)는, 뒤에서 예외가 나 approve() 의
 * @Transactional 경계가 rollbackOnly 를 표시해도, **그 자리에서 즉시
 * ROLLBACK 되지 않는다** — 실제 ROLLBACK 은 테스트 메서드가 끝나고 테스트
 * 프레임워크가 트랜잭션을 정리할 때 비로소 일어난다. 그러므로 같은
 * 트랜잭션(=같은 커넥션) 안에서 예외를 잡은 직후 문서 상태를 다시 읽으면,
 * 아직 커밋도 롤백도 되지 않은 "쓰다 만" 값(예: 이미 APPROVED 로 갱신된 값)을
 * 그대로 다시 읽게 되어 "롤백됐다"는 assert 가 거짓으로 통과하거나 거짓으로
 * 실패한다 — 검증하려는 것(서비스의 롤백)이 아니라 테스트 트랜잭션의 존재
 * 자체를 검증하게 되는 함정이다.
 *
 * 그래서 이 클래스의 각 테스트 메서드는 **트랜잭션 없이** 실행된다.
 * approvalService.saveDraft()/submit()/approve() 는 각각 자기 자신의
 * @Transactional 경계에서 커밋되거나(성공) 시작한 트랜잭션의 소유자로서
 * 진짜 ROLLBACK 을 실행한다(실패). 그 다음에 별도 호출로 다시 조회하면,
 * 그 조회는 새 트랜잭션·새로운 시점의 커밋된 DB 상태를 읽는다 — 이번에는
 * 진짜다. 대신 각 테스트가 만든 행은 자동으로 롤백되지 않으므로
 * {@link #cleanUp()} 에서 직접 지운다.
 */
@SpringBootTest
class ApprovalServiceLeaveApplyRollbackIT {

    private static final Long KWAK = 18L;   // 잔여 12.0 (부여 17.0/사용 5.0)
    private static final Long SHIN = 14L;   // 잔여 10.0 (부여 20.0/사용 10.0), 결재선은 박현주 1명뿐
    private static final Long PARK = 3L;

    private static final LocalDate FRI = LocalDate.of(2026, 7, 3);
    private static final LocalDate MON = LocalDate.of(2026, 7, 6);

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalDocMapper approvalDocMapper;

    @Autowired
    private ApprovalLineMapper lineMapper;

    @Autowired
    private LeaveBalanceMapper leaveBalanceMapper;

    @Autowired
    private LeaveUsageMapper leaveUsageMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * ★ 실패하는 협력자를 주입한다("make the failure real").
     * AttendanceMapper 를 모킹해서 근태 UPSERT 단계만 확실히 실패시킨다 —
     * 그 앞의 모든 단계(결재선·문서 상태 갱신, leave_balance 잠금·갱신,
     * leave_usage 삽입)는 실제 DB 로 실행된 뒤, 이 실패로 인해 실제
     * 트랜잭션 매니저가 진짜 ROLLBACK 을 실행하는지가 검증 대상이다.
     */
    @MockitoBean
    private AttendanceMapper attendanceMapper;

    private Long createdApprovalId;

    @AfterEach
    void cleanUp() {
        if (createdApprovalId == null) {
            return;
        }
        // 자식 → 부모 순서로 지운다. FK 에 ON DELETE CASCADE 가 없다.
        jdbcTemplate.update("DELETE FROM approval_history WHERE approval_id = ?", createdApprovalId);
        jdbcTemplate.update("DELETE FROM approval_line WHERE approval_id = ?", createdApprovalId);
        jdbcTemplate.update("DELETE FROM leave_request WHERE approval_id = ?", createdApprovalId);
        jdbcTemplate.update("DELETE FROM leave_usage WHERE approval_id = ?", createdApprovalId);
        jdbcTemplate.update("DELETE FROM approval_doc WHERE approval_id = ?", createdApprovalId);
        createdApprovalId = null;
    }

    @Test
    @DisplayName("★★ 근태 반영이 실패하면 결재 승인 자체가 롤백된다 — 같은 트랜잭션임의 증명")
    void attendanceFailureRollsBackTheApproval() {
        willThrow(new RuntimeException("근태 반영 실패 - 테스트로 강제 주입한 장애"))
                .given(attendanceMapper).upsertForLeave(any());

        Long id = draftAndSubmitLeave(SHIN, LeaveType.ANNUAL, FRI, MON);   // 결재선: 박현주 1명
        createdApprovalId = id;

        // 박현주의 승인이 곧 최종 승인이라, 이 한 번의 approve() 호출이
        // ApprovalService.approve() 자신의 트랜잭션 경계다(테스트가 감싸지 않는다) —
        // 실패하면 그 경계에서 실제 ROLLBACK 이 실행된다.
        assertThatThrownBy(() -> approvalService.approve(id, PARK, "승인"))
                .isInstanceOf(RuntimeException.class);

        // ★ 여기서부터는 새 트랜잭션으로 다시 읽는다 — 진짜 커밋된(=롤백된) 상태.
        ApprovalDoc doc = approvalDocMapper.findById(id);
        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.PENDING);   // APPROVED 로 남지 않는다
        assertThat(doc.getCompletedAt()).isNull();

        ApprovalLine line = lineMapper.findStep(id, 1);
        assertThat(line.getStatus()).isEqualTo(LineStatus.CURRENT);      // APPROVED 로 남지 않는다

        LeaveBalance balance = leaveBalanceMapper.findByEmpIdAndYear(SHIN, 2026);
        assertThat(balance.getUsedDays()).isEqualByComparingTo("10.0");  // 시드값 그대로, 12.0 이 아니다

        assertThat(leaveUsageMapper.existsByApprovalId(id)).isFalse();
    }

    @Test
    @DisplayName("★ 잔여 부족으로 승인이 실패하면 문서는 PENDING 에 머물고 잔여는 그대로다")
    void insufficientBalanceBlocksApproval() {
        // 곽수빈 잔여 12.0. 7/6(월)~7/24(금) 3주 평일 = 15 영업일로 잔여를 넘긴다.
        LocalDate start = LocalDate.of(2026, 7, 6);
        LocalDate end = LocalDate.of(2026, 7, 24);

        Long id = draftAndSubmitLeave(KWAK, LeaveType.ANNUAL, start, end);   // 결재선: 신동혁 → 박현주
        createdApprovalId = id;

        approvalService.approve(id, SHIN, "확인");   // 1단계 - 완료 아님, 그대로 커밋된다

        assertThatThrownBy(() -> approvalService.approve(id, PARK, "승인"))
                .isInstanceOf(IllegalStateException.class);

        ApprovalDoc doc = approvalDocMapper.findById(id);
        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(doc.getCurrentStep()).isEqualTo(2);   // 2단계에서 실패해 대기 상태 그대로

        ApprovalLine step1 = lineMapper.findStep(id, 1);
        assertThat(step1.getStatus()).isEqualTo(LineStatus.APPROVED);   // 1단계는 별개 트랜잭션이라 그대로 유지
        ApprovalLine step2 = lineMapper.findStep(id, 2);
        assertThat(step2.getStatus()).isEqualTo(LineStatus.CURRENT);    // APPROVED 로 남지 않는다

        LeaveBalance balance = leaveBalanceMapper.findByEmpIdAndYear(KWAK, 2026);
        assertThat(balance.getUsedDays()).isEqualByComparingTo("5.0");  // 시드값 그대로

        assertThat(leaveUsageMapper.existsByApprovalId(id)).isFalse();
    }

    private Long draftAndSubmitLeave(Long drafterId, String leaveType, LocalDate start, LocalDate end) {
        ApprovalForm form = new ApprovalForm();
        form.setDocType(DocType.LEAVE);
        form.setTitle("연차 신청 - 롤백 테스트");
        form.setAmount(BigDecimal.ZERO);
        form.setLeaveType(leaveType);
        form.setStartDate(start);
        form.setEndDate(end);
        form.setReason("통합 테스트");

        Long id = approvalService.saveDraft(form, drafterId);
        approvalService.submit(id, drafterId);
        return id;
    }
}
