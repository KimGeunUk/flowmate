package com.flowmate.approval.domain;

/**
 * 결재 문서 상태.
 *
 * enum 이 아니라 String 상수인 이유:
 * MyBatis 가 VARCHAR 컬럼을 그대로 읽고, JSP EL 이 ${doc.status == 'PENDING'} 으로
 * 비교할 수 있어야 한다. enum 으로 두면 양쪽에 타입 핸들러와 변환이 필요해진다.
 */
public final class ApprovalStatus {

    /** 임시저장. 기안자만 보이고 수정·삭제할 수 있다 */
    public static final String DRAFT = "DRAFT";
    /** 상신되어 결재 진행 중 */
    public static final String PENDING = "PENDING";
    /** 최종 승인 완료 */
    public static final String APPROVED = "APPROVED";
    /** 반려됨 */
    public static final String REJECTED = "REJECTED";
    /** 기안자가 회수함 */
    public static final String CANCELED = "CANCELED";

    private ApprovalStatus() {
    }

    /** 더 이상 상태가 바뀌지 않는 종결 상태인가 */
    public static boolean isTerminal(String status) {
        return APPROVED.equals(status) || REJECTED.equals(status) || CANCELED.equals(status);
    }
}
