package com.flowmate.approval.domain;

import java.util.List;
import java.util.stream.Collectors;

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

    /**
     * 유효한 유형인가. 화면에서 넘어온 값을 신뢰하지 않기 위한 검사다.
     *
     * null 을 먼저 걸러내는 이유: ALL 은 List.of(...) 로 만든 불변 리스트인데,
     * 이런 리스트의 contains(null) 은 false 가 아니라 NullPointerException 을 던진다
     * (null 원소를 아예 허용하지 않는 리스트라 "포함 여부를 못 묻는다"는 뜻으로 실패한다).
     * 화면에서 유형을 선택하지 않고 보내면 category 가 정확히 이 null 이 된다.
     */
    public static boolean isValid(String category) {
        return category != null && ALL.contains(category);
    }

    /**
     * 선택 상자에 쓸 (코드, 한글 이름) 쌍. DocType.options() 와 같은 자리다.
     *
     * ★ 반려 모달이 ALL(코드 목록)을 그대로 받아 INSUFFICIENT_CONTENT 를
     *   화면에 내보내고 있었다 — labelOf 는 진작 있었는데 화면만 안 쓰고
     *   있었다(연차 유형과 똑같은 누락이다). 하필 결재자가 반드시 골라야
     *   하는 필수 항목이라, 영문 코드를 보고 고르게 되어 있었다.
     */
    public static List<Option> options() {
        return ALL.stream().map(Option::new).collect(Collectors.toList());
    }

    /** 선택 상자 한 줄. JSP 가 ${r.code} / ${r.label} 로 읽는다 */
    public static final class Option {

        private final String code;
        private final String label;

        public Option(String code) {
            this.code = code;
            this.label = labelOf(code);
        }

        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }
    }
}
