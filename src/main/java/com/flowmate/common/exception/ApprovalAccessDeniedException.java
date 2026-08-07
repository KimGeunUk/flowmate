package com.flowmate.common.exception;

/**
 * 문서 접근·처리 권한 없음.
 *
 * URL 인가로는 막을 수 없는 종류의 권한이다 — "내 결재선에 있는 문서만" 같은 판정은
 * 문서마다 다르므로 Service 에서 검사한다 (설계서 §6.1).
 */
public class ApprovalAccessDeniedException extends FlowMateException {

    private static final long serialVersionUID = 1L;

    public ApprovalAccessDeniedException(String message) {
        super(message);
    }
}
