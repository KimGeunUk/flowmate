package com.flowmate.approval.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 결재 문서. 상태 전이 규칙을 이 객체 안에 가둔다.
 *
 * Service 가 상태 문자열을 직접 바꾸지 않는 것이 이 설계의 요점이다.
 * 허용되지 않는 전이는 여기서 즉시 예외가 되므로, 잘못된 순서로 부르는 코드가
 * DB 에 닿기 전에 죽는다.
 *
 * drafterName / deptName / docTypeLabel 은 조인·변환 결과를 담는 조회 표시용 필드다.
 * approval_doc 테이블의 컬럼이 아니다.
 */
public class ApprovalDoc implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long approvalId;
    private String docNo;
    private String docType;
    private String title;
    private String content;
    private Long drafterId;
    private Long deptId;
    private BigDecimal amount;
    private String status;
    private int currentStep;
    private LocalDateTime draftedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;

    // 조회 표시용
    private String drafterName;
    private String deptName;
    private String drafterPositionName;

    // ── 상태 전이 ────────────────────────────────────────────────

    /**
     * 상신한다.
     *
     * @param approverCount 생성된 결재선의 결재자 수. 0이면 승인할 사람이 없다는 뜻이다.
     */
    public void submit(int approverCount) {
        if (approverCount < 0) {
            throw new IllegalArgumentException("결재자 수는 0 이상이어야 합니다: " + approverCount);
        }
        if (!ApprovalStatus.DRAFT.equals(this.status)) {
            throw new IllegalStateException("임시저장 상태만 상신할 수 있습니다: " + this.status);
        }
        this.submittedAt = LocalDateTime.now();
        if (approverCount == 0) {
            // 결재할 사람이 없다. PENDING 을 거치면 결재선이 빈 채로 대기하는
            // 유령 문서가 되어 내 결재함이 영원히 찾지 못한다.
            this.status = ApprovalStatus.APPROVED;
            this.currentStep = 0;
            this.completedAt = this.submittedAt;
            return;
        }
        this.status = ApprovalStatus.PENDING;
        this.currentStep = 1;
    }

    /**
     * 현재 단계를 승인한다.
     *
     * @param totalStep 이 문서의 전체 결재 단계 수. Service 가 결재선 길이로 넘긴다.
     */
    public void approve(int totalStep) {
        if (totalStep < 1) {
            throw new IllegalArgumentException("전체 단계 수는 1 이상이어야 합니다: " + totalStep);
        }
        if (!ApprovalStatus.PENDING.equals(this.status)) {
            throw new IllegalStateException("결재 진행 중인 문서만 승인할 수 있습니다: " + this.status);
        }
        if (this.currentStep >= totalStep) {
            this.status = ApprovalStatus.APPROVED;
            this.completedAt = LocalDateTime.now();
            return;
        }
        this.currentStep = this.currentStep + 1;
    }

    /** 반려한다. 남은 단계는 Service 가 SKIPPED 로 정리한다. */
    public void reject() {
        if (!ApprovalStatus.PENDING.equals(this.status)) {
            throw new IllegalStateException("결재 진행 중인 문서만 반려할 수 있습니다: " + this.status);
        }
        this.status = ApprovalStatus.REJECTED;
        this.completedAt = LocalDateTime.now();
    }

    /**
     * 기안자가 회수한다.
     *
     * 임시저장이거나, 상신했더라도 아직 아무도 승인하지 않았을 때만 가능하다.
     * currentStep 이 1이라는 것은 1단계가 아직 대기 중이라는 뜻이므로 승인 이력이 없다.
     */
    public void cancel(Long actorId) {
        if (!Objects.equals(this.drafterId, actorId)) {
            throw new IllegalStateException("기안자만 회수할 수 있습니다");
        }
        boolean cancelableDraft = ApprovalStatus.DRAFT.equals(this.status);
        boolean cancelablePending = ApprovalStatus.PENDING.equals(this.status) && this.currentStep <= 1;
        if (!cancelableDraft && !cancelablePending) {
            throw new IllegalStateException(
                    "이미 결재가 진행된 문서는 회수할 수 없습니다: " + this.status + " step=" + this.currentStep);
        }
        this.status = ApprovalStatus.CANCELED;
        this.completedAt = LocalDateTime.now();
    }

    // ── 판정 ────────────────────────────────────────────────────

    /** 더 이상 상태가 바뀌지 않는가 */
    public boolean isCompleted() {
        return ApprovalStatus.isTerminal(this.status);
    }

    /** 기안자가 내용을 고칠 수 있는가 */
    public boolean isEditable() {
        return ApprovalStatus.DRAFT.equals(this.status);
    }

    /** 주어진 단계가 지금 처리를 기다리는 단계인가 */
    public boolean isAwaitingStep(int stepNo) {
        return ApprovalStatus.PENDING.equals(this.status) && this.currentStep == stepNo;
    }

    /** 화면 표시용 문서 유형 한글명 */
    public String getDocTypeLabel() {
        return DocType.labelOf(this.docType);
    }

    /** 화면 표시용 상태 한글명 */
    public String getStatusLabel() {
        return ApprovalStatus.labelOf(this.status);
    }

    /** 금액 칸을 쓰는 문서인가 — 기안 화면이 금액 입력을 보일지 정하는 데 쓴다 */
    public boolean isUsesAmount() {
        return DocType.usesAmount(this.docType);
    }

    /**
     * 본문 영역에 붙일 제목. 연차 신청서는 본문이 곧 신청 사유이므로
     * (ApprovalService.contentOf 참조) 그렇게 부르는 편이 읽는 사람에게 정확하다.
     */
    public String getContentLabel() {
        return DocType.usesLeaveFields(this.docType) ? "신청 사유" : "본문";
    }

    // ── getter / setter ─────────────────────────────────────────

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public String getDocNo() {
        return docNo;
    }

    public void setDocNo(String docNo) {
        this.docNo = docNo;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getDrafterId() {
        return drafterId;
    }

    public void setDrafterId(Long drafterId) {
        this.drafterId = drafterId;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(int currentStep) {
        this.currentStep = currentStep;
    }

    public LocalDateTime getDraftedAt() {
        return draftedAt;
    }

    public void setDraftedAt(LocalDateTime draftedAt) {
        this.draftedAt = draftedAt;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getDrafterName() {
        return drafterName;
    }

    public void setDrafterName(String drafterName) {
        this.drafterName = drafterName;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getDrafterPositionName() {
        return drafterPositionName;
    }

    public void setDrafterPositionName(String drafterPositionName) {
        this.drafterPositionName = drafterPositionName;
    }
}
