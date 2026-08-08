package com.flowmate.common.exception;

/** FlowMate 업무 예외의 뿌리 */
public class FlowMateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FlowMateException(String message) {
        super(message);
    }
}
