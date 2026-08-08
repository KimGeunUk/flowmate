package com.flowmate.approval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.flowmate.approval.domain.ApprovalDoc;
import com.flowmate.approval.domain.ApprovalForm;
import com.flowmate.approval.domain.ApprovalHistory;
import com.flowmate.approval.domain.ApprovalLine;
import com.flowmate.approval.domain.ApprovalStatus;
import com.flowmate.approval.domain.DocType;
import com.flowmate.approval.domain.HistoryAction;
import com.flowmate.approval.domain.LineStatus;
import com.flowmate.approval.domain.RejectReason;
import com.flowmate.approval.mapper.ApprovalHistoryMapper;
import com.flowmate.approval.mapper.ApprovalLineMapper;
import com.flowmate.common.exception.ApprovalAccessDeniedException;

/**
 * 결재 처리의 트랜잭션 경계. 시드(문서 6건)와 조직 시드(사원 20)를 전제로 한다.
 *
 * 사원번호: 곽수빈 18(개발팀 사원), 신동혁 14(개발팀 과장), 박현주 3(사업본부 부장), 정도현 1(이사)
 */
@SpringBootTest
@Transactional
class ApprovalServiceIT {

    private static final Long KWAK = 18L;
    private static final Long SHIN = 14L;
    private static final Long PARK = 3L;
    private static final Long JEONG = 1L;

    @Autowired
    private ApprovalService approvalService;

    @Autowired
    private ApprovalQueryService queryService;

    @Autowired
    private ApprovalLineMapper lineMapper;

    @Autowired
    private ApprovalHistoryMapper historyMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("임시저장하면 문서번호가 부여되고 결재선이 자동 생성된다")
    void saveDraftGeneratesDocNoAndLines() {
        Long id = approvalService.saveDraft(newForm("소액 지출", "1000000"), KWAK);

        ApprovalDoc doc = queryService.findDoc(id, KWAK);
        assertThat(doc.getDocNo()).matches("EXP-\\d{4}-\\d{4}");
        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.DRAFT);
        assertThat(doc.getCurrentStep()).isZero();

        List<ApprovalLine> lines = lineMapper.findByApprovalId(id);
        assertThat(lines).extracting(ApprovalLine::getApproverId).containsExactly(SHIN, PARK);
        assertThat(lines).allSatisfy(l -> assertThat(l.getStatus()).isEqualTo(LineStatus.WAITING));
    }

    @Test
    @DisplayName("금액이 크면 결재선에 이사가 추가된다 - 정책이 실제로 적용된다")
    void largeAmountAddsExecutiveToLine() {
        Long id = approvalService.saveDraft(newForm("고액 구매", "5000000"), KWAK);

        assertThat(lineMapper.findByApprovalId(id))
                .extracting(ApprovalLine::getApproverId)
                .containsExactly(SHIN, PARK, JEONG);
    }

    @Test
    @DisplayName("이사가 기안하면 결재선이 비어 있다")
    void executiveDraftHasNoLine() {
        Long id = approvalService.saveDraft(newForm("이사 기안", "1000000"), JEONG);

        assertThat(lineMapper.findByApprovalId(id)).isEmpty();
    }

    @Test
    @DisplayName("문서번호는 같은 유형·연도에서 이어진다")
    void docNoIncrementsPerTypeAndYear() {
        Long first = approvalService.saveDraft(newForm("첫 번째", "10000"), KWAK);
        Long second = approvalService.saveDraft(newForm("두 번째", "10000"), KWAK);

        String firstNo = queryService.findDoc(first, KWAK).getDocNo();
        String secondNo = queryService.findDoc(second, KWAK).getDocNo();

        int firstSeq = Integer.parseInt(firstNo.substring(firstNo.length() - 4));
        int secondSeq = Integer.parseInt(secondNo.substring(secondNo.length() - 4));
        assertThat(secondSeq).isEqualTo(firstSeq + 1);
    }

    @Test
    @DisplayName("임시저장 문서는 기안자만 수정할 수 있다")
    void onlyDrafterCanEditDraft() {
        Long id = approvalService.saveDraft(newForm("원본", "10000"), KWAK);

        ApprovalForm changed = newForm("수정본", "20000");
        changed.setApprovalId(id);

        assertThatThrownBy(() -> approvalService.saveDraft(changed, SHIN))
                .isInstanceOf(ApprovalAccessDeniedException.class);

        approvalService.saveDraft(changed, KWAK);
        assertThat(queryService.findDoc(id, KWAK).getTitle()).isEqualTo("수정본");
    }

    @Test
    @DisplayName("수정할 때 금액이 바뀌면 결재선을 다시 만든다")
    void editingAmountRebuildsLine() {
        Long id = approvalService.saveDraft(newForm("소액", "1000000"), KWAK);
        assertThat(lineMapper.findByApprovalId(id)).hasSize(2);

        ApprovalForm changed = newForm("고액으로 수정", "5000000");
        changed.setApprovalId(id);
        approvalService.saveDraft(changed, KWAK);

        assertThat(lineMapper.findByApprovalId(id))
                .extracting(ApprovalLine::getApproverId)
                .containsExactly(SHIN, PARK, JEONG);
    }

    @Test
    @DisplayName("상신하면 진행 중이 되고 1단계가 현재 차례가 된다")
    void submitActivatesFirstStep() {
        Long id = approvalService.saveDraft(newForm("상신 대상", "1000000"), KWAK);

        approvalService.submit(id, KWAK);

        ApprovalDoc doc = queryService.findDoc(id, KWAK);
        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(doc.getCurrentStep()).isEqualTo(1);

        List<ApprovalLine> lines = lineMapper.findByApprovalId(id);
        assertThat(lines.get(0).getStatus()).isEqualTo(LineStatus.CURRENT);
        assertThat(lines.get(1).getStatus()).isEqualTo(LineStatus.WAITING);
    }

    @Test
    @DisplayName("결재자가 없으면 상신 즉시 완료된다")
    void submitWithoutApproverCompletesImmediately() {
        Long id = approvalService.saveDraft(newForm("이사 기안", "1000000"), JEONG);

        approvalService.submit(id, JEONG);

        ApprovalDoc doc = queryService.findDoc(id, JEONG);
        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(doc.getCompletedAt()).isNotNull();
        // 승인한 사람이 없으므로 APPROVE 이력을 지어내지 않는다
        assertThat(queryService.findHistories(id))
                .extracting(ApprovalHistory::getAction)
                .containsExactly(HistoryAction.DRAFT, HistoryAction.SUBMIT);
    }

    @Test
    @DisplayName("기안자가 아니면 상신할 수 없다")
    void onlyDrafterCanSubmit() {
        Long id = approvalService.saveDraft(newForm("남의 문서", "1000000"), KWAK);

        assertThatThrownBy(() -> approvalService.submit(id, SHIN))
                .isInstanceOf(ApprovalAccessDeniedException.class);
    }

    @Test
    @DisplayName("★ 사원 기안 → 팀장 승인 → 부장 승인 → 완료 전 과정이 이어진다")
    void fullApprovalFlowCompletes() {
        Long id = approvalService.saveDraft(newForm("전 과정", "1000000"), KWAK);
        approvalService.submit(id, KWAK);

        approvalService.approve(id, SHIN, "확인했습니다");

        ApprovalDoc mid = queryService.findDoc(id, KWAK);
        assertThat(mid.getStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(mid.getCurrentStep()).isEqualTo(2);
        assertThat(lineMapper.findStep(id, 1).getStatus()).isEqualTo(LineStatus.APPROVED);
        assertThat(lineMapper.findStep(id, 2).getStatus()).isEqualTo(LineStatus.CURRENT);

        approvalService.approve(id, PARK, null);

        ApprovalDoc done = queryService.findDoc(id, KWAK);
        assertThat(done.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(done.getCompletedAt()).isNotNull();
        assertThat(lineMapper.findStep(id, 2).getStatus()).isEqualTo(LineStatus.APPROVED);

        assertThat(queryService.findHistories(id))
                .extracting(ApprovalHistory::getAction)
                .containsExactly(HistoryAction.DRAFT, HistoryAction.SUBMIT,
                                 HistoryAction.APPROVE, HistoryAction.APPROVE);
    }

    @Test
    @DisplayName("자기 차례가 아니면 승인할 수 없다")
    void cannotApproveOutOfTurn() {
        Long id = approvalService.saveDraft(newForm("순서 확인", "1000000"), KWAK);
        approvalService.submit(id, KWAK);

        // 2단계 결재자가 1단계를 건너뛰고 승인하려 함 - 1단계는 CURRENT 이지만
        // 그 결재자는 박현주가 아니라 신동혁이므로 여전히 권한 문제다 (상태 문제가 아니다)
        assertThatThrownBy(() -> approvalService.approve(id, PARK, null))
                .isInstanceOf(ApprovalAccessDeniedException.class);
    }

    @Test
    @DisplayName("이미 처리된 단계를 다시 승인하려 하면 권한이 아니라 상태 문제로 거부한다")
    void reApprovingProcessedStepIsStateError() {
        // 신동혁(과장)이 기안하면 결재선은 박현주 1명뿐이다 (로드맵 §5.1 검증 표).
        // 결재자가 1명이라 첫 승인이 곧 최종 승인이고, current_step 이 그대로 1에 머문다 —
        // 그래서 다시 눌러도 "다른 사람 차례"가 아니라 "이미 끝난 단계"로 걸린다.
        Long id = approvalService.saveDraft(newForm("중복 클릭", "1000000"), SHIN);
        approvalService.submit(id, SHIN);
        approvalService.approve(id, PARK, null);

        // 박현주가 같은 버튼을 한 번 더 누른 상황 - 그의 단계는 이미 APPROVED 다
        assertThatThrownBy(() -> approvalService.approve(id, PARK, null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("★ 반려하면 뒤 단계가 건너뛰기 처리되고 반려 이력이 유형과 함께 쌓인다")
    void rejectSkipsRemainingAndRecordsReason() {
        Long id = approvalService.saveDraft(newForm("반려 대상", "1000000"), KWAK);
        approvalService.submit(id, KWAK);

        approvalService.reject(id, SHIN, RejectReason.MISSING_EVIDENCE, "견적서를 첨부해 주세요");

        ApprovalDoc doc = queryService.findDoc(id, KWAK);
        assertThat(doc.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(lineMapper.findStep(id, 1).getStatus()).isEqualTo(LineStatus.REJECTED);
        assertThat(lineMapper.findStep(id, 2).getStatus()).isEqualTo(LineStatus.SKIPPED);

        // ★ Phase 5 사전점검이 읽는 표
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM approval_reject_history WHERE approval_id = ? AND reason_category = ?",
                Integer.class, id, RejectReason.MISSING_EVIDENCE);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("반려 유형이 없거나 잘못되면 반려 자체를 거부한다")
    void rejectRequiresValidReasonCategory() {
        Long id = approvalService.saveDraft(newForm("유형 검증", "1000000"), KWAK);
        approvalService.submit(id, KWAK);

        assertThatThrownBy(() -> approvalService.reject(id, SHIN, null, "이유"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> approvalService.reject(id, SHIN, "NOT_A_REASON", "이유"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("첫 승인 전이면 기안자가 회수할 수 있다")
    void drafterCanCancelBeforeFirstApproval() {
        Long id = approvalService.saveDraft(newForm("회수 대상", "1000000"), KWAK);
        approvalService.submit(id, KWAK);

        approvalService.cancel(id, KWAK);

        assertThat(queryService.findDoc(id, KWAK).getStatus()).isEqualTo(ApprovalStatus.CANCELED);
    }

    @Test
    @DisplayName("한 단계라도 승인되면 회수할 수 없다")
    void cannotCancelAfterFirstApproval() {
        Long id = approvalService.saveDraft(newForm("회수 불가", "1000000"), KWAK);
        approvalService.submit(id, KWAK);
        approvalService.approve(id, SHIN, null);

        assertThatThrownBy(() -> approvalService.cancel(id, KWAK))
                .isInstanceOf(IllegalStateException.class);
    }

    private ApprovalForm newForm(String title, String amount) {
        ApprovalForm form = new ApprovalForm();
        form.setDocType(DocType.EXPENSE);
        form.setTitle(title);
        form.setContent("본문 내용");
        form.setAmount(new BigDecimal(amount));
        return form;
    }
}
