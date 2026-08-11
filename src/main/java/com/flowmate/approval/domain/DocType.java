package com.flowmate.approval.domain;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 문서 유형 5종 (설계서 §12 확정).
 *
 * prefixOf 가 문서번호 접두사를 준다. 문서번호는 {접두사}-{연도}-{4자리 일련번호} 형식이다.
 */
public final class DocType {

    public static final String EXPENSE = "EXPENSE";
    public static final String PURCHASE = "PURCHASE";
    public static final String LEAVE = "LEAVE";
    public static final String CONTRACT = "CONTRACT";
    public static final String GENERAL = "GENERAL";

    /** 화면 선택 상자 순서 */
    public static final List<String> ALL = List.of(EXPENSE, PURCHASE, LEAVE, CONTRACT, GENERAL);

    private DocType() {
    }

    /** 문서번호 접두사. 알 수 없는 유형은 GEN 으로 떨어뜨린다 */
    public static String prefixOf(String docType) {
        if (EXPENSE.equals(docType)) {
            return "EXP";
        }
        if (PURCHASE.equals(docType)) {
            return "PUR";
        }
        if (LEAVE.equals(docType)) {
            return "LEV";
        }
        if (CONTRACT.equals(docType)) {
            return "CON";
        }
        return "GEN";
    }

    /** 화면에 보여줄 한글 이름 */
    public static String labelOf(String docType) {
        if (EXPENSE.equals(docType)) {
            return "지출결의";
        }
        if (PURCHASE.equals(docType)) {
            return "구매요청";
        }
        if (LEAVE.equals(docType)) {
            return "연차신청";
        }
        if (CONTRACT.equals(docType)) {
            return "계약서";
        }
        return "일반문서";
    }

    /**
     * 금액 칸을 쓰는 유형인가. 지출결의·구매요청만 금액이 의미를 갖는다.
     *
     * ★ 이 판정이 화면이 아니라 여기 있는 이유:
     *   기안 화면은 유형을 고르는 순간 금액 칸을 감춰야 하므로 판정 자체는
     *   브라우저에서 일어난다. 그렇다고 스크립트에 코드를 적어 두면
     *   ("EXPENSE 나 PURCHASE 면 보인다") 유형을 추가할 때 화면 스크립트도
     *   같이 고쳐야 하는데, 그것을 강제하는 장치가 없다 — labelOf 를
     *   빠뜨렸던 것과 똑같은 함정이다(DocTypeTest 참조).
     *
     *   그래서 판정은 여기서 하고, 선택 상자의 각 항목이 자기 성격을 data
     *   속성으로 들고 간다. 스크립트는 "고른 항목의 속성을 읽는다"만 한다 —
     *   유형이 몇 개인지, 이름이 무엇인지 알 필요가 없다.
     */
    public static boolean usesAmount(String docType) {
        return EXPENSE.equals(docType) || PURCHASE.equals(docType);
    }

    /** 연차 입력 영역(기간·유형·사유)을 쓰는 유형인가 */
    public static boolean usesLeaveFields(String docType) {
        return LEAVE.equals(docType);
    }

    /**
     * 선택 상자에 쓸 (코드, 한글 이름) 쌍. 순서는 {@link #ALL} 을 따른다.
     *
     * ★ ALL 을 그대로 화면에 넘기지 않는 이유:
     *   그러면 JSP 가 코드(EXPENSE)를 그대로 출력하게 된다. 실제로 기안 화면과
     *   결재함 필터 두 곳이 그랬다 — 목록·상세는 ApprovalDoc.getDocTypeLabel() 을
     *   쓰고 있었는데 선택 상자만 빠져 있었다.
     *
     *   JSP 안에서 EL 로 코드→이름을 고르게 하지 않는다. 이 프로젝트는
     *   "판정은 서버가 하고 JSP 는 받은 것을 그리기만 한다"를 규약으로 지켜 왔고,
     *   그 규약이 있어야 Phase 6 의 CSS 마감처럼 화면을 안 열고도 작업할 수 있다.
     *   여기서 값을 만들어 넘기면 labelOf 한 곳만 고쳐도 모든 화면이 따라온다.
     */
    public static List<Option> options() {
        return ALL.stream().map(Option::new).collect(Collectors.toList());
    }

    /**
     * 선택 상자 한 줄. JSP 가 ${t.code} / ${t.label} 로 읽고,
     * 유형에 따라 달라지는 입력칸은 ${t.usesAmount} / ${t.usesLeaveFields} 를
     * data 속성으로 실어 보낸다.
     */
    public static final class Option {

        private final String code;
        private final String label;
        private final boolean usesAmount;
        private final boolean usesLeaveFields;

        public Option(String code) {
            this.code = code;
            this.label = labelOf(code);
            this.usesAmount = DocType.usesAmount(code);
            this.usesLeaveFields = DocType.usesLeaveFields(code);
        }

        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        public boolean isUsesAmount() {
            return usesAmount;
        }

        public boolean isUsesLeaveFields() {
            return usesLeaveFields;
        }
    }
}
