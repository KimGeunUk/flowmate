package com.flowmate.approval.domain;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 내 결재함 탭 4종.
 *
 * ★ 네 탭은 성격이 다르다. 그런데 화면에서는 넷이 똑같이 생겨서, "지금 내가
 *   뭘 해야 하는지"가 지나간 기록 사이에 묻혀 있었다.
 *
 *     대기   지금 내 차례다        → 할 일 (남이 나를 기다린다)
 *     반려   고쳐서 다시 올려야 한다 → 할 일
 *     기안   내가 올린 것들         → 기록
 *     완료   내가 처리를 끝낸 것들   → 기록
 *
 *   isTodo 가 그 구분이다. 화면은 이 값으로 할 일 탭을 눈에 띄게 만든다.
 *
 * ★ 탭 코드와 한글 이름의 짝을 여기서 만든다(DocType.options() 와 같은 자리).
 *   예전에는 box.jsp 안에서 c:choose 로 'drafted'→'기안'을 골랐는데, 그러면
 *   탭을 하나 추가할 때 화면을 고쳐야 하고 그것을 강제하는 장치가 없다.
 */
public final class BoxTab {

    /** 내가 기안한 문서 전부 */
    public static final String DRAFTED = "drafted";
    /** 지금 내 차례인 문서 */
    public static final String PENDING = "pending";
    /** 내가 승인·반려로 처리를 끝낸 문서 */
    public static final String DONE = "done";
    /** 내가 기안했고 반려된 문서 */
    public static final String REJECTED = "rejected";

    /** 화면의 탭 순서 */
    public static final List<String> ALL = List.of(DRAFTED, PENDING, DONE, REJECTED);

    private BoxTab() {
    }

    /** 화면에 보여줄 한글 이름 */
    public static String labelOf(String tab) {
        if (PENDING.equals(tab)) {
            return "대기";
        }
        if (DONE.equals(tab)) {
            return "완료";
        }
        if (REJECTED.equals(tab)) {
            return "반려";
        }
        return "기안";
    }

    /** 지금 내가 무언가 해야 하는 탭인가. 기안·완료는 지나간 기록이라 아니다 */
    public static boolean isTodo(String tab) {
        return PENDING.equals(tab) || REJECTED.equals(tab);
    }

    /** 탭 목록에 건수를 실어 화면으로 넘긴다 */
    public static List<Option> options(ApprovalBoxCounts counts) {
        return ALL.stream().map(code -> new Option(code, counts)).collect(Collectors.toList());
    }

    /** 탭 하나. JSP 가 ${tab.code} / ${tab.label} / ${tab.count} / ${tab.todo} 로 읽는다 */
    public static final class Option {

        private final String code;
        private final String label;
        private final long count;
        private final boolean todo;

        public Option(String code, ApprovalBoxCounts counts) {
            this.code = code;
            this.label = labelOf(code);
            this.count = counts == null ? 0L : counts.countOf(code);
            // BoxTab.isTodo 를 명시한다 — 이 클래스의 isTodo() 가 이름을 가린다
            this.todo = BoxTab.isTodo(code);
        }

        public String getCode() {
            return code;
        }

        public String getLabel() {
            return label;
        }

        public long getCount() {
            return count;
        }

        public boolean isTodo() {
            return todo;
        }
    }
}
