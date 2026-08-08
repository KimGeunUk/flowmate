package com.flowmate.approval.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 결재 문서의 상태 기계. 설계서 §6.2 가 정의한 전이만 허용하고 나머지는 즉시 예외로 막는다.
 * DB 도 Spring 도 없는 순수 단위 테스트다.
 */
class ApprovalDocTest {

    private static final Long DRAFTER_ID = 18L;   // 곽수빈
    private static final Long APPROVER_ID = 14L;  // 신동혁
    private static final Long OTHER_ID = 3L;      // 박현주

    private ApprovalDoc doc;

    @BeforeEach
    void setUp() {
        doc = new ApprovalDoc();
        doc.setApprovalId(1L);
        doc.setDocNo("EXP-2026-0001");
        doc.setDocType(DocType.EXPENSE);
        doc.setTitle("3월 출장비 정산");
        doc.setDrafterId(DRAFTER_ID);
        doc.setDeptId(7L);
        doc.setAmount(new BigDecimal("540000"));
        doc.setStatus(ApprovalStatus.DRAFT);
        doc.setCurrentStep(0);
        doc.setDraftedAt(LocalDateTime.of(2026, 3, 2, 9, 10));
    }

    @Test
    @DisplayName("임시저장 문서를 상신하면 결재 진행 중이 되고 1단계부터 시작한다")
    void submitMovesDraftToPending() {
        doc.submit(2);

        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(doc.getCurrentStep()).isEqualTo(1);
        assertThat(doc.getSubmittedAt()).isNotNull();
        assertThat(doc.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("결재자가 없으면 상신 즉시 승인 완료된다")
    void submitWithNoApproverCompletesImmediately() {
        // 이사가 기안한 문서처럼 위에 결재할 사람이 없는 경우다.
        // PENDING 을 거치면 결재선이 빈 채로 대기하는 유령 문서가 되므로 직행시킨다.
        doc.submit(0);

        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(doc.getCurrentStep()).isZero();
        assertThat(doc.getSubmittedAt()).isNotNull();
        assertThat(doc.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("임시저장이 아닌 문서는 상신할 수 없다")
    void cannotSubmitUnlessDraft() {
        doc.submit(2);

        assertThatThrownBy(() -> doc.submit(2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("임시저장");
    }

    @Test
    @DisplayName("결재자 수가 음수면 상신 자체를 거부한다")
    void rejectsNegativeApproverCount() {
        assertThatThrownBy(() -> doc.submit(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("중간 승인은 진행 중을 유지하고 단계만 하나 올린다")
    void intermediateApprovalAdvancesStep() {
        doc.submit(2);

        doc.approve(2);

        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(doc.getCurrentStep()).isEqualTo(2);
        assertThat(doc.getCompletedAt()).isNull();
    }

    @Test
    @DisplayName("마지막 단계에서 승인하면 완료된다")
    void finalApprovalCompletesDocument() {
        doc.submit(2);
        doc.approve(2);

        doc.approve(2);

        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(doc.getCompletedAt()).isNotNull();
        assertThat(doc.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("결재자가 1명이면 첫 승인이 곧 최종 승인이다")
    void singleApproverCompletesOnFirstApproval() {
        doc.submit(1);

        doc.approve(1);

        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(doc.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("진행 중이 아닌 문서는 승인할 수 없다")
    void cannotApproveUnlessPending() {
        assertThatThrownBy(() -> doc.approve(2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("진행 중");
    }

    @Test
    @DisplayName("반려하면 즉시 종결된다")
    void rejectTerminatesDocument() {
        doc.submit(2);

        doc.reject();

        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(doc.getCompletedAt()).isNotNull();
        assertThat(doc.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("진행 중이 아닌 문서는 반려할 수 없다")
    void cannotRejectUnlessPending() {
        assertThatThrownBy(doc::reject)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("진행 중");
    }

    @Test
    @DisplayName("임시저장 문서는 기안자가 회수할 수 있다")
    void drafterCanCancelDraft() {
        doc.cancel(DRAFTER_ID);

        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.CANCELED);
        assertThat(doc.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("첫 승인 전이면 상신한 문서도 회수할 수 있다")
    void drafterCanCancelBeforeFirstApproval() {
        doc.submit(2);

        doc.cancel(DRAFTER_ID);

        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.CANCELED);
    }

    @Test
    @DisplayName("한 단계라도 승인된 문서는 기안자도 회수할 수 없다")
    void cannotCancelAfterFirstApproval() {
        doc.submit(2);
        doc.approve(2);

        assertThatThrownBy(() -> doc.cancel(DRAFTER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 결재가 진행");
    }

    @Test
    @DisplayName("기안자가 아니면 회수할 수 없다")
    void nonDrafterCannotCancel() {
        assertThatThrownBy(() -> doc.cancel(OTHER_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("기안자");
    }

    @Test
    @DisplayName("종결된 문서는 어떤 전이도 받지 않는다")
    void terminalDocumentAcceptsNoTransition() {
        doc.submit(1);
        doc.approve(1);

        assertThatThrownBy(() -> doc.submit(1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> doc.approve(1)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(doc::reject).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> doc.cancel(DRAFTER_ID)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("임시저장 문서만 수정할 수 있다")
    void onlyDraftIsEditable() {
        assertThat(doc.isEditable()).isTrue();

        doc.submit(2);

        assertThat(doc.isEditable()).isFalse();
    }

    @Test
    @DisplayName("결재자가 지금 이 문서를 처리할 차례인지 판정한다")
    void identifiesCurrentApproverStep() {
        doc.submit(2);

        assertThat(doc.isAwaitingStep(1)).isTrue();
        assertThat(doc.isAwaitingStep(2)).isFalse();

        doc.approve(2);

        assertThat(doc.isAwaitingStep(1)).isFalse();
        assertThat(doc.isAwaitingStep(2)).isTrue();
    }
}
