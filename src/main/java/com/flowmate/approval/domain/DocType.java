package com.flowmate.approval.domain;

import java.util.List;

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
}
