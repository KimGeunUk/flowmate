package com.flowmate.approval.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.LeaveRequest;
import com.flowmate.attendance.domain.LeaveType;

/**
 * leave_request 는 approval_doc 을 PK 로 참조하는 1:1 확장이다.
 * 먼저 approval_doc 행을 만들어야 FK 제약을 지킬 수 있다.
 */
@SpringBootTest
@Transactional
class LeaveRequestMapperIT {

    @Autowired
    private ApprovalDocMapper approvalDocMapper;

    @Autowired
    private LeaveRequestMapper leaveRequestMapper;

    @Test
    @DisplayName("없는 문서의 연차 신청서는 null 이다")
    void findByApprovalIdReturnsNullWhenAbsent() {
        assertThat(leaveRequestMapper.findByApprovalId(99999L)).isNull();
    }

    @Test
    @DisplayName("upsert 는 처음엔 삽입이다")
    void upsertInsertsWhenAbsent() {
        Long approvalId = newLeaveDoc();

        leaveRequestMapper.upsert(leaveRequest(approvalId, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 6), "2"));

        LeaveRequest saved = leaveRequestMapper.findByApprovalId(approvalId);
        assertThat(saved).isNotNull();
        assertThat(saved.getLeaveType()).isEqualTo(LeaveType.ANNUAL);
        assertThat(saved.getStartDate()).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(saved.getEndDate()).isEqualTo(LocalDate.of(2026, 7, 6));
        assertThat(saved.getDays()).isEqualByComparingTo("2");
    }

    @Test
    @DisplayName("★ 같은 approvalId 로 다시 upsert 하면 갱신이다 — 임시저장 반복 수정")
    void upsertUpdatesWhenAlreadyExists() {
        Long approvalId = newLeaveDoc();
        leaveRequestMapper.upsert(leaveRequest(approvalId, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 6), "2"));

        leaveRequestMapper.upsert(leaveRequest(approvalId, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 3), "1"));

        LeaveRequest saved = leaveRequestMapper.findByApprovalId(approvalId);
        assertThat(saved.getEndDate()).isEqualTo(LocalDate.of(2026, 7, 3));
        assertThat(saved.getDays()).isEqualByComparingTo("1");
    }

    @Test
    @DisplayName("삭제하면 더 이상 조회되지 않는다")
    void deleteByApprovalIdRemovesRow() {
        Long approvalId = newLeaveDoc();
        leaveRequestMapper.upsert(leaveRequest(approvalId, LocalDate.of(2026, 7, 3), LocalDate.of(2026, 7, 6), "2"));

        leaveRequestMapper.deleteByApprovalId(approvalId);

        assertThat(leaveRequestMapper.findByApprovalId(approvalId)).isNull();
    }

    private Long newLeaveDoc() {
        ApprovalDoc doc = new ApprovalDoc();
        doc.setDocNo("LEV-2026-9001");
        doc.setDocType(DocType.LEAVE);
        doc.setTitle("연차 신청 통합 테스트");
        doc.setContent(null);
        doc.setDrafterId(18L);
        doc.setDeptId(7L);
        doc.setAmount(BigDecimal.ZERO);
        doc.setStatus(ApprovalStatus.DRAFT);
        doc.setCurrentStep(0);
        doc.setDraftedAt(java.time.LocalDateTime.now());
        approvalDocMapper.insert(doc);
        return doc.getApprovalId();
    }

    private LeaveRequest leaveRequest(Long approvalId, LocalDate start, LocalDate end, String days) {
        LeaveRequest lr = new LeaveRequest();
        lr.setApprovalId(approvalId);
        lr.setLeaveType(LeaveType.ANNUAL);
        lr.setStartDate(start);
        lr.setEndDate(end);
        lr.setDays(new BigDecimal(days));
        lr.setReason("통합 테스트");
        return lr;
    }
}
