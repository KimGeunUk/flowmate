package com.flowmate.approval.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 반려 이력. ★ Phase 5 AI 사전점검이 읽는 표다.
 *
 * docType / deptId 가 approval_doc 과 중복이지만 의도한 비정규화다 (설계서 §5.2).
 * 사전점검은 상신할 때마다 도는 조회이므로 매번 조인하지 않는다.
 */
public class RejectHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long approvalId;
    private String docType;
    private Long deptId;
    private Long rejectorId;
    private String reasonCategory;
    private String reasonText;
    private LocalDateTime rejectedAt;

    /** 화면 표시용 반려 유형 한글명 */
    public String getReasonLabel() {
        return RejectReason.labelOf(this.reasonCategory);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getRejectorId() {
        return rejectorId;
    }

    public void setRejectorId(Long rejectorId) {
        this.rejectorId = rejectorId;
    }

    public String getReasonCategory() {
        return reasonCategory;
    }

    public void setReasonCategory(String reasonCategory) {
        this.reasonCategory = reasonCategory;
    }

    public String getReasonText() {
        return reasonText;
    }

    public void setReasonText(String reasonText) {
        this.reasonText = reasonText;
    }

    public LocalDateTime getRejectedAt() {
        return rejectedAt;
    }

    public void setRejectedAt(LocalDateTime rejectedAt) {
        this.rejectedAt = rejectedAt;
    }
}
