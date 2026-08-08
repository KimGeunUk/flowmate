package com.flowmate.approval.service;

import java.time.LocalDateTime;

import com.flowmate.approval.domain.ApprovalHistory;

/** 이력 객체 조립. Service 가 매번 5줄을 쓰지 않게 한다 */
final class HistoryFactory {

    private HistoryFactory() {
    }

    static ApprovalHistory of(Long approvalId, Long actorId, String action, String comment) {
        ApprovalHistory h = new ApprovalHistory();
        h.setApprovalId(approvalId);
        h.setActorId(actorId);
        h.setAction(action);
        h.setComment(comment);
        h.setCreatedAt(LocalDateTime.now());
        return h;
    }
}
