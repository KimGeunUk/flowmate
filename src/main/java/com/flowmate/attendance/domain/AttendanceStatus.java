package com.flowmate.attendance.domain;

/**
 * 근태 상태 (설계서 §5.4).
 *
 * enum 이 아니라 String 상수인 이유는 approval.domain.ApprovalStatus 와 같다:
 * MyBatis 가 VARCHAR 컬럼을 그대로 읽고, JSP EL 이 ${att.status == 'LATE'} 로
 * 비교할 수 있어야 한다.
 */
public final class AttendanceStatus {

    /** 정상 출퇴근 */
    public static final String NORMAL = "NORMAL";
    /** 지각. 09:01 이후 출근 (DefaultWorkTimePolicy 기준) */
    public static final String LATE = "LATE";
    /** 조퇴. 정시 이전 퇴근, 또는 소정 근로시간 미달 (FlexWorkTimePolicy 기준) */
    public static final String EARLY_LEAVE = "EARLY_LEAVE";
    /** 결근. WorkTimePolicy 는 이 상태를 만들지 않는다 — 출근 기록이 있는데
     *  퇴근이 없다고 결근으로 적으면 거짓이 된다 (계획서 4 Task 2 함정 2) */
    public static final String ABSENT = "ABSENT";
    /** 연차 (하루 전체) */
    public static final String LEAVE = "LEAVE";
    /** 반차 */
    public static final String HALF_LEAVE = "HALF_LEAVE";
    /** 공휴일 */
    public static final String HOLIDAY = "HOLIDAY";

    private AttendanceStatus() {
    }
}
