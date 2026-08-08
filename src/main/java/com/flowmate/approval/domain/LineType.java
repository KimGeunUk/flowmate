package com.flowmate.approval.domain;

/**
 * 결재선 종류.
 *
 * Phase 2 는 APPROVAL 만 쓴다. AGREEMENT/REFERENCE 는 설계서 §9 가 정한
 * 잘라내기 순서에서 2순위이므로 상수만 두고 화면·로직은 만들지 않는다.
 */
public final class LineType {

    /** 결재 — 승인/반려 권한이 있다 */
    public static final String APPROVAL = "APPROVAL";
    /** 합의 — 의견만 남긴다 (Phase 2 범위 밖) */
    public static final String AGREEMENT = "AGREEMENT";
    /** 참조 — 열람만 한다 (Phase 2 범위 밖) */
    public static final String REFERENCE = "REFERENCE";

    private LineType() {
    }
}
