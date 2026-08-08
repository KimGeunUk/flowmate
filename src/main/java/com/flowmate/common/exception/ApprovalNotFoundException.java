package com.flowmate.common.exception;

public class ApprovalNotFoundException extends FlowMateException {

    private static final long serialVersionUID = 1L;

    public ApprovalNotFoundException(Long approvalId) {
        super("결재 문서를 찾을 수 없습니다: " + approvalId);
    }
}
