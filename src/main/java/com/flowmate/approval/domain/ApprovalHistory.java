package com.flowmate.approval.domain;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 결재 이력 한 줄. 문서 상세 화면의 타임라인이 이 목록을 그린다.
 *
 * actorName / actorPositionName 은 조인 결과를 담는 조회 표시용 파생 필드다.
 */
public class ApprovalHistory implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long historyId;
    private Long approvalId;
    private Long actorId;
    private String action;
    private String comment;
    private LocalDateTime createdAt;

    // 조회 표시용
    private String actorName;
    private String actorPositionName;

    /**
     * 화면에 보여줄 한글 행위명.
     *
     * JSP 가 ${h.actionLabel} 로 읽는다. 정적 메서드 호출은 EL 에서 번거로우므로
     * 도메인 객체에 파생 getter 로 둔다.
     */
    public String getActionLabel() {
        if (HistoryAction.DRAFT.equals(this.action)) {
            return "기안";
        }
        if (HistoryAction.SUBMIT.equals(this.action)) {
            return "상신";
        }
        if (HistoryAction.APPROVE.equals(this.action)) {
            return "승인";
        }
        if (HistoryAction.REJECT.equals(this.action)) {
            return "반려";
        }
        if (HistoryAction.CANCEL.equals(this.action)) {
            return "회수";
        }
        return this.action;
    }

    public Long getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Long historyId) {
        this.historyId = historyId;
    }

    public Long getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(Long approvalId) {
        this.approvalId = approvalId;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getActorName() {
        return actorName;
    }

    public void setActorName(String actorName) {
        this.actorName = actorName;
    }

    public String getActorPositionName() {
        return actorPositionName;
    }

    public void setActorPositionName(String actorPositionName) {
        this.actorPositionName = actorPositionName;
    }
}
