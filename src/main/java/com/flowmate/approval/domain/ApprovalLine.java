package com.flowmate.approval.domain;

import java.io.Serializable;
import java.time.LocalDateTime;
import com.flowmate.common.web.DateLabels;

/**
 * 결재선 한 단계.
 *
 * approverName / approverPositionName / approverDeptName 은 조인 결과를 담는
 * 조회 표시용 파생 필드다. approval_line 테이블의 컬럼이 아니다.
 */
public class ApprovalLine implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long lineId;
    private Long approvalId;
    private int stepNo;
    private Long approverId;
    private String lineType;
    private String status;
    private String comment;
    private LocalDateTime processedAt;

    // 조회 표시용
    private String approverName;
    private String approverPositionName;
    private String approverDeptName;

    /** 이 단계가 지금 처리를 기다리는가 */
    public boolean isCurrent() {
        return LineStatus.CURRENT.equals(this.status);
    }

    /** 처리가 끝난 단계인가 */
    public boolean isProcessed() {
        return LineStatus.APPROVED.equals(this.status) || LineStatus.REJECTED.equals(this.status);
    }

    /** 화면 표시용 처리 시각 */
    public String getProcessedAtLabel() {
        return DateLabels.dateTime(this.processedAt);
    }

    /** 화면 표시용 상태 한글명 */
    public String getStatusLabel() {
        return LineStatus.labelOf(this.status);
    }

    public Long getLineId() {
        return lineId;
    }

    public void setLineId(Long lineId) {
        this.lineId = lineId;
    }

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public int getStepNo() {
        return stepNo;
    }

    public void setStepNo(int stepNo) {
        this.stepNo = stepNo;
    }

    public Long getApproverId() {
        return approverId;
    }

    public void setApproverId(Long approverId) {
        this.approverId = approverId;
    }

    public String getLineType() {
        return lineType;
    }

    public void setLineType(String lineType) {
        this.lineType = lineType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(LocalDateTime processedAt) {
        this.processedAt = processedAt;
    }

    public String getApproverName() {
        return approverName;
    }

    public void setApproverName(String approverName) {
        this.approverName = approverName;
    }

    public String getApproverPositionName() {
        return approverPositionName;
    }

    public void setApproverPositionName(String approverPositionName) {
        this.approverPositionName = approverPositionName;
    }

    public String getApproverDeptName() {
        return approverDeptName;
    }

    public void setApproverDeptName(String approverDeptName) {
        this.approverDeptName = approverDeptName;
    }
}
