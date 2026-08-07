package com.flowmate.approval.domain;

import java.util.List;

/**
 * 반려 유형 6종 (설계서 §5.2 확정).
 *
 * ★ 이 값이 Phase 5 AI 사전점검의 학습 원천이다.
 *   반려 화면에서 선택을 필수로 만드는 이유가 여기에 있다 —
 *   자유 텍스트만 받으면 유형별 빈도를 집계할 수 없고, 집계가 없으면
 *   "과거 반려 3건에 근거함" 같은 숫자를 제시할 수 없다.
 */
public final class RejectReason {

    public static final String INSUFFICIENT_CONTENT = "INSUFFICIENT_CONTENT";
    public static final String EXCESSIVE_AMOUNT = "EXCESSIVE_AMOUNT";
    public static final String MISSING_EVIDENCE = "MISSING_EVIDENCE";
    public static final String PROCEDURE_ERROR = "PROCEDURE_ERROR";
    public static final String BUDGET_EXCEEDED = "BUDGET_EXCEEDED";
    public static final String OTHER = "OTHER";

    /** 화면의 선택 상자 순서 */
    public static final List<String> ALL = List.of(
            INSUFFICIENT_CONTENT, EXCESSIVE_AMOUNT, MISSING_EVIDENCE,
            PROCEDURE_ERROR, BUDGET_EXCEEDED, OTHER);

    private RejectReason() {
    }

    /** 화면에 보여줄 한글 이름 */
    public static String labelOf(String category) {
        if (INSUFFICIENT_CONTENT.equals(category)) {
            return "문서 내용 불충분";
        }
        if (EXCESSIVE_AMOUNT.equals(category)) {
            return "금액 과다 · 근거 부족";
        }
        if (MISSING_EVIDENCE.equals(category)) {
            return "증빙 누락";
        }
        if (PROCEDURE_ERROR.equals(category)) {
            return "결재 절차 오류";
        }
        if (BUDGET_EXCEEDED.equals(category)) {
            return "예산 초과";
        }
        return "기타";
    }

    /** 유효한 유형인가. 화면에서 넘어온 값을 신뢰하지 않기 위한 검사다 */
    public static boolean isValid(String category) {
        return ALL.contains(category);
    }
}
