package com.flowmate.attendance.domain;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 연차 신청 유형.
 *
 * enum 이 아니라 String 상수인 이유는 AttendanceStatus 와 같다: MyBatis 가
 * VARCHAR 컬럼을 그대로 읽고, JSP EL 이 ${form.leaveType == 'HALF_AM'} 로
 * 비교할 수 있어야 한다.
 */
public final class LeaveType {

    /** 연차 (하루 전체) */
    public static final String ANNUAL = "ANNUAL";
    /** 오전 반차 */
    public static final String HALF_AM = "HALF_AM";
    /** 오후 반차 */
    public static final String HALF_PM = "HALF_PM";
    /** 병가 */
    public static final String SICK = "SICK";

    /** 화면 선택 상자 순서 */
    public static final List<String> ALL = List.of(ANNUAL, HALF_AM, HALF_PM, SICK);

    private LeaveType() {
    }

    /**
     * 반차인가. 반차는 하루짜리만 허용하고 0.5일로 고정한다 —
     * 반차가 며칠에 걸치는 것은 기능이 아니라 입력 오류다.
     */
    public static boolean isHalfDay(String leaveType) {
        return HALF_AM.equals(leaveType) || HALF_PM.equals(leaveType);
    }

    /** 화면에 보여줄 한글 이름. DocType.labelOf 와 같은 자리 */
    public static String labelOf(String leaveType) {
        if (ANNUAL.equals(leaveType)) {
            return "연차";
        }
        if (HALF_AM.equals(leaveType)) {
            return "오전 반차";
        }
        if (HALF_PM.equals(leaveType)) {
            return "오후 반차";
        }
        if (SICK.equals(leaveType)) {
            return "병가";
        }
        return leaveType;
    }

    /**
     * 선택 상자에 쓸 (코드, 한글 이름) 쌍. 순서는 {@link #ALL} 을 따른다 —
     * DocType.options() 와 같은 자리다.
     *
     * ★ ALL 을 그대로 화면에 넘기면 선택 상자에 ANNUAL·HALF_AM 이 그대로 뜬다.
     *   문서 유형은 한글로 고쳤는데 연차 유형만 남아 있었다 — labelOf 는 진작
     *   있었고 화면만 안 쓰고 있었다. 값을 만들어 넘기면 그런 누락이 생기지 않는다.
     */
    public static List<Option> options() {
        return ALL.stream().map(Option::new).collect(Collectors.toList());
    }

    /** 선택 상자 한 줄. JSP 가 ${t.code} / ${t.label} 로 읽는다 */
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
