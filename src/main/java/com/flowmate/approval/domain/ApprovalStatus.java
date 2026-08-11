package com.flowmate.approval.domain;

import java.util.List;

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

    /**
     * 존재하는 모든 상태. 이 목록이 있어야 "상태를 추가하고 한글 이름을
     * 빠뜨렸다"를 테스트가 잡을 수 있다 - 없으면 테스트가 상수를 손으로 다시
     * 적게 되고, 빠뜨린 상태는 테스트에서도 똑같이 빠진다.
     */
    public static final List<String> ALL = List.of(DRAFT, PENDING, APPROVED, REJECTED, CANCELED);

    private ApprovalStatus() {
    }

    /** 더 이상 상태가 바뀌지 않는 종결 상태인가 */
    public static boolean isTerminal(String status) {
        return APPROVED.equals(status) || REJECTED.equals(status) || CANCELED.equals(status);
    }

    /** 화면에 보여줄 한글 이름. DocType.labelOf / AttendanceStatus.labelOf 와 같은 자리 */
    public static String labelOf(String status) {
        if (DRAFT.equals(status)) {
            return "임시저장";
        }
        if (PENDING.equals(status)) {
            return "결재중";
        }
        if (APPROVED.equals(status)) {
            return "승인";
        }
        if (REJECTED.equals(status)) {
            return "반려";
        }
        if (CANCELED.equals(status)) {
            return "회수";
        }
        return status;
    }
}
