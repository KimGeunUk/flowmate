package com.flowmate.approval.domain;

/** 결재선 한 단계의 상태 */
public final class LineStatus {

    /** 아직 순서가 오지 않음 */
    public static final String WAITING = "WAITING";
    /** 지금 이 사람이 처리할 차례 */
    public static final String CURRENT = "CURRENT";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    /** 앞 단계에서 반려되어 차례가 오지 않음 */
    public static final String SKIPPED = "SKIPPED";

    private LineStatus() {
    }
}
