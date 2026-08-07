package com.flowmate.approval.domain;

/** 결재 이력에 남는 행위 */
public final class HistoryAction {

    public static final String DRAFT = "DRAFT";
    public static final String SUBMIT = "SUBMIT";
    public static final String APPROVE = "APPROVE";
    public static final String REJECT = "REJECT";
    public static final String CANCEL = "CANCEL";

    private HistoryAction() {
    }
}
