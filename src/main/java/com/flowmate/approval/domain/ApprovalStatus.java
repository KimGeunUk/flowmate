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
     * 존재하는 모든 상태. DocType.ALL 과 같은 자리다.
     *
     * 화면에서 이 목록을 통째로 쓰는 곳은 아직 없지만, 이것이 있어야
     * "상태를 추가하고 한글 이름을 빠뜨렸다"를 테스트가 잡을 수 있다 —
     * 목록이 없으면 테스트가 상수를 손으로 다시 적게 되고, 그러면 빠뜨린
     * 상태는 테스트에서도 똑같이 빠진다.
     */
    public static final List<String> ALL = List.of(DRAFT, PENDING, APPROVED, REJECTED, CANCELED);

    private ApprovalStatus() {
    }

    /** 더 이상 상태가 바뀌지 않는 종결 상태인가 */
    public static boolean isTerminal(String status) {
        return APPROVED.equals(status) || REJECTED.equals(status) || CANCELED.equals(status);
    }

    /**
     * 화면에 보여줄 한글 이름. DocType.labelOf / AttendanceStatus 와 같은 자리다.
     *
     * ★ 근태 화면은 진작 statusLabel 을 쓰고 있었는데 결재 화면 세 곳(결재함
     *   목록·문서 상세·기안 작성)만 DRAFT·PENDING 을 영문 그대로 내보내고
     *   있었다. 색은 status--draft 로 이미 구분되고 있었으므로 글자만 남은
     *   문제였다 — 눈에 덜 띄는 종류의 누락이다.
     */
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
